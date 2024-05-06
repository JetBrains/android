// "Add Android View constructors using '@JvmOverloads'" "true"
// ERROR: This type has a constructor, and thus must be initialized here
// K2-ERROR: None of the following functions are applicable: [constructor(context: Context): View, constructor(context: Context, attrs: AttributeSet?): View, constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int): View]
// WITH_STDLIB

package com.myapp.activity

import android.view.View

class Foo : View<caret>