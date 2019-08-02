package p1.p2

import android.view.View

class WhenTest {
    fun measure(mode: Int) {
        val `val` = View.MeasureSpec.getMode(mode)
        <warning descr="Switch statement on an `int` with known associated constant missing case `MeasureSpec.EXACTLY`, `MeasureSpec.UNSPECIFIED`">when<caret></warning> (`val`) {
            View.MeasureSpec.AT_MOST -> {
            // something
        }
        }
    }
}
