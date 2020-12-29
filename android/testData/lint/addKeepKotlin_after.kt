@file:Suppress("unused", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package p1.p2

import android.animation.ObjectAnimator
import android.widget.Button
import androidx.annotation.Keep

class AnimatorTest {
    fun testObjectAnimator(button: Button?) {
        val myObject: Any = MyObject()
        val animator1 =
            ObjectAnimator.ofInt(myObject, "prop1", 0, 1, 2, 5)
    }

    private class MyObject {
        @Keep
        fun setProp1(x: Int) {}
    }
}
