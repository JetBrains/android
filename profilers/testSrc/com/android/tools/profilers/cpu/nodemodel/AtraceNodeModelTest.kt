/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.profilers.cpu.nodemodel

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AtraceNodeModelTest {

  @Test
  fun nameWithoutNumbers() {
    val name = "MyNoNumber Name"
    assertThat(name).isEqualTo(AtraceNodeModel(name).fullName)
  }

  @Test
  fun nameEndsWithNumbers() {
    val name = "Name Ends Number 1234"
    val expected = "Name Ends Number ###"
    assertThat(expected).isEqualTo(AtraceNodeModel(name).fullName)
  }

  @Test
  fun nameRandomNumber() {
    val name = "Name 1 number"
    assertThat(name).isEqualTo(AtraceNodeModel(name).fullName)
  }
}