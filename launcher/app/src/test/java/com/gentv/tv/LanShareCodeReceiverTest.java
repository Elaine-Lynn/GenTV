package com.gentv.tv;

import org.junit.Test;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.Random;

import static org.junit.Assert.assertTrue;

public class LanShareCodeReceiverTest {
    @Test public void receiverListensOnAFourDigitPort() throws Exception {
        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        for (int seed = 0; seed < 5; seed++) {
            try (ServerSocket socket = LanShareCodeReceiver.bindFourDigitPort(loopback, new Random(seed))) {
                int port = socket.getLocalPort();
                assertTrue(String.valueOf(port), port >= LanShareCodeReceiver.FIRST_PORT
                        && port <= LanShareCodeReceiver.LAST_PORT);
            }
        }
    }

    @Test public void aTakenPortIsSkipped() throws Exception {
        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        int taken;
        try (ServerSocket first = LanShareCodeReceiver.bindFourDigitPort(loopback, new Random(7))) {
            taken = first.getLocalPort();
            // The same seed picks the same first port; it is in use, so another four-digit port is chosen.
            try (ServerSocket second = LanShareCodeReceiver.bindFourDigitPort(loopback, new Random(7))) {
                assertTrue(second.getLocalPort() != taken);
                assertTrue(second.getLocalPort() >= 2000 && second.getLocalPort() <= 9999);
            }
        }
    }
}
