package com.other.folder;

import com.example.app.R;

public class AppOtherPackageJavaClass {
    public void foo() {
        int[] ids = new int[] {
                R.string.from_lib,
                R.string.another_lib_string,
                R.string.from_sublib,
                com.example.lib.R.string.from_lib,
                com.example.lib.R.string.another_lib_string,
                com.example.lib.R.string.from_sublib,
                com.example.sublib.R.string.from_sublib,

                // Styleable_Attr has more logic than other ResourceTypes
                R.styleable.styleable_from_lib_Attr_from_lib,
                R.styleable.styleable_from_sublib_Attr_from_sublib,
                com.example.lib.R.styleable.styleable_from_lib_Attr_from_lib,
                com.example.lib.R.styleable.styleable_from_sublib_Attr_from_sublib,
                com.example.sublib.R.styleable.styleable_from_sublib_Attr_from_sublib,
        };
    }
}