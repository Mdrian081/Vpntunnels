package com.vpntunnel.app.vpn

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import org.json.JSONObject

class VpnModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    companion object {
        const val VPN_REQUEST_CODE = 0x0F
        private var preparePromise: Promise? = null

        fun onVpnPermissionResult(resultCode: Int) {
            if (resultCode == Activity.RESULT_OK) {
                preparePromise?.resolve(true)
            } else {
                preparePromise?.resolve(false)
            }
            preparePromise = null
        }
    }

    override fun getName(): String = "VpnModule"

    // ── prepare ────────────────────────────────────────────
    @ReactMethod
    fun prepare(promise: Promise) {
        val intent = VpnService.prepare(reactContext)
        if (intent == null) {
            promise.resolve(true)
            return
        }
        preparePromise = promise
        currentActivity?.startActivityForResult(intent, VPN_REQUEST_CODE)
            ?: promise.resolve(false)
    }

    // ── connect ─────────────────────────────────────────────
    @ReactMethod
    fun connect(serverJson: String, promise: Promise) {
        try {
            val server = JSONObject(serverJson)
            val intent = Intent(reactContext, CoreVpnService::class.java).apply {
                action = CoreVpnService.ACTION_START
                putExtra(CoreVpnService.EXTRA_SERVER, serverJson)
            }
            reactContext.startForegroundService(intent)
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("CONNECT_ERROR", e.message, e)
        }
    }

    // ── disconnect ──────────────────────────────────────────
    @ReactMethod
    fun disconnect(promise: Promise) {
        try {
            val intent = Intent(reactContext, CoreVpnService::class.java).apply {
                action = CoreVpnService.ACTION_STOP
            }
            reactContext.startService(intent)
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("DISCONNECT_ERROR", e.message, e)
        }
    }

    // ── event emitter ────────────────────────────────────────
    @ReactMethod
    fun addListener(eventName: String) {}

    @ReactMethod
    fun removeListeners(count: Int) {}

    internal fun sendEvent(eventName: String, params: WritableMap) {
        reactContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit(eventName, params)
    }
}
