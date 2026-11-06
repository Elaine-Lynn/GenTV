package com.gentv.tv;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.Cipher;

public class LocalAdbConnectionTest {
    private static KeyPair keys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        return generator.generateKeyPair();
    }
    private interface Session { void run(Socket peer) throws Exception; }
    private static class Daemon implements AutoCloseable {
        final ServerSocket listener = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        final Future<?> future;
        Daemon(Session session) throws IOException {
            future = executor.submit(() -> {
                try (Socket peer = listener.accept()) { peer.setSoTimeout(5000); session.run(peer); }
                catch (Exception e) { throw new RuntimeException(e); }
            });
        }
        int port() { return listener.getLocalPort(); }
        void verify() throws Exception { future.get(10, TimeUnit.SECONDS); }
        public void close() throws Exception { listener.close(); executor.shutdownNow(); }
    }
    private static byte[] read(InputStream input, int size) throws IOException {
        byte[] data = new byte[size]; new DataInputStream(input).readFully(data); return data;
    }
    private static int sum(byte[] data) { int sum=0; for(byte b:data) sum+=b&255;return sum; }
    private static byte[] packet(Socket peer, int expected) throws Exception {
        ByteBuffer header=ByteBuffer.wrap(read(peer.getInputStream(),24)).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(expected,header.getInt()); header.getInt();header.getInt();
        int length=header.getInt(), checksum=header.getInt();assertEquals(expected^-1,header.getInt());
        byte[] data=read(peer.getInputStream(),length);assertEquals(checksum,sum(data));return data;
    }
    private static void send(Socket peer,int command,int arg0,int arg1,byte[] data) throws Exception {
        ByteBuffer header=ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(command).putInt(arg0).putInt(arg1).putInt(data.length).putInt(sum(data)).putInt(command^-1);
        // Fragment every header to exercise exact reads over TCP.
        for(byte b:header.array())peer.getOutputStream().write(b);
        peer.getOutputStream().write(data);peer.getOutputStream().flush();
    }
    private static byte[] bytes(String s){return s.getBytes(StandardCharsets.UTF_8);}

    @Test public void authorisesNewKeyAndAcknowledgesShellOutput() throws Exception {
        KeyPair key=keys(); byte[] token=new byte[20];new SecureRandom().nextBytes(token);
        try(Daemon daemon=new Daemon(peer->{
            packet(peer,LocalAdbConnection.CNXN);
            send(peer,LocalAdbConnection.AUTH,1,0,token);
            byte[] signature=packet(peer,LocalAdbConnection.AUTH);
            Cipher rsa=Cipher.getInstance("RSA/ECB/PKCS1Padding");rsa.init(Cipher.DECRYPT_MODE,key.getPublic());
            byte[] digestInfo=rsa.doFinal(signature);
            assertEquals(35,digestInfo.length);
            assertArrayEquals(token,Arrays.copyOfRange(digestInfo,15,35));
            send(peer,LocalAdbConnection.AUTH,1,0,token);
            assertArrayEquals(LocalAdbConnection.publicKey((RSAPublicKey)key.getPublic()),packet(peer,LocalAdbConnection.AUTH));
            send(peer,LocalAdbConnection.CNXN,0x01000000,4096,bytes("device::\0"));
            assertEquals("shell:echo timer\0",new String(packet(peer,LocalAdbConnection.OPEN),StandardCharsets.UTF_8));
            send(peer,LocalAdbConnection.OKAY,7,1,new byte[0]);
            send(peer,LocalAdbConnection.WRTE,7,1,bytes("timer\n"));packet(peer,LocalAdbConnection.OKAY);
            send(peer,LocalAdbConnection.CLSE,7,1,new byte[0]);packet(peer,LocalAdbConnection.CLSE);
        }); LocalAdbConnection adb=new LocalAdbConnection()) {
            adb.connect(daemon.port(),key);assertEquals("timer",adb.shell("echo timer"));daemon.verify();
        }
    }
    @Test public void ignoresLateCloseForCompletedStreamAcrossSuccessiveShellCommands() throws Exception {
        try (Daemon daemon = new Daemon(peer -> {
            packet(peer, LocalAdbConnection.CNXN);
            send(peer, LocalAdbConnection.CNXN, 0x01000000, 4096, bytes("device::\0"));
            for (int stream = 1; stream <= 4; stream++) {
                packet(peer, LocalAdbConnection.OPEN);
                if (stream > 1) send(peer, LocalAdbConnection.CLSE, 0, stream - 1, new byte[0]);
                send(peer, LocalAdbConnection.OKAY, stream + 10, stream, new byte[0]);
                send(peer, LocalAdbConnection.WRTE, stream + 10, stream, bytes("ok\n"));
                packet(peer, LocalAdbConnection.OKAY);
                send(peer, LocalAdbConnection.CLSE, stream + 10, stream, new byte[0]);
                packet(peer, LocalAdbConnection.CLSE);
            }
        }); LocalAdbConnection adb = new LocalAdbConnection()) {
            adb.connect(daemon.port(), keys());
            for (int stream = 0; stream < 4; stream++) assertEquals("ok", adb.shell("echo ok"));
            daemon.verify();
        }
    }
    @Test public void stillRejectsCloseForFutureStream() throws Exception {
        try (Daemon daemon = new Daemon(peer -> {
            packet(peer, LocalAdbConnection.CNXN);
            send(peer, LocalAdbConnection.CNXN, 0x01000000, 4096, bytes("device::\0"));
            packet(peer, LocalAdbConnection.OPEN);
            send(peer, LocalAdbConnection.CLSE, 0, 2, new byte[0]);
        }); LocalAdbConnection adb = new LocalAdbConnection()) {
            adb.connect(daemon.port(), keys());
            assertThrows(IOException.class, () -> adb.shell("echo ok"));
            daemon.verify();
        }
    }
    @Test public void publicKeyHasAndroidModulusAndMontgomeryParameters() throws Exception {
        RSAPublicKey publicKey=(RSAPublicKey)keys().getPublic();
        byte[] auth=LocalAdbConnection.publicKey(publicKey);
        String base64=new String(auth,StandardCharsets.UTF_8).split(" ")[0];
        ByteBuffer wire=ByteBuffer.wrap(Base64.getDecoder().decode(base64)).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(64,wire.getInt());
        assertEquals(-1,publicKey.getModulus().intValue()*wire.getInt());
        byte[] n=new byte[256],r=new byte[256];wire.get(n);wire.get(r);
        reverse(n);reverse(r);
        assertEquals(publicKey.getModulus(),new BigInteger(1,n));
        assertEquals(BigInteger.ONE.shiftLeft(4096).mod(publicKey.getModulus()),new BigInteger(1,r));
        assertEquals(65537,wire.getInt());
    }
    private static void reverse(byte[] a){for(int i=0;i<a.length/2;i++){byte b=a[i];a[i]=a[a.length-1-i];a[a.length-1-i]=b;}}
    @Test public void rejectsOversizedOrCorruptFrames() throws Exception {
        KeyPair key=keys();
        for(int length:new int[]{-1,4097}) {
            try(Daemon daemon=new Daemon(peer->{
                packet(peer,LocalAdbConnection.CNXN);
                ByteBuffer header=ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
                header.putInt(LocalAdbConnection.AUTH).putInt(1).putInt(0).putInt(length).putInt(0).putInt(LocalAdbConnection.AUTH^-1);
                peer.getOutputStream().write(header.array());
            });LocalAdbConnection adb=new LocalAdbConnection()) {
                assertThrows(IOException.class,()->adb.connect(daemon.port(),key));daemon.verify();
            }
        }
    }
    @Test public void refusesTlsOrUnexpectedAuthentication() throws Exception {
        try(Daemon daemon=new Daemon(peer->{packet(peer,LocalAdbConnection.CNXN);
            send(peer,0x534c5453,1,0,new byte[0]);
        });LocalAdbConnection adb=new LocalAdbConnection()) {
            assertThrows(IOException.class,()->adb.connect(daemon.port(),keys()));daemon.verify();
        }
    }
}
