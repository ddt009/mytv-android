package top.yogiczy.mytv.core.data.network

import java.net.InetAddress
import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * TLS 1.2 Socket Factory
 */
class Tls12SocketFactory(private val delegate: SSLSocketFactory) : SSLSocketFactory() {

    override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites

    override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites

    override fun createSocket(s: Socket, host: String, port: Int, autoClose: Boolean): Socket {
        return patch(delegate.createSocket(s, host, port, autoClose))
    }

    override fun createSocket(host: String, port: Int): Socket {
        return patch(delegate.createSocket(host, port))
    }

    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket {
        return patch(delegate.createSocket(host, port, localHost, localPort))
    }

    override fun createSocket(host: InetAddress, port: Int): Socket {
        return patch(delegate.createSocket(host, port))
    }

    override fun createSocket(host: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket {
        return patch(delegate.createSocket(host, port, localAddress, localPort))
    }

    private fun patch(socket: Socket): Socket {
        if (socket is SSLSocket) {
            val supported = socket.supportedProtocols
            val enabled = mutableListOf<String>()
            
            if (supported.contains("TLSv1.3")) enabled.add("TLSv1.3")
            if (supported.contains("TLSv1.2")) enabled.add("TLSv1.2")
            
            if (enabled.isNotEmpty()) {
                socket.enabledProtocols = enabled.toTypedArray()
            }
        }
        return socket
    }
}
