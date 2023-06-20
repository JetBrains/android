package com.example.lib

class LibKotlinClass {
    fun foo() {
        val ids = intArrayOf(
            R.string.from_lib,
            R.string.another_lib_string,
            R.string.from_sublib,
            com.example.sublib.R.string.from_sublib,

            // Styleable_Attr has more logic than other ResourceTypes
            R.styleable.styleable_from_lib_Attr_from_lib,
            R.styleable.styleable_from_sublib_Attr_from_sublib,
            com.example.sublib.R.styleable.styleable_from_sublib_Attr_from_sublib
        )
    }
}