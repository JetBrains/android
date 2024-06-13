@file:Suppress("unused", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package p1.p2

import android.animation.ObjectAnimator
import android.widget.Button

class AnimatorTest {
    fun testObjectAnimator(button: Button?) {
        val myObject: Any = MyObject()
        val animator1 =
            ObjectAnimator.ofInt(myObject, <warning descr="This method is accessed from an ObjectAnimator so it should be annotated with `@Keep` to ensure that it is not discarded or renamed in release builds (The method referenced here (setProp1) has not been annotated with `@Keep` which means it could be discarded or renamed in release builds)">"prop1"</warning>, 0, 1, 2, 5)
    }

    private class MyObject {
        fun <warning descr="This method is accessed from an ObjectAnimator so it should be annotated with `@Keep` to ensure that it is not discarded or renamed in release builds">setProp<caret>1(x: Int)</warning> {}
    }
}
