package com.example.kmpfirstlib

import kotlin.test.Test

class CommonComposeTest {

  @Test
  fun emptyComposeUiTestThatPasses() {
      val x = KmpCommonFirstLibClass()
      assert(x.get() == "I'm here")
  }
}