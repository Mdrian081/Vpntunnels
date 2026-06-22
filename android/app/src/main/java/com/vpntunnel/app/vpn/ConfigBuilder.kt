package com.vpntunnel.app.vpn

import org.json.JSONArray
import org.json.JSONObject

/**
 * Firebase server object থেকে sing-box JSON config তৈরি করে।
 * Supports: VLESS, VMess, Trojan, SSH, V2Ray
 * Mode: SOCKS5 inbound (tun2socks handles TUN routing)
 */
object ConfigBuilder {

    fun build(server: JSONObject, socksPort: Int = 10808): String {
        val type   = server.getString("type")
        val host   = server.getString("host")
        val port   = server.getInt("port")
        val config = server.optJSONObject("config") ?: JSONObject()

        val outbound = buildOutbound(type, host, port, config)

        return JSONObject().apply {
            put("log", JSONObject().apply {
                put("level", "error")
                put("timestamp", true)
            })
            put("inbounds", JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "socks")
                    put("tag", "socks-in")
                    put("listen", "127.0.0.1")
                    put("listen_port", socksPort)
                    put("sniff", true)
                    put("sniff_override_destination", false)
                })
                put(JSONObject().apply {
                    put("type", "mixed")
                    put("tag", "mixed-in")
                    put("listen", "127.0.0.1")
                    put("listen_port", socksPort + 1)
                })
            })
            put("outbounds", JSONArray().apply {
                put(outbound)
                put(JSONObject().apply { put("type", "direct"); put("tag", "direct") })
                put(JSONObject().apply { put("type", "block");  put("tag", "block")  })
                put(JSONObject().apply { put("type", "dns");    put("tag", "dns-out") })
            })
            put("route", JSONObject().apply {
                put("rules", JSONArray().apply {
                    put(JSONObject().apply { put("protocol", "dns"); put("outbound", "dns-out") })
                    put(JSONObject().apply { put("network", "udp"); put("port", 443); put("outbound", "block") })
                })
                put("final", "proxy")
            })
            put("dns", JSONObject().apply {
                put("servers", JSONArray().apply {
                    put(JSONObject().apply { put("tag", "remote"); put("address", "tls://1.1.1.1"); put("detour", "proxy") })
                    put(JSONObject().apply { put("tag", "local");  put("address", "223.5.5.5");     put("detour", "direct") })
                })
                put("final", "remote")
                put("strategy", "prefer_ipv4")
            })
        }.toString(2)
    }

    private fun buildOutbound(type: String, host: String, port: Int, c: JSONObject): JSONObject {
        return JSONObject().apply {
            put("tag", "proxy")
            put("server", host)
            put("server_port", port)

            when (type) {
                "vless" -> {
                    put("type", "vless")
                    put("uuid", c.optString("uuid"))
                    put("flow", "")
                    if (c.optBoolean("tls", false)) put("tls", tlsObj(c, host))
                    if (c.optString("network", "ws") == "ws") put("transport", wsObj(c, host, "/vless"))
                    if (c.optString("network", "ws") == "grpc") put("transport", grpcObj(c))
                }
                "vmess" -> {
                    put("type", "vmess")
                    put("uuid", c.optString("uuid"))
                    put("security", "auto")
                    put("alter_id", c.optInt("alterId", 0))
                    if (c.optBoolean("tls", false)) put("tls", tlsObj(c, host))
                    if (c.optString("network", "ws") == "ws") put("transport", wsObj(c, host, "/vmess"))
                    if (c.optString("network", "ws") == "grpc") put("transport", grpcObj(c))
                }
                "trojan" -> {
                    put("type", "trojan")
                    put("password", c.optString("password"))
                    put("tls", tlsObj(c, host))
                    if (c.optString("network", "tcp") == "ws") put("transport", wsObj(c, host, "/trojan"))
                }
                "ssh" -> {
                    put("type", "ssh")
                    put("user", c.optString("username", "root"))
                    put("password", c.optString("password"))
                }
                "v2ray" -> {
                    val proto = c.optString("protocol", "vmess")
                    put("type", proto)
                    put("uuid", c.optString("uuid"))
                    if (proto == "vmess") { put("security", "auto"); put("alter_id", 0) }
                    if (c.optBoolean("tls", false)) put("tls", tlsObj(c, host))
                    if (c.optString("network", "ws") == "ws") put("transport", wsObj(c, host, "/v2ray"))
                }
                else -> put("type", "direct")
            }
        }
    }

    private fun tlsObj(c: JSONObject, host: String) = JSONObject().apply {
        put("enabled", true)
        put("server_name", c.optString("sni", host).takeIf { it.isNotBlank() } ?: host)
        put("insecure", false)
    }

    private fun wsObj(c: JSONObject, host: String, defaultPath: String) = JSONObject().apply {
        put("type", "ws")
        put("path", c.optString("path", defaultPath).takeIf { it.isNotBlank() } ?: defaultPath)
        put("headers", JSONObject().apply {
            put("Host", c.optString("sni", host).takeIf { it.isNotBlank() } ?: host)
        })
    }

    private fun grpcObj(c: JSONObject) = JSONObject().apply {
        put("type", "grpc")
        put("service_name", c.optString("path", "").trimStart('/').takeIf { it.isNotBlank() } ?: "grpc")
    }
}
