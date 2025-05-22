package com.example;

public class MainActivity {
    protected void onCreate() {
        int x = <error descr="Cannot resolve method 'someMethod' in 'MainActivity'">so<caret>meMethod</error>(2);
    }
}