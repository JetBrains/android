// "Add Android View constructors using '@JvmOverloads'" "true"
// K2-ERROR: SUPERTYPE_NOT_INITIALIZED: TextView
// K2-ERROR: NONE_APPLICABLE: class Foo : TextView
// WITH_STDLIB

package com.myapp.activity

import android.content.Context
import android.util.AttributeSet
import android.view.TextView

class Foo @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : TextView<caret>(context, attrs)