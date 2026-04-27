// This file should not be edited manually! See go/template-diff-tests
package template.test.`in`.shared

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session

class MyCarAppSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        return MyCarAppScreen(carContext)
    }
}