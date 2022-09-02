package p1.p2

import android.view.View

class WhenTest {
    fun measure(mode: Int) {
        val `val` = View.MeasureSpec.getMode(mode)
        when (`val`) {
            View.MeasureSpec.AT_MOST -> {
            // something
        }

            View.MeasureSpec.EXACTLY -> {
                TODO()
            }

            View.MeasureSpec.UNSPECIFIED -> {
                TODO()
            }
        }
    }
}
