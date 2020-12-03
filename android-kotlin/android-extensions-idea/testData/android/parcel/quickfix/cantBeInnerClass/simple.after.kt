// "Remove 'inner' modifier" "true"
// ERROR: 'Parcelable' can't be an inner class
// WITH_RUNTIME

package com.myapp.activity

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

class Foo {
    @Parcelize<caret>
    class Bar : Parcelable
}