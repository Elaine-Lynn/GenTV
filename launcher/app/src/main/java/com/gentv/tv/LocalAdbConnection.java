package com.gentv.tv;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import javax.crypto.Cipher;

/** Small legacy ADB transport, confined to this device's loopback interface. No TLS downgrade. */
final class LocalAdbConnection implements Closeable {
    static final int CNXN = 0x4e584e43, AUTH = 0x48545541, OPEN = 0x4e45504f;
    static final int OKAY = 0x59414b4f, WRTE = 0x45545257, CLSE = 0x45534c43;
    private static final int MAX_PAYLOAD = 4096;
    private final Socket socket = new Socket();
    private InputStream input;
    private OutputStream output;
    private int nextId = 1;

    void connect(int port, KeyPair key) throws IOException, GeneralSecurityException {
        if (port < 1 || port > 65535) throw new ProtocolFailure("Invalid port");
        socket.connect(new InetSocketAddress("127.0.0.1", port), 3000);
        socket.setSoTimeout(90_000); // Time for the owner to accept Android's RSA dialog.
        input = socket.getInputStream();
        output = socket.getOutputStream();
        send(CNXN, 0x01000000, MAX_PAYLOAD, bytes("host::\0"));
        boolean signed = false, published = false;
        for (int i = 0; i < 6; i++) {
            Packet packet = read();
            if (packet.command == CNXN) { socket.setSoTimeout(10_000); return; }
            if (packet.command != AUTH || packet.arg0 != 1 || packet.data.length != 20) {
                throw new ProtocolFailure("Unsupported ADB authentication; use system settings or phone pairing");
            }
            if (!signed) {
                // ADB tokens are already SHA-1 digests. PKCS#1 encodes DigestInfo + token, without hashing again.
                byte[] prefix = {0x30,0x21,0x30,0x09,0x06,0x05,0x2b,0x0e,0x03,0x02,0x1a,0x05,0x00,0x04,0x14};
                byte[] digest = new byte[prefix.length + 20];
                System.arraycopy(prefix, 0, digest, 0, prefix.length);
                System.arraycopy(packet.data, 0, digest, prefix.length, 20);
                Cipher rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                rsa.init(Cipher.ENCRYPT_MODE, key.getPrivate());
                send(AUTH, 2, 0, rsa.doFinal(digest));
                signed = true;
            } else if (!published) {
                send(AUTH, 3, 0, publicKey((RSAPublicKey) key.getPublic()));
                published = true;
            } else throw new ProtocolFailure("ADB authorisation denied");
        }
        throw new ProtocolFailure("ADB handshake did not complete");
    }

    String shell(String command) throws IOException {
        int local = nextId++;
        send(OPEN, local, 0, bytes("shell:" + command + "\0"));
        java.io.ByteArrayOutputStream collected = new java.io.ByteArrayOutputStream();
        int remote = 0;
        for (int packets = 0; packets < 1024; packets++) {
            Packet packet = read();
            // Some adbd versions reply to our close acknowledgement after the next OPEN.
            // That stream is already closed; acknowledging it again would prolong the exchange.
            if (packet.command == CLSE && packet.arg1 > 0 && packet.arg1 < local) continue;
            if (packet.arg1 != local) throw new ProtocolFailure("command=" + packet.command + ", stream=" + packet.arg1 + ", expected=" + local);
            if (packet.command == OKAY && remote == 0) remote = packet.arg0;
            else if (packet.command == WRTE && remote != 0 && packet.arg0 == remote) {
                if (collected.size() + packet.data.length > 65_536) throw new ProtocolFailure("ADB output too large");
                collected.write(packet.data);
                send(OKAY, local, remote, new byte[0]);
            } else if (packet.command == CLSE && (remote == 0 || remote == packet.arg0)) {
                send(CLSE, local, packet.arg0, new byte[0]);
                return collected.toString(StandardCharsets.UTF_8.name()).trim();
            } else throw new ProtocolFailure("Unexpected ADB packet");
        }
        throw new ProtocolFailure("ADB stream did not close");
    }

    static byte[] publicKey(RSAPublicKey key) throws IOException {
        BigInteger n = key.getModulus();
        if (n.bitLength() != 2048) throw new ProtocolFailure("ADB requires RSA-2048");
        ByteBuffer encoded = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN);
        encoded.putInt(64);
        encoded.putInt(n.modInverse(BigInteger.ONE.shiftLeft(32)).negate().intValue());
        littleInteger(encoded, n);
        littleInteger(encoded, BigInteger.ONE.shiftLeft(4096).mod(n));
        encoded.putInt(key.getPublicExponent().intValue());
        return bytes(Base64.getEncoder().encodeToString(encoded.array()) + " gentv@localhost\0");
    }

    private static void littleInteger(ByteBuffer out, BigInteger value) {
        byte[] big = value.toByteArray();
        for (int i = 0; i < 256; i++) out.put(i < big.length ? big[big.length - 1 - i] : (byte) 0);
    }

    private void send(int command, int arg0, int arg1, byte[] data) throws IOException {
        if (data.length > MAX_PAYLOAD) throw new ProtocolFailure("ADB request too large");
        ByteBuffer header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(command).putInt(arg0).putInt(arg1).putInt(data.length).putInt(checksum(data)).putInt(command ^ -1);
        output.write(header.array()); output.write(data); output.flush();
    }

    private Packet read() throws IOException {
        ByteBuffer header = ByteBuffer.wrap(readFully(24)).order(ByteOrder.LITTLE_ENDIAN);
        int command = header.getInt(), arg0 = header.getInt(), arg1 = header.getInt();
        int length = header.getInt(), sum = header.getInt(), magic = header.getInt();
        if (magic != (command ^ -1) || length < 0 || length > MAX_PAYLOAD) throw new ProtocolFailure("Invalid ADB frame");
        byte[] data = readFully(length);
        if (checksum(data) != sum) throw new ProtocolFailure("Invalid ADB checksum");
        return new Packet(command, arg0, arg1, data);
    }

    private byte[] readFully(int length) throws IOException {
        byte[] result = new byte[length];
        int offset = 0;
        while (offset < length) {
            int count = input.read(result, offset, length - offset);
            if (count < 0) throw new EOFException("ADB disconnected");
            offset += count;
        }
        return result;
    }

    private static int checksum(byte[] data) { int sum = 0; for (byte b : data) sum += b & 255; return sum; }
    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    @Override public void close() throws IOException { socket.close(); }
    static final class ProtocolFailure extends IOException {
        ProtocolFailure(String diagnostic) { super(diagnostic); }
    }
    private static final class Packet {
        final int command, arg0, arg1;
        final byte[] data;
        Packet(int command, int arg0, int arg1, byte[] data) {
            this.command = command; this.arg0 = arg0; this.arg1 = arg1; this.data = data;
        }
    }
}
