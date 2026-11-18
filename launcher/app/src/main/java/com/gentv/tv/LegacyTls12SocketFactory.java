package com.gentv.tv;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/** Enables the platform's TLS 1.2 support on Android 4.2-4.4 without changing trust checks. */
final class LegacyTls12SocketFactory extends SSLSocketFactory {
    private final SSLSocketFactory delegate;

    LegacyTls12SocketFactory(SSLSocketFactory delegate) { this.delegate = delegate; }

    private Socket tls12(Socket socket) {
        if (socket instanceof SSLSocket) {
            SSLSocket ssl = (SSLSocket) socket;
            for (String protocol : ssl.getSupportedProtocols()) {
                if ("TLSv1.2".equals(protocol)) {
                    ssl.setEnabledProtocols(new String[]{"TLSv1.2"});
                    break;
                }
            }
        }
        return socket;
    }

    @Override public String[] getDefaultCipherSuites() { return delegate.getDefaultCipherSuites(); }
    @Override public String[] getSupportedCipherSuites() { return delegate.getSupportedCipherSuites(); }
    @Override public Socket createSocket() throws IOException { return tls12(delegate.createSocket()); }
    @Override public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
        return tls12(delegate.createSocket(socket, host, port, autoClose));
    }
    @Override public Socket createSocket(String host, int port) throws IOException {
        return tls12(delegate.createSocket(host, port));
    }
    @Override public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
        return tls12(delegate.createSocket(host, port, localHost, localPort));
    }
    @Override public Socket createSocket(InetAddress host, int port) throws IOException {
        return tls12(delegate.createSocket(host, port));
    }
    @Override public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
        return tls12(delegate.createSocket(address, port, localAddress, localPort));
    }
}
