package com.myrbsdk

import android.annotation.SuppressLint
import android.app.sdksandbox.SandboxedSdk
import android.app.sdksandbox.SandboxedSdkProvider
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout

@SuppressLint("NewApi")
class MyAdsSdkEntryPoint : SandboxedSdkProvider() {

    @SuppressLint("Override")
    override fun onLoadSdk(params: Bundle): SandboxedSdk {
        log("onLoadSdk called")
        return SandboxedSdk(MySdkStubDelegate(MySdkImpl()))
    }

    @SuppressLint("Override")
    override fun getView(windowContext: Context, bundle: Bundle, width: Int, height: Int): View {
        log("getView called")
        val layout = LinearLayout(windowContext)
        layout.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        )
        layout.setBackgroundColor(Color.RED)
        return layout
    }

    @SuppressLint("Override")
    override fun onDataReceived(bundle: Bundle, dataReceivedCallback: DataReceivedCallback) {
        log("onDataReceived called")
    }

    private fun log(message: String) {
        Log.i("SDK", message)
    }
}