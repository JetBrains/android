package com.example.targetsdkupgradesample32_33.nonsdk

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.os.PowerManager
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.targetsdkupgradesample32_33.R


class ReflectionActivity : AppCompatActivity() {
          override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    setContentView(R.layout.activity_reflection)
                    var act: Activity
          }


          fun disableScreenshot(view: View?) {
                    val act = Activity()
                    val method = act.javaClass.getDeclaredMethod("setDisablePreviewScreenshots", Boolean::class.java)
                    method.isAccessible = true
                    method.invoke(act, true)
          }

          fun checkDeviceIdleMode(view: View) {

                    val mgr = this.getSystemService(Context.POWER_SERVICE) as PowerManager
                    val method = mgr.javaClass.getDeclaredMethod("isLightDeviceIdleMode")
                    method.isAccessible = true
                   if(method.invoke(mgr) as Boolean)
                   {
                             Toast.makeText(applicationContext,"Device in LightDeviceIdleMode",Toast.LENGTH_SHORT).show()
                   }
                    else
                   {
                             Toast.makeText(applicationContext,"Device not in LightDeviceIdleMode",Toast.LENGTH_SHORT).show()
                   }

          }
}