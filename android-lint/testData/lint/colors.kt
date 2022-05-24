@file:Suppress("unused", "UNUSED_PARAMETER")

package test.pkg

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes

class Stuff(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    fun setThing(@ColorInt colorOne: Int, @ColorRes colorTwo: Int) {
        setBgHelper(colorOne, colorTwo)
    }
}

fun View.setBgHelper(@ColorInt colorOne: Int, @ColorRes colorRes: Int) {
    // Do stuff
}

