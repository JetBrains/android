// "Add Android View constructors using '@JvmOverloads'" "true"
// K1-ERROR: SUPERTYPE_NOT_INITIALIZED: View
// K2-ERROR: SUPERTYPE_NOT_INITIALIZED: View
// K2-ERROR: NONE_APPLICABLE: class Foo : View
// WITH_STDLIB

package com.myapp.activity

import android.content.Context
import android.util.AttributeSet
import android.view.View

class Foo @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View<caret>(context, attrs, defStyleAttr)