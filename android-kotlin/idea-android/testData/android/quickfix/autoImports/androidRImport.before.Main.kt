// "Import class 'R'" "true"
// K1-ERROR: Unresolved reference: R
// K2-ERROR: Unresolved reference 'R'.
// DO_NOT_IMPORT: android.support.v7.appcompat.R
// SKIP-K1 (b/304360782)

package com.myapp.activity

fun test() {
    val a = <caret>R.layout.activity_test_kotlin
}