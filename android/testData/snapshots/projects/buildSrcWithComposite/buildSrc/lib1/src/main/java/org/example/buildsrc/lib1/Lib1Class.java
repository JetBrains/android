package org.example.buildsrc.lib1;

import org.example.buildsrc.lib2.Lib2Class;

public class Lib1Class {
    static {
      new Lib2Class();
    }

    public static String getString() {
        return "Lib1ClassString";
    }
}
