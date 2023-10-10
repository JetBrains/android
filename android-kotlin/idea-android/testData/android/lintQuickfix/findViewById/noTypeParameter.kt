// INTENTION_TEXT: Convert cast to findViewById<TextView>(...)
// K1_INSPECTION_CLASS: org.jetbrains.kotlin.android.inspection.K1TypeParameterFindViewByIdInspection
// K2_INSPECTION_CLASS: org.jetbrains.kotlin.android.inspection.K2TypeParameterFindViewByIdInspection
// INTENTION_NOT_AVAILABLE

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView


class OtherActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_other)

        val tvHello = <caret>findViewById(savedInstanceState) as TextView
    }
}

private fun findViewById(bundle: Bundle): View = View()

class R {
    object layout {
        val activity_other = 100500
    }

    object id {
        val tvHello = 0
    }
}