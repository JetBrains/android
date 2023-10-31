package me.example.feature.b

import me.example.test.CommonClass
import me.example.test.TestInterceptor

class Test {
    val commonClass = CommonClass("test")
    val jvmClass = TestInterceptor {}
}
