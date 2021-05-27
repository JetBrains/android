package com.example

import androidx.compose.runtime.Composable

val l1: (Int) -> Int = { it }
val l2: (Int) -> Int = { number ->
  // The line numbers from JVMTI of this lambda, starts AFTER this comment...
  number * number
}
val l3 = ::f3

class C1 {
  inner class C2 {
    init {
      f2({ 1 }, { 2 })
    }
  }

  fun fx() {
    f2({ 1 }, { 2 })
  }

  val l4 = ::fx

  init {
    f2({ 1 }, { 2 }, { f2({ 3 }, { 4 }) })
    f2(::f3, ::f4, { f2(::f5, ::f6) })
    f2({ 1 }, ::f6, { f2(::f5, { 2 }) })
  }
}

fun f1() {
  val x: () -> Int = { 15 }
  f2({ 1 }, { 2 }, { f2({ 3 }, { 4 }) })
  f2(l1, l2, l1)
}

fun f2(a1: (Int) -> Int, a2: (Int) -> Int, a3: (Int) -> Int = { -1 }): Int {
  return if (a2(1) + a1(2) > 8) a1(1) else a3(2)
}

fun f3(a1: Int): Int = a1 + 1
fun f4(a1: Int): Int = a1 + 1
fun f5(a1: Int): Int = a1 + 1
fun f6(a1: Int): Int = a1 + 1
fun f7(a1: () -> IntArray): Int =
  a1().sum()

fun f8(): Int = f7 {
  intArrayOf(
    1,
    2,
    3,
    4
  )
}

@Composable
fun Element(
  l1: (Int) -> Int = { it },
  l2: @Composable () -> Unit = {},
  l3: (Int) -> Int = { it },
  l4: @Composable () -> Unit = {}
) {
  l4()
}

@Composable
fun MyPart(
  l1: (Int) -> Int = { it },
  l2: (Int) -> Int = { it },
  l3: @Composable () -> Unit = {},
) {
  l3()
}

@Composable
fun MyApp() {
  MyPart({ it - 1 }) {
    Element(l1 = { it + 1 }, l2 = { Element() }, l3 = { it + 2 }) {
      Element(l1 = { it + 3 }, l3 = { it + 4 }) {
        Element()
        Element(l1 = { 1 })
      }
    }
  }
}
