package google.simpleapplication

import android.app.Activity
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem

class MyActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my)
    }
}

