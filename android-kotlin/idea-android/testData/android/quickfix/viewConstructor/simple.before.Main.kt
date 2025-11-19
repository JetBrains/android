// "Add Android View constructors using '@JvmOverloads'" "true"
// K1-ERROR: SUPERTYPE_NOT_INITIALIZED: View
// K2-ERROR: SUPERTYPE_NOT_INITIALIZED: View
// K2-ERROR: NONE_APPLICABLE: class Foo : View
// WITH_STDLIB

package com.myapp.activity

import android.view.View

class Foo : View<caret>