// "Add Android View constructors using '@JvmOverloads'" "true"
// K1-ERROR: This type has a constructor, and thus must be initialized here
// K2-ERROR: This type has a constructor, so it must be initialized here.

package android.view

import android.util.AttributeSet
import android.content.Context

public open class View {
    constructor(context: Context)
    constructor(context: Context, attrs: AttributeSet?)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int)
}