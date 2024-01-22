package io.ktor.perf

import io.ktor.client.*
import io.ktor.client.engine.apache5.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.network.tls.certificates.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

private const val PORT_NUMBER = 8843
private const val SECRET = "pwd123"
private const val KEY_ALIAS = "loadTest"
private const val CLIENT_ENGINE = "cio"

@OptIn(DelicateCoroutinesApi::class)
fun main() {

    val keyStoreFile = File("build/keystore.jks")
    val keyStore = generateCertificate(
        file = keyStoreFile,
        keyAlias = KEY_ALIAS,
        keyPassword = SECRET,
        jksPassword = SECRET,
    )

    val environment = applicationEngineEnvironment {
        log = LoggerFactory.getLogger("ktor.application")
        sslConnector(
            keyStore = keyStore,
            keyAlias = KEY_ALIAS,
            keyStorePassword = { SECRET.toCharArray() },
            privateKeyPassword = { SECRET.toCharArray() }) {
            port = PORT_NUMBER
            keyStorePath = keyStoreFile
        }
        module(Application::module)
    }

    val server = embeddedServer(io.ktor.server.netty.Netty, environment).start()

    val localSslContext = SSLContext.getInstance("TLS")
    val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    trustManagerFactory.init(keyStore)
    localSslContext.init(null, trustManagerFactory?.trustManagers, null)

    val client = when(CLIENT_ENGINE.uppercase()) {
        "CIO" -> HttpClient(CIO) {
            engine {
                https {
                    trustManager = trustManagerFactory?.trustManagers?.first()
                }
            }
        }
        "APACHE", "APACHE5" -> HttpClient(Apache5) {
            engine {
                sslContext = localSslContext
            }
        }
        else -> throw IllegalStateException("Invalid engine $CLIENT_ENGINE")
    }

    val successfulRequests = AtomicInteger()
    val job = GlobalScope.launch {
        for (i in 0..10_000) {
            if (isActive) launch {
                val response = client.get("https://localhost:$PORT_NUMBER/hello")
                if (response.status.isSuccess())
                    successfulRequests.getAndIncrement()
            }
        }
    }

    runBlocking {
        job.join()
        server.stop()
    }

    println("${successfulRequests.get()} successful requests")
}

fun Application.module() {
    install(CallLogging)
    routing {
        get("/hello") {
            call.respondText("Hello, World!", ContentType.Text.Plain)
        }
    }
}