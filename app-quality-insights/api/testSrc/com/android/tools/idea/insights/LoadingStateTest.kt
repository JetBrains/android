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
package com.android.tools.idea.insights

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Test

class LoadingStateTest {
  @Test
  fun `map Loading state should be a noop`() {
    val state = LoadingState.Loading
    assertThat(state.map(Int::toString)).isEqualTo(LoadingState.Loading)
  }

  @Test
  fun `map Ready state should transform the underlying value`() {
    val state = LoadingState.Ready(42)
    assertThat(state.map(Int::toString)).isEqualTo(LoadingState.Ready("42"))
  }

  @Test
  fun `map Unauthorized state should be a noop`() {
    val state = LoadingState.Unauthorized("failure")
    assertThat(state.map(Int::toString)).isEqualTo(state)
  }

  @Test
  fun `map Unknown state should be a noop`() {
    val state = LoadingState.UnknownFailure("failure")
    assertThat(state.map(Int::toString)).isEqualTo(state)
  }

  @Test
  fun `filterReady should return a flow with only ready elements`() {
    val ready1 = LoadingState.Ready("1")
    val ready2 = LoadingState.Ready("2")
    val flow =
      flowOf(ready1, LoadingState.Loading, ready2, LoadingState.Unauthorized("Unauthorized"))
    val result = runBlocking { flow.filterReady().toList() }
    assertThat(result).containsExactly(ready1.value, ready2.value).inOrder()
  }

  @Test
  fun `mapReady should transform ready values and leave others unmodified`() {
    val ready1 = LoadingState.Ready("1")
    val ready2 = LoadingState.Ready("2")
    val unauthorized = LoadingState.Unauthorized("Unauthorized")
    val flow = flowOf(ready1, LoadingState.Loading, ready2, unauthorized)

    val result = runBlocking { flow.mapReady(String::toInt).toList() }
    assertThat(result)
      .containsExactly(
        LoadingState.Ready(1),
        LoadingState.Loading,
        LoadingState.Ready(2),
        unauthorized
      )
      .inOrder()
  }
}
