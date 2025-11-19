// "Add Android View constructors using '@JvmOverloads'" "true"
// K2-ERROR: SUPERTYPE_NOT_INITIALIZED: TextView

package android.view

import android.util.AttributeSet
import android.content.Context

public open class View {
    constructor(context: Context)
    constructor(context: Context, attrs: AttributeSet?)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int)
}