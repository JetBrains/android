package com.myrbsdk


class MySdkImpl: MySdk {
    override suspend fun doMath(x: Int, y: Int): Int {
        return x + y
    }
}