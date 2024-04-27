// INTENTION_TEXT: Convert cast to findViewById<TextView>(...)
// K1_INSPECTION_CLASS: org.jetbrains.kotlin.android.inspection.K1TypeParameterFindViewByIdInspection
// K2_INSPECTION_CLASS: org.jetbrains.kotlin.android.inspection.K2TypeParameterFindViewByIdInspection

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.TextView


class OtherActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_other)

        val tvHello = ((run {
            SomeObject.InnerClass()
        })?.findViewById(this, R.id.tvHello)) as <caret>TextView?
    }
}

class R {
    object layout {
        val activity_other = 100500
    }

    object id {
        val tvHello = 0
    }
}

object SomeObject {
    class InnerClass {
        fun <T : View> findViewById(activity: Activity, id: Int): T? =
            activity.findViewById(id)
    }
}
