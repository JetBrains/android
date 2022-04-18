/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.android.kotlin

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class ParameterizedTest(
  private val someBoolean: Boolean
) {
  @Test
  fun exampleParameterizedTest() {}

  companion object {
    @Parameterized.Parameters(
      name = "someBoolean{0}" // comment this line to workaround
    )
    @JvmStatic
    fun myTestParameters(): List<Array<Any>> {
      return listOf(Array(1) {true}, Array(1) {false})
    }
  }
}
