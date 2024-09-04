// "Add Android View constructors using '@JvmOverloads'" "true"
// K1-ERROR: This type has a constructor, and thus must be initialized here
// K2-ERROR: This type has a constructor, so it must be initialized here.
// K2-ERROR: None of the following candidates is applicable:<br>constructor(context: Context): TextView<br>constructor(context: Context, attrs: AttributeSet?): TextView<br>constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int): TextView
// WITH_STDLIB

package com.myapp.activity

import android.content.Context
import android.util.AttributeSet
import android.view.TextView

class Foo @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : TextView<caret>(context, attrs)