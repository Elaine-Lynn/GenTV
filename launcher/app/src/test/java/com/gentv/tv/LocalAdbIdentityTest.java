package com.gentv.tv;

import org.junit.Test;
import java.security.*;
import static org.junit.Assert.*;

public class LocalAdbIdentityTest {
    @Test public void persistedIdentityCanStillAuthenticateWithoutPrivateCrtCast() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair original = generator.generateKeyPair();
        KeyPair restored = LocalAdbIdentity.decode(LocalAdbIdentity.encode(original));
        assertArrayEquals(original.getPublic().getEncoded(), restored.getPublic().getEncoded());
        Signature signature = Signature.getInstance("SHA256withRSA");
        byte[] challenge = new byte[]{1, 2, 3, 4};
        signature.initSign(restored.getPrivate()); signature.update(challenge);
        byte[] signed = signature.sign();
        signature.initVerify(original.getPublic()); signature.update(challenge);
        assertTrue(signature.verify(signed));
    }

    @Test(expected = java.io.IOException.class) public void invalidLengthIsRejected() throws Exception {
        LocalAdbIdentity.decode(new byte[]{127, -1, -1, -1});
    }
}
