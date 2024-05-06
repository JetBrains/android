package p1.p2

import android.view.View
import android.view.View.MeasureSpec.AT_MOST
import android.view.View.MeasureSpec.EXACTLY
import android.view.View.MeasureSpec.UNSPECIFIED

class WhenTest {
    fun measure(mode: Int) {
        val `val` = View.MeasureSpec.getMode(mode)
        when (`val`) {
            AT_MOST -> {
            // something
        }

            EXACTLY -> {
                TODO()
            }

            UNSPECIFIED -> {
                TODO()
            }
        }
    }
}
