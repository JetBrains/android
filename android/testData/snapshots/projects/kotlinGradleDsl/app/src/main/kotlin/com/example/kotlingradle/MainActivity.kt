package com.example.kotlingradle

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import com.example.lib.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        awesomeFunction()
    }
}
