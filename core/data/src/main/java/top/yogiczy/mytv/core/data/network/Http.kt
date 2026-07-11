package top.yogiczy.mytv.core.data.network

import android.os.Build
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.TlsVersion
import top.yogiczy.mytv.core.data.utils.Globals
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * HTTP 客户端工具类
 */
object Http {
    /**
     * 获取 OkHttpClient 实例
     */
    val client: OkHttpClient by lazy {
        configure(OkHttpClient.Builder()).build()
    }

    /**
     * 获取安全信任管理器（包含系统根证书和 ISRG Root X1）
     */
    private fun getSafeTrustManager(): X509TrustManager {
        // 1. 获取系统默认 TrustManager
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(null as KeyStore?)
        val systemTrustManager = tmf.trustManagers.first { it is X509TrustManager } as X509TrustManager

        try {
            // 2. 加载自定义 ISRG Root X1 证书
            val cf = CertificateFactory.getInstance("X.509")
            
            // 尝试从多个可能的包名加载资源
            val packageNames = listOf(
                Globals.context.packageName,
                "top.yogiczy.mytv.core.data",
                "top.yogiczy.mytv.tv",
                "top.yogiczy.mytv.mobile"
            )
            
            var certId = 0
            for (pkg in packageNames) {
                certId = Globals.context.resources.getIdentifier("isrgrootx1", "raw", pkg)
                if (certId != 0) break
            }

            if (certId == 0) {
                // 如果还是找不到，尝试通过反射直接访问 core:data 的 R 类
                try {
                    val rClass = Class.forName("top.yogiczy.mytv.core.data.R\$raw")
                    certId = rClass.getField("isrgrootx1").getInt(null)
                } catch (e: Exception) {
                    // ignore
                }
            }

            if (certId == 0) return systemTrustManager

            val certInputStream = Globals.context.resources.openRawResource(certId)
            val customCerts = certInputStream.use { cf.generateCertificates(it) }

            // 3. 将所有自定义证书加入 KeyStore
            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null, null)
                customCerts.forEachIndexed { index, cert ->
                    setCertificateEntry("custom-cert-$index", cert)
                }
            }

            // 4. 为自定义 KeyStore 创建 TrustManager
            val customTmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            customTmf.init(keyStore)
            val customTrustManager = customTmf.trustManagers.first { it is X509TrustManager } as X509TrustManager

            // 5. 返回一个组合的 TrustManager
            return object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                    try {
                        systemTrustManager.checkClientTrusted(chain, authType)
                    } catch (e: Exception) {
                        customTrustManager.checkClientTrusted(chain, authType)
                    }
                }

                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                    try {
                        systemTrustManager.checkServerTrusted(chain, authType)
                    } catch (e: Exception) {
                        // 如果系统校验失败，尝试使用自定义库校验
                        // 注意：这里需要捕获异常，如果自定义库也失败，则抛出最后的异常
                        customTrustManager.checkServerTrusted(chain, authType)
                    }
                }

                override fun getAcceptedIssuers(): Array<X509Certificate> {
                    val systemIssuers = systemTrustManager.acceptedIssuers
                    val customIssuers = customTrustManager.acceptedIssuers
                    val result = Array(systemIssuers.size + customIssuers.size) { i ->
                        if (i < systemIssuers.size) systemIssuers[i] else customIssuers[i - systemIssuers.size]
                    }
                    return result
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return systemTrustManager
        }
    }

    /**
     * 配置 OkHttpClient.Builder
     */
    /**
     * 配置 OkHttpClient.Builder
     */
    fun configure(builder: OkHttpClient.Builder): OkHttpClient.Builder {
        val trustManager = getSafeTrustManager()

        try {
            // 优先尝试使用 TLSv1.2 实例，这是 Android 6.0 兼容性的关键
            val sc = try {
                SSLContext.getInstance("TLSv1.2")
            } catch (e: Exception) {
                SSLContext.getInstance("TLS")
            }
            
            sc.init(null, arrayOf(trustManager), java.security.SecureRandom())
            builder.sslSocketFactory(
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) Tls12SocketFactory(sc.socketFactory)
                else sc.socketFactory,
                trustManager
            )
        } catch (ex: Exception) {
            ex.printStackTrace()
        }

        // 针对老旧设备优化主机名校验
        // 某些直播源（如 fjddt.com）由于 CDN 或 SNI 适配问题，在老安卓上可能出现校验失败
        builder.hostnameVerifier { hostname, session ->
            if (hostname == "fjddt.com" || hostname.endsWith(".fjddt.com")) {
                true
            } else {
                try {
                    val defaultVerifier = javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier()
                    defaultVerifier.verify(hostname, session)
                } catch (e: Exception) {
                    false
                }
            }
        }

        // 配置更广泛的兼容性协议
        val cs = ConnectionSpec.Builder(ConnectionSpec.COMPATIBLE_TLS)
            .tlsVersions(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2, TlsVersion.TLS_1_1, TlsVersion.TLS_1_0)
            .build()
        
        builder.connectionSpecs(listOf(ConnectionSpec.MODERN_TLS, cs, ConnectionSpec.CLEARTEXT))

        return builder
    }
}
