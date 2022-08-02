package me.example.test


class TestInterceptor(val lambda: (String) -> Unit) : Unit {
    fun cool() {
      lambda()
    }
}
