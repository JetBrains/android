package p1.p2

// Regression test for https://issuetracker.google.com/151164628
import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.widget.TextView

@SuppressLint("AppCompatCustomView")
class TextViewEx : TextView {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    init {
        setOnTouchListener <warning descr="`onTouch` lambda should call `View#performClick` when a click is detected">{ _, _<caret> -> true }</warning>
    }
}
