package com.mycompany.myapp.subpackage

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.mycompany.myapp.R

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}