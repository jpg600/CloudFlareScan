
package com.example.cfscanner

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.SecureRandom
import java.security.cert.X509Certificate

class Scanner(
    private val ipList: List<String>,
    private val port: Int,
    private val onProgress: (current: Int, total: Int) -> Unit,
    private val onResult: (IpInfo) -> Unit,
    private val onComplete: () -> Unit
) {
    private val executor = Executors.newFixedThreadPool(50)
    private val isCancelled = AtomicBoolean(false)
    private val completedCount = AtomicInteger(0)
    private val totalIps = ipList.size
    private val mainHandler = Handler(Looper.getMainLooper())

    private val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    })

    private val sslContext: SSLContext by lazy {
        SSLContext.getInstance("TLS").apply {
            init(null, trustAllCerts, SecureRandom())
        }
    }

    private val DEBUG = true

    fun start() {
        if (totalIps == 0) {
            mainHandler.post { onComplete() }
            return
        }

        for (ip in ipList) {
            if (isCancelled.get()) break
            executor.submit {
                try {
                    if (!isCancelled.get()) {
                        scanIp(ip)
                    }
                } catch (e: Exception) {
                    if (DEBUG) Log.e("Scanner", "Unhandled exception for $ip", e)
                } finally {
                    val completed = completedCount.incrementAndGet()
                    mainHandler.post {
                        onProgress(completed, totalIps)
                        if (completed == totalIps) {
                            onComplete()
                        }
                    }
                }
            }
        }
        executor.shutdown()
    }

    fun stop() {
        isCancelled.set(true)
        executor.shutdownNow()
    }

    private fun doublePing(ip: String, port: Int): Long? {
        val threshold = if (ip.contains(':')) 350 else 230
        val latencies = mutableListOf<Long>()

        for (attempt in 1..2) {
            try {
                val start = System.currentTimeMillis()
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(ip, port), threshold)
                    val delay = System.currentTimeMillis() - start
                    if (delay <= threshold) {
                        latencies.add(delay)
                    } else {
                        return null
                    }
                }
            } catch (e: Exception) {
                return null
            }
            if (attempt == 1) Thread.sleep(20)
        }
        return if (latencies.size == 2) latencies.minOrNull() else null
    }

    private fun scanIp(ip: String) {
        val startTotal = System.currentTimeMillis()
        val delay = doublePing(ip, port) ?: return
        val pingTime = System.currentTimeMillis() - startTotal

        if (DEBUG) Log.d("Scanner", "Ping成功: $ip, delay=$delay, 耗时=${pingTime}ms")

        val coloStart = System.currentTimeMillis()
        val colo = fetchColoViaTrace(ip)
        val coloTime = System.currentTimeMillis() - coloStart

        val finalColo = colo ?: "N/A"
        val totalTime = System.currentTimeMillis() - startTotal
        if (DEBUG) Log.d("Scanner", "IP: $ip, delay=$delay, colo=$finalColo, ping耗时=${pingTime}ms, colo耗时=${coloTime}ms, 总耗时=${totalTime}ms")

        val info = IpInfo(ip, port, delay, finalColo)
        mainHandler.post { onResult(info) }
    }

    private fun fetchColoViaTrace(ip: String): String? {
        val testHost = "speed.cloudflare.com"
        val path = "/cdn-cgi/trace"
        val request = buildString {
            append("GET $path HTTP/1.1\r\n")
            append("Host: $testHost\r\n")
            append("User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36\r\n")
            append("Accept: */*\r\n")
            append("Connection: close\r\n\r\n")
        }.toByteArray()

        val timeout = 3000
        var socket: Socket? = null

        try {
            val rawSocket = Socket()
            rawSocket.connect(InetSocketAddress(ip, port), timeout)
            rawSocket.soTimeout = timeout

            val sslFactory = sslContext.socketFactory as SSLSocketFactory
            val sslSocket = sslFactory.createSocket(rawSocket, testHost, port, true) as SSLSocket
            sslSocket.startHandshake()
            socket = sslSocket

            socket.getOutputStream().write(request)
            socket.getOutputStream().flush()

            val input = socket.getInputStream()
            val buffer = ByteArray(4096)
            val responseData = mutableListOf<Byte>()
            var bytesRead: Int
            val readStart = System.currentTimeMillis()

            while (true) {
                if (System.currentTimeMillis() - readStart > timeout) {
                    if (DEBUG) Log.d("Scanner", "读取超时: $ip")
                    break
                }
                bytesRead = input.read(buffer)
                if (bytesRead == -1) break
                responseData.addAll(buffer.sliceArray(0 until bytesRead).toList())
                if (responseData.size > 8192) break
            }

            val responseStr = String(responseData.toByteArray(), Charsets.ISO_8859_1)
            val headersEnd = responseStr.indexOf("\r\n\r\n")

            if (headersEnd != -1) {
                val bodyStr = responseStr.substring(headersEnd + 4)
                val lines = bodyStr.split("\r\n", "\n")
                for (line in lines) {
                    if (line.startsWith("colo=")) {
                        val coloValue = line.substringAfter("=").trim()
                        if (coloValue.isNotEmpty() && !coloValue.equals("UNKNOWN", ignoreCase = true)) {
                            return coloValue.uppercase()
                        }
                    }
                }
            } else {
                val lines = responseStr.split("\r\n", "\n")
                for (line in lines) {
                    if (line.startsWith("colo=")) {
                        val coloValue = line.substringAfter("=").trim()
                        if (coloValue.isNotEmpty() && !coloValue.equals("UNKNOWN", ignoreCase = true)) {
                            return coloValue.uppercase()
                        }
                    }
                }
            }
        } catch (e: SocketTimeoutException) {
            if (DEBUG) Log.d("Scanner", "超时: $ip")
        } catch (e: Exception) {
            if (DEBUG) Log.d("Scanner", "异常: $ip, ${e.message}")
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
        return null
    }
}
