package com.myapp

import android.content.Context

fun Context.testPlurals(quantity: Int) <fold text='{...}' expand='true'>{
    val plural1 = <fold text='"Reference One"' expand='false'>resources.getQuantityString(R.plurals.first, quantity)</fold>
    val plural2 = <fold text='"Quantity One"' expand='false'>this.getResources().getQuantityString(R.plurals.second, quantity)</fold>
    val plural3 = <fold text='"{3} song found from {25}."' expand='false'>resources.getQuantityString(R.plurals.numberOfSongsAvailable, quantity, 3, 25)</fold>
}</fold>

