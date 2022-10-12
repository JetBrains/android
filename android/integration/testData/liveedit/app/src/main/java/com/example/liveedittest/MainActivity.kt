package com.example.liveedittest

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.liveedittest.ui.theme.LiveEditTestTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LiveEditTestTheme {
                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
                    Greeting("Android")
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String) {
    // EASILY SEARCHABLE LINE (this comment exists for the test to be able to find/replace from here until the next comment)
    Log.i("MainActivity", "Before editing")
    Text(text = "Hello, $name!")
    // END SEARCH
}

@Composable
fun DefaultPreview() {
    LiveEditTestTheme {
        Greeting("Android")
    }
}
