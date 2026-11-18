package com.gentv.tv;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;

public class LegacyTls12SocketFactoryTest {
    @Test public void enablesTls12WithoutOpeningConnection() throws Exception {
        SSLSocketFactory defaultFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        try (SSLSocket socket = (SSLSocket) new LegacyTls12SocketFactory(defaultFactory).createSocket()) {
            assertArrayEquals(new String[]{"TLSv1.2"}, socket.getEnabledProtocols());
        }
    }
}
