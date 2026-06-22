package com.vpntunnel.app

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate
import com.vpntunnel.app.vpn.VpnModule

class MainActivity : ReactActivity() {

    override fun getMainComponentName(): String = "VpnApp"

    override fun createReactActivityDelegate(): ReactActivityDelegate =
        DefaultReactActivityDelegate(this, mainComponentName, fabricEnabled)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VpnModule.VPN_REQUEST_CODE) {
            VpnModule.onVpnPermissionResult(resultCode)
        }
    }
}
