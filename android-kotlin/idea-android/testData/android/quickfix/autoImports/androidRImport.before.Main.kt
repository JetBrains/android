// "Import class 'R'" "true"
// K2-ERROR: UNRESOLVED_REFERENCE: R
// DO_NOT_IMPORT: android.support.v7.appcompat.R

package com.myapp.activity

fun test() {
    val a = <caret>R.layout.activity_test_kotlin
}