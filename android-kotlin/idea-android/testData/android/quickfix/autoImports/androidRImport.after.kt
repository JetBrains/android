// "Import class 'R'" "true"
// K1-ERROR: UNRESOLVED_REFERENCE: R
// K2-ERROR: UNRESOLVED_REFERENCE: R
// DO_NOT_IMPORT: android.support.v7.appcompat.R
// SKIP-K1 (b/304360782)

package com.myapp.activity

import android.R

fun test() {
    val a = <caret>R.layout.activity_test_kotlin
}