// "Add Android View constructors using '@JvmOverloads'" "true"
// K1-ERROR: This type has a constructor, and thus must be initialized here
// K2-ERROR: This type has a constructor, so it must be initialized here.
// K2-ERROR: None of the following functions is applicable: [constructor(context: Context): View, constructor(context: Context, attrs: AttributeSet?): View, constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int): View]
// WITH_STDLIB

package com.myapp.activity

import android.content.Context
import android.util.AttributeSet
import android.view.View

class Foo @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View<caret>(context, attrs, defStyleAttr)