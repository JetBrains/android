// "Add Android View constructors using '@JvmOverloads'" "true"
// K1-ERROR: This type has a constructor, and thus must be initialized here
// K2-ERROR: This type has a constructor, so it must be initialized here.
// K2-ERROR: None of the following functions is applicable: [constructor(context: Context): TextView, constructor(context: Context, attrs: AttributeSet?): TextView, constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int): TextView]
// WITH_STDLIB

package com.myapp.activity

import android.view.TextView

class Foo : TextView<caret>