package com.example.targetsdkupgradesample32_33.bodysensor

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.targetsdkupgradesample32_33.R


class BodySensorsActivity : AppCompatActivity(), SensorEventListener {

    lateinit var heart_rate_lable: TextView

    lateinit var sMgr: SensorManager
    lateinit var heart_rate: Sensor
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_body_sensors)
        heart_rate_lable = findViewById<TextView>(R.id.sensor_value)

Toast.makeText(applicationContext,"Add hw.sensors.heart_rate =yes in config.ini folder to enable sensor", Toast.LENGTH_LONG).show()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS)
            != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted
            // Ask for permision
            Log.d("permission", "permission denied to sensors - requesting it")
            val permissions =
                arrayOf(Manifest.permission.BODY_SENSORS)
            ActivityCompat.requestPermissions(this, permissions, 1)
            initialize_sensor()
        }
        else
            initialize_sensor()


    }

    private fun initialize_sensor() {

        sMgr = this.getSystemService(SENSOR_SERVICE) as SensorManager


        heart_rate = sMgr.getDefaultSensor(Sensor.TYPE_HEART_RATE)

        if(heart_rate!=null)
        {
            heart_rate_lable.text ="Load Sensor"
            sMgr.registerListener(this, heart_rate, SensorManager.SENSOR_DELAY_UI)
        }

        else
        {
            heart_rate_lable.text ="No load sensor"
            Toast.makeText(applicationContext,"Add hw.sensors.heart_rate =yes in config.ini folder to enable sensor", Toast.LENGTH_LONG).show()
        }




    }

    override fun onSensorChanged(sensorEvent: SensorEvent?) {
        if (sensorEvent?.sensor?.getType() == Sensor.TYPE_HEART_RATE && sensorEvent.values.size > 0) {
            val newValue = Math.round(sensorEvent.values.get(0))
            heart_rate_lable.text = "Sensor Value updated"+newValue.toString()
            Toast.makeText(applicationContext,"Sensor value updated to "+newValue, Toast.LENGTH_LONG).show()
        }


    }

    override fun onResume() {
        super.onResume()
        //Toast.makeText(applicationContext, "On resume", Toast.LENGTH_LONG).show()
        if(heart_rate!=null)
        {
            sMgr.registerListener(this, heart_rate, SensorManager.SENSOR_DELAY_UI)
        }

    }

    override fun onPause() {
        super.onPause()
        Toast.makeText(applicationContext, "On pause", Toast.LENGTH_LONG).show()

    }


    override fun onDestroy() {
        super.onDestroy()
        // Toast.makeText(applicationContext, "On Destroy", Toast.LENGTH_LONG).show()
        sMgr.unregisterListener(this)
    }
    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
        System.out.println("onAccuracyChanged - accuracy: " + p1)
    }
}
