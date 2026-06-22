package com.vpntunnel.app.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.vpntunnel.app.MainActivity
import com.vpntunnel.app.R
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class CoreVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.vpnapp.vpn.START"
        const val ACTION_STOP  = "com.vpnapp.vpn.STOP"
        const val EXTRA_SERVER = "server_json"

        private const val TAG          = "CoreVpnService"
        private const val CHANNEL_ID   = "vpn_channel"
        private const val NOTIF_ID     = 1001
        private const val SOCKS_PORT   = 10808

        @Volatile var instance: CoreVpnService? = null
    }

    private var tunInterface: ParcelFileDescriptor? = null
    private var singboxProcess: Process? = null
    private var tun2socksProcess: Process? = null
    private var trafficExecutor: ScheduledExecutorService? = null
    private var currentServerJson: String? = null

    // ── Lifecycle ─────────────────────────────────────────────

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val serverJson = intent.getStringExtra(EXTRA_SERVER) ?: return START_NOT_STICKY
                startVpn(serverJson)
            }
            ACTION_STOP -> stopVpn()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }

    // ── Start VPN ─────────────────────────────────────────────

    private fun startVpn(serverJson: String) {
        instance = this
        currentServerJson = serverJson

        emitState("connecting")
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("সংযুক্ত হচ্ছে..."))

        Thread {
            try {
                // 1. Extract binaries from assets
                val singboxBin   = extractBinary("sing-box")
                val tun2socksBin = extractBinary("tun2socks")

                // 2. Build sing-box config
                val server = JSONObject(serverJson)
                val configJson = buildSingboxConfig(server)
                val configFile = File(filesDir, "singbox_config.json")
                configFile.writeText(configJson)

                // 3. Start sing-box (SOCKS5 mode, no TUN)
                startSingbox(singboxBin, configFile)
                Thread.sleep(1200) // sing-box startup time

                // 4. Create TUN interface
                val tun = buildTunInterface() ?: throw IOException("TUN creation failed")
                tunInterface = tun

                // 5. Start tun2socks — bridges TUN ↔ sing-box SOCKS5
                startTun2socks(tun2socksBin, tun.fd)

                // 6. Done
                emitState("connected")
                startForeground(NOTIF_ID, buildNotification("সংযুক্ত • ${server.optString("name", "")}"))
                startTrafficMonitor()

            } catch (e: Exception) {
                Log.e(TAG, "VPN start failed: ${e.message}", e)
                emitStateError(e.message ?: "Unknown error")
                stopVpn()
            }
        }.start()
    }

    // ── Stop VPN ──────────────────────────────────────────────

    private fun stopVpn() {
        emitState("disconnecting")
        trafficExecutor?.shutdownNow()
        trafficExecutor = null

        tun2socksProcess?.destroy()
        tun2socksProcess = null

        singboxProcess?.destroy()
        singboxProcess = null

        try { tunInterface?.close() } catch (_: IOException) {}
        tunInterface = null

        instance = null
        emitState("disconnected")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ── TUN Interface ─────────────────────────────────────────

    private fun buildTunInterface(): ParcelFileDescriptor? {
        return try {
            Builder()
                .setSession("VpnApp")
                .addAddress("172.19.0.1", 30)
                .addDnsServer("8.8.8.8")
                .addDnsServer("8.8.4.4")
                .addRoute("0.0.0.0", 0)
                .setMtu(9000)
                .setBlocking(false)
                .also {
                    // bypass sing-box and tun2socks themselves
                    protect(singboxProcess?.let { p ->
                        try { p.javaClass.getDeclaredField("pid").also { f -> f.isAccessible = true }.get(p) as Int } catch (_: Exception) { -1 }
                    } ?: -1)
                }
                .establish()
        } catch (e: Exception) {
            Log.e(TAG, "TUN build error: ${e.message}", e)
            null
        }
    }

    // ── sing-box process (SOCKS5 inbound) ─────────────────────

    private fun startSingbox(binary: File, configFile: File) {
        val configJson = configFile.readText()
        // Rewrite to SOCKS5 only mode (no tun, we handle tun ourselves)
        val json = JSONObject(configJson)
        val inboundsArr = org.json.JSONArray()
        inboundsArr.put(JSONObject().apply {
            put("type", "socks")
            put("tag", "socks-in")
            put("listen", "127.0.0.1")
            put("listen_port", SOCKS_PORT)
            put("sniff", true)
        })
        json.put("inbounds", inboundsArr)
        // remove route auto_detect_interface for SOCKS mode
        json.optJSONObject("route")?.remove("auto_detect_interface")
        configFile.writeText(json.toString())

        singboxProcess = ProcessBuilder(binary.absolutePath, "run", "-c", configFile.absolutePath)
            .directory(filesDir)
            .redirectErrorStream(true)
            .start()

        Log.d(TAG, "sing-box started (PID=${singboxProcess?.let { try { it.javaClass.getDeclaredField("pid").also { f -> f.isAccessible = true }.get(it) } catch (_: Exception) { "?" } }})")
    }

    // ── tun2socks process ─────────────────────────────────────

    private fun startTun2socks(binary: File, tunFd: Int) {
        tun2socksProcess = ProcessBuilder(
            binary.absolutePath,
            "-device", "fd://$tunFd",
            "-proxy", "socks5://127.0.0.1:$SOCKS_PORT",
            "-loglevel", "error"
        )
            .directory(filesDir)
            .redirectErrorStream(true)
            .start()

        Log.d(TAG, "tun2socks started")
    }

    // ── Binary extraction ─────────────────────────────────────

    private fun extractBinary(name: String): File {
        val dest = File(filesDir, name)
        if (dest.exists() && dest.length() > 0L) {
            dest.setExecutable(true)
            return dest
        }
        val abi = Build.SUPPORTED_ABIS.firstOrNull { it in listOf("arm64-v8a", "armeabi-v7a", "x86_64") } ?: "arm64-v8a"
        val assetName = "$name-$abi"
        try {
            assets.open(assetName).use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            }
        } catch (_: IOException) {
            // try without ABI suffix
            assets.open(name).use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            }
        }
        dest.setExecutable(true)
        return dest
    }

    // ── sing-box config builder ────────────────────────────────

    private fun buildSingboxConfig(server: JSONObject): String {
        val type    = server.getString("type")
        val host    = server.getString("host")
        val port    = server.getInt("port")
        val config  = server.optJSONObject("config") ?: JSONObject()

        val outbound = JSONObject()
        outbound.put("tag", "proxy")
        outbound.put("server", host)
        outbound.put("server_port", port)

        when (type) {
            "vless" -> {
                outbound.put("type", "vless")
                outbound.put("uuid", config.optString("uuid"))
                outbound.put("flow", "")
                if (config.optBoolean("tls", false)) {
                    outbound.put("tls", JSONObject().apply {
                        put("enabled", true)
                        put("server_name", config.optString("sni", host))
                        put("insecure", false)
                    })
                }
                if (config.optString("network", "ws") == "ws") {
                    outbound.put("transport", JSONObject().apply {
                        put("type", "ws")
                        put("path", config.optString("path", "/vless"))
                        put("headers", JSONObject().apply { put("Host", config.optString("sni", host)) })
                    })
                }
            }
            "vmess" -> {
                outbound.put("type", "vmess")
                outbound.put("uuid", config.optString("uuid"))
                outbound.put("security", "auto")
                outbound.put("alter_id", config.optInt("alterId", 0))
                if (config.optBoolean("tls", false)) {
                    outbound.put("tls", JSONObject().apply {
                        put("enabled", true)
                        put("server_name", config.optString("sni", host))
                    })
                }
                if (config.optString("network", "ws") == "ws") {
                    outbound.put("transport", JSONObject().apply {
                        put("type", "ws")
                        put("path", config.optString("path", "/vmess"))
                        put("headers", JSONObject().apply { put("Host", config.optString("sni", host)) })
                    })
                }
            }
            "trojan" -> {
                outbound.put("type", "trojan")
                outbound.put("password", config.optString("password"))
                outbound.put("tls", JSONObject().apply {
                    put("enabled", true)
                    put("server_name", config.optString("sni", host))
                    put("insecure", false)
                })
                if (config.optString("network", "tcp") == "ws") {
                    outbound.put("transport", JSONObject().apply {
                        put("type", "ws")
                        put("path", config.optString("path", "/trojan"))
                        put("headers", JSONObject().apply { put("Host", config.optString("sni", host)) })
                    })
                }
            }
            "ssh" -> {
                outbound.put("type", "ssh")
                outbound.put("user", config.optString("username", "root"))
                outbound.put("password", config.optString("password"))
            }
            "v2ray" -> {
                val proto = config.optString("protocol", "vmess")
                outbound.put("type", proto)
                outbound.put("uuid", config.optString("uuid"))
                outbound.put("security", "auto")
                if (config.optBoolean("tls", false)) {
                    outbound.put("tls", JSONObject().apply {
                        put("enabled", true)
                        put("server_name", config.optString("sni", host))
                    })
                }
                if (config.optString("network", "ws") == "ws") {
                    outbound.put("transport", JSONObject().apply {
                        put("type", "ws")
                        put("path", config.optString("path", "/v2ray"))
                        put("headers", JSONObject().apply { put("Host", config.optString("sni", host)) })
                    })
                }
            }
        }

        val root = JSONObject()
        root.put("log", JSONObject().apply {
            put("level", "error")
            put("timestamp", true)
        })
        root.put("inbounds", org.json.JSONArray())   // will be replaced before start
        root.put("outbounds", org.json.JSONArray().apply {
            put(outbound)
            put(JSONObject().apply { put("type", "direct"); put("tag", "direct") })
            put(JSONObject().apply { put("type", "dns"); put("tag", "dns-out") })
        })
        root.put("route", JSONObject().apply {
            put("rules", org.json.JSONArray().apply {
                put(JSONObject().apply { put("protocol", "dns"); put("outbound", "dns-out") })
            })
            put("final", "proxy")
        })
        return root.toString(2)
    }

    // ── Traffic monitor ───────────────────────────────────────

    private fun startTrafficMonitor() {
        var lastRx = 0L; var lastTx = 0L
        trafficExecutor = Executors.newSingleThreadScheduledExecutor()
        trafficExecutor?.scheduleAtFixedRate({
            try {
                val rx = readNetStat("rx_bytes")
                val tx = readNetStat("tx_bytes")
                val deltaRx = if (rx >= lastRx) rx - lastRx else rx
                val deltaTx = if (tx >= lastTx) tx - lastTx else tx
                lastRx = rx; lastTx = tx
                emitTraffic(deltaRx, deltaTx)
            } catch (_: Exception) {}
        }, 1, 1, TimeUnit.SECONDS)
    }

    private fun readNetStat(stat: String): Long {
        return try {
            File("/proc/net/dev").readLines()
                .drop(2)
                .filter { !it.contains("lo:") && !it.contains("tun0:") }
                .sumOf { line ->
                    val parts = line.trim().split("\\s+".toRegex())
                    when (stat) {
                        "rx_bytes" -> parts.getOrNull(1)?.toLongOrNull() ?: 0L
                        "tx_bytes" -> parts.getOrNull(9)?.toLongOrNull() ?: 0L
                        else -> 0L
                    }
                }
        } catch (_: Exception) { 0L }
    }

    // ── Events ───────────────────────────────────────────────

    private fun emitState(state: String) {
        // Broadcast to React Native via VpnModule
        android.os.Handler(mainLooper).post {
            try {
                val params = com.facebook.react.bridge.Arguments.createMap()
                params.putString("state", state)
                // find active VpnModule in RN host
                (application as? com.facebook.react.ReactApplication)
                    ?.reactNativeHost
                    ?.reactInstanceManager
                    ?.currentReactContext
                    ?.getNativeModule(VpnModule::class.java)
                    ?.sendEvent("vpnStateChanged", params)
            } catch (_: Exception) {}
        }
    }

    private fun emitStateError(error: String) {
        android.os.Handler(mainLooper).post {
            try {
                val params = com.facebook.react.bridge.Arguments.createMap()
                params.putString("state", "error")
                params.putString("error", error)
                (application as? com.facebook.react.ReactApplication)
                    ?.reactNativeHost
                    ?.reactInstanceManager
                    ?.currentReactContext
                    ?.getNativeModule(VpnModule::class.java)
                    ?.sendEvent("vpnStateChanged", params)
            } catch (_: Exception) {}
        }
    }

    private fun emitTraffic(bytesIn: Long, bytesOut: Long) {
        android.os.Handler(mainLooper).post {
            try {
                val params = com.facebook.react.bridge.Arguments.createMap()
                params.putDouble("bytesIn", bytesIn.toDouble())
                params.putDouble("bytesOut", bytesOut.toDouble())
                (application as? com.facebook.react.ReactApplication)
                    ?.reactNativeHost
                    ?.reactInstanceManager
                    ?.currentReactContext
                    ?.getNativeModule(VpnModule::class.java)
                    ?.sendEvent("vpnTraffic", params)
            } catch (_: Exception) {}
        }
    }

    // ── Notification ─────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "VPN Service", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "VPN connection status" }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VPN App")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0x6366F1.or(0xFF shl 24))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
