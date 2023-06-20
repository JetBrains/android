package com.example.app;

public class AppJavaClass {
    public void foo() {
        int[] ids = new int[] {
                R.string.from_app,
                R.string.another_app_string,
                R.string.from_lib,
                R.string.another_lib_string,
                R.string.from_sublib,
                com.example.lib.R.string.from_lib,
                com.example.lib.R.string.another_lib_string,
                com.example.lib.R.string.from_sublib,
                com.example.sublib.R.string.from_sublib,

                // Styleable_Attr has more logic than other ResourceTypes
                R.styleable.styleable_from_app_Attr_from_app,
                R.styleable.styleable_from_lib_Attr_from_lib,
                R.styleable.styleable_from_sublib_Attr_from_sublib,
                com.example.lib.R.styleable.styleable_from_lib_Attr_from_lib,
                com.example.lib.R.styleable.styleable_from_sublib_Attr_from_sublib,
                com.example.sublib.R.styleable.styleable_from_sublib_Attr_from_sublib,
        };
    }
}