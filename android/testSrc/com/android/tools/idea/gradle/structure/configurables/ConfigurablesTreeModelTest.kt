/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables

import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.assertThat
import org.junit.Test

class ConfigurablesTreeModelTest {

  @Test
  fun listFromGenerator() {
    val generator: ((String) -> Unit) -> Unit = { consumer ->
      consumer("A")
      consumer("B")
      consumer("C")
      consumer("D")
      consumer("E")
    }
    assertThat(listFromGenerator(generator), equalTo(listOf("A", "B", "C", "D", "E")))
  }

  @Test
  fun listFromGenerator_empty() {
    val generator: ((String) -> Unit) -> Unit = {}
    assertThat(listFromGenerator(generator), equalTo(listOf()))
  }
}