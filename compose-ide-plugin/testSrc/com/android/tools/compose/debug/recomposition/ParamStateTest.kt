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
package com.android.tools.compose.debug.recomposition

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for [ParamState]
 */
class ParamStateTest {
  @Test
  fun oneFullValue() {
    val states = ParamState.decode(listOf(0b1111101011000110100010000010101))

    assertThat(states.map { it.getDisplayName() }).containsExactly(
      "Different",
      "Same",
      "Uncertain",
      "Same",
      "Different",
      "Static",
      "Unstable",
      "Unstable",
      "Unstable",
      "Unstable",
    ).inOrder()
  }

  @Test
  fun multipleValuea() {
    val states = ParamState.decode(listOf(
      0b1111101011000110100010000010101,
      0b1111101011000110100010000010100,
      0b0010,
    ))

    assertThat(states.map { it.getDisplayName() }).containsExactly(
      "Different",
      "Same",
      "Uncertain",
      "Same",
      "Different",
      "Static",
      "Unstable",
      "Unstable",
      "Unstable",
      "Unstable",
      "Different",
      "Same",
      "Uncertain",
      "Same",
      "Different",
      "Static",
      "Unstable",
      "Unstable",
      "Unstable",
      "Unstable",
      "Same",
      "Uncertain",
      "Uncertain",
      "Uncertain",
      "Uncertain",
      "Uncertain",
      "Uncertain",
      "Uncertain",
      "Uncertain",
      "Uncertain",
      ).inOrder()
  }
}
