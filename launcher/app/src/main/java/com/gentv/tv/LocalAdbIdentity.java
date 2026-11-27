package com.gentv.tv;

import android.content.Context;
import android.util.AtomicFile;
import java.io.*;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/** Persist both halves: some Android RSA providers do not expose private CRT parameters. */
final class LocalAdbIdentity {
    static synchronized KeyPair key(Context context) throws Exception {
        AtomicFile file = new AtomicFile(new File(CompatApi.noBackupDir(context), "memory-local-adb.bin"));
        if (file.getBaseFile().exists()) {
            if (file.getBaseFile().length() > 8192) throw new IOException("Invalid identity size");
            return decode(file.readFully());
        }
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        FileOutputStream stream = file.startWrite();
        try { stream.write(encode(pair)); file.finishWrite(stream); }
        catch (IOException failure) { file.failWrite(stream); throw failure; }
        return pair;
    }

    static byte[] encode(KeyPair pair) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(buffer);
        byte[] privateBytes = pair.getPrivate().getEncoded(), publicBytes = pair.getPublic().getEncoded();
        output.writeInt(privateBytes.length); output.write(privateBytes);
        output.writeInt(publicBytes.length); output.write(publicBytes);
        return buffer.toByteArray();
    }

    static KeyPair decode(byte[] encoded) throws IOException, GeneralSecurityException {
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded));
        byte[] privateBytes = field(input), publicBytes = field(input);
        if (input.available() != 0) throw new IOException("Invalid identity format");
        KeyFactory factory = KeyFactory.getInstance("RSA");
        return new KeyPair(factory.generatePublic(new X509EncodedKeySpec(publicBytes)),
                factory.generatePrivate(new PKCS8EncodedKeySpec(privateBytes)));
    }

    private static byte[] field(DataInputStream input) throws IOException {
        int size = input.readInt();
        if (size < 1 || size > 4096) throw new IOException("Invalid identity field");
        byte[] value = new byte[size]; input.readFully(value); return value;
    }
}
