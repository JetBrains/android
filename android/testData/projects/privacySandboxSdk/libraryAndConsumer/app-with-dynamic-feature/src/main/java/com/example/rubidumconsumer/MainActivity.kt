package com.example.rubidumconsumer

import android.annotation.SuppressLint
import android.app.sdksandbox.LoadSdkException
import android.app.sdksandbox.RequestSurfacePackageException
import android.app.sdksandbox.SandboxedSdk
import android.app.sdksandbox.SdkSandboxManager
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.OutcomeReceiver
import android.util.Log
import android.view.SurfaceControlViewHost
import android.view.SurfaceView
import android.view.View
import android.widget.Button
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity

@SuppressLint("NewApi")
class MainActivity : AppCompatActivity() {

    private fun log(message: String) {
        Log.i("APP-WITH-DYNAMIC-FEATURES", message)
    }

    private lateinit var button: Button
    private lateinit var sdkSandboxManager: SdkSandboxManager
    private lateinit var surfaceView: SurfaceView
    private lateinit var sdk : SandboxedSdk


    @RequiresApi(api = 33)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        sdkSandboxManager = applicationContext.getSystemService(
            SdkSandboxManager::class.java
        )
        surfaceView = findViewById(R.id.rendered_view)
        button = findViewById(R.id.button)

        loadSdk()
        registerButton()
    }

    public override fun onResume() {
        super.onResume()
        surfaceView.visibility = View.INVISIBLE
    }

    private fun loadSdk() {
        log("Loading sandbox SDK")
        val callback = LoadSdkCallbackImpl()
        sdkSandboxManager.loadSdk(
            SDK_PACKAGE_NAME, Bundle(), { obj: Runnable -> obj.run() }, callback
        )
    }


    @RequiresApi(api = 33)
    private fun registerButton() {
        button.setOnClickListener {
            log("Getting SurfaceView")
            Handler(Looper.getMainLooper()).post {
                val params = Bundle()
                params.putInt(SdkSandboxManager.EXTRA_WIDTH_IN_PIXELS, surfaceView.getWidth())
                params.putInt(SdkSandboxManager.EXTRA_HEIGHT_IN_PIXELS, surfaceView.getHeight())
                params.putInt(SdkSandboxManager.EXTRA_DISPLAY_ID, getDisplay()?.getDisplayId()!!)
                params.putBinder(SdkSandboxManager.EXTRA_HOST_TOKEN, surfaceView.getHostToken())
                sdkSandboxManager.requestSurfacePackage(
                    SDK_PACKAGE_NAME,
                    params,
                    { obj: Runnable -> obj.run() },
                    RequestSurfacePackageCallbackImpl()
                )
            }
        }
    }

    @RequiresApi(api = 33)
    private inner class LoadSdkCallbackImpl : OutcomeReceiver<SandboxedSdk, LoadSdkException> {
        @SuppressLint("Override")
        override fun onResult(sandboxedSdk: SandboxedSdk) {
            log("SDK loaded successfully")
            sdk = sandboxedSdk;
        }

        @SuppressLint("Override")
        override fun onError(error: LoadSdkException) {
            log("SDK loading failed: ${error.extraInformation}")
        }
    }

    @RequiresApi(api = 33)
    private inner class RequestSurfacePackageCallbackImpl :
        OutcomeReceiver<Bundle?, RequestSurfacePackageException?> {
        @SuppressLint("Override")
        override fun onResult(response: Bundle) {
            log("Surface view received")
            Handler(Looper.getMainLooper()).post {
                val surfacePackage: SurfaceControlViewHost.SurfacePackage? = response.getParcelable(
                    SdkSandboxManager.EXTRA_SURFACE_PACKAGE, SurfaceControlViewHost.SurfacePackage::class.java
                )
                surfaceView.setChildSurfacePackage(surfacePackage!!)
                surfaceView.visibility = View.VISIBLE
            }
        }

        @SuppressLint("Override")
        override fun onError(error: RequestSurfacePackageException) {
            log("Surface view error: ${error.extraErrorInformation}")
        }
    }

    companion object {
        // Needs to be the package name of the SDK as defined in the SdkModulesConfig.pb.json when building the SDK
        const val SDK_PACKAGE_NAME = "com.myrbsdk"
    }
}