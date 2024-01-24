package io.ktor.perf

import io.ktor.client.*
import io.ktor.client.engine.apache5.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.network.tls.certificates.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import kotlinx.coroutines.*
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

private const val SECURE = true
private const val HTTP_PORT = 8888
private const val HTTPS_PORT = 8843
private const val SECRET = "pwd123"
private const val KEY_ALIAS = "loadTest"
private const val CLIENT_ENGINE = "cio"
private const val KEYSTORE_FILE = "build/keystore.jks"
private const val NUMBER_OF_REQUESTS = 100_000
private const val CONNECT_TIMEOUT: Long = 15_000

@OptIn(DelicateCoroutinesApi::class)
fun main() {
    val sslConfig = buildSSLConfig()
    val server = buildServer(sslConfig)

    server.start()

    val client = buildClient(sslConfig)

    val startTime = System.currentTimeMillis()
    val successfulRequests = AtomicInteger()
    val (proto, port) = if (SECURE) "https" to HTTPS_PORT else "http" to HTTP_PORT
    val requestUrl = "$proto://localhost:$port/hello"
    server.application.log.info("Firing $NUMBER_OF_REQUESTS requests...")

    val job = GlobalScope.launch {
        for (i in 0 until NUMBER_OF_REQUESTS) {
            if (isActive) launch {
                val response = client.get(requestUrl) {
                    header(HttpHeaders.AcceptEncoding, StandardCharsets.UTF_8.displayName())
                }
                if (response.status.isSuccess() && response.bodyAsText() == "Hello, world!")
                    successfulRequests.getAndIncrement()
            }
        }
    }

    runBlocking {
        job.join()
    }
    server.stop()

    println(String.format(
        "%d of %d success in %.2f seconds",
        successfulRequests.get(),
        NUMBER_OF_REQUESTS,
        (System.currentTimeMillis() - startTime).toDouble() / 1000.0
    ))
}

data class SSLConfig(
    val keyStoreFile: File,
    val keyStore: KeyStore,
    val sslContext: SSLContext,
    val trustManagerFactory: TrustManagerFactory,
)

private fun buildSSLConfig(): SSLConfig? {
    if (!SECURE)
        return null

    val keyStoreFile = File(KEYSTORE_FILE)
    val keyStore = generateCertificate(
        file = keyStoreFile,
        keyAlias = KEY_ALIAS,
        keyPassword = SECRET,
        jksPassword = SECRET,
    )
    val localSslContext = SSLContext.getInstance("TLS")
    val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    trustManagerFactory.init(keyStore)
    localSslContext.init(null, trustManagerFactory?.trustManagers, null)

    return SSLConfig(
        keyStoreFile,
        keyStore,
        localSslContext,
        trustManagerFactory,
    )
}

private fun buildServer(
    sslConfig: SSLConfig?
) = embeddedServer(
    Netty,
    applicationProperties {
        module {
            routing {
                get("/hello") {
                    call.respondText("Hello, world!", ContentType.Text.Plain)
                }
            }
        }
    }
) {
    if (SECURE) {
        sslConnector(
            keyStore = sslConfig!!.keyStore,
            keyAlias = KEY_ALIAS,
            keyStorePassword = { SECRET.toCharArray() },
            privateKeyPassword = { SECRET.toCharArray() }
        ) {
            port = HTTPS_PORT
            keyStorePath = sslConfig.keyStoreFile.absoluteFile
        }
    } else {
        connector {
            port = HTTP_PORT
        }
    }
}

private fun buildClient(
    sslConfig: SSLConfig?,
) = when (CLIENT_ENGINE.uppercase()) {
    "CIO" -> HttpClient(CIO) {
        engine {
            requestTimeout = CONNECT_TIMEOUT * 2
            endpoint {
                connectTimeout = CONNECT_TIMEOUT
                keepAliveTime = CONNECT_TIMEOUT
            }
            https {
                trustManager = sslConfig?.trustManagerFactory?.trustManagers?.first()
            }
        }
    }

    "APACHE", "APACHE5" -> HttpClient(Apache5) {
        engine {
            sslContext = sslConfig?.sslContext
            connectTimeout = CONNECT_TIMEOUT
        }
    }

    else -> throw IllegalStateException("Invalid engine $CLIENT_ENGINE")
}