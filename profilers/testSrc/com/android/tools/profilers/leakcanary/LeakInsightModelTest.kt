/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.profilers.leakcanary

import com.android.tools.leakcanarylib.data.Leak
import com.android.tools.profilers.FakeIdeProfilerServices
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when` as whenever

class LeakInsightModelTest {

  @Test
  fun fetchInsight_success() = runBlocking {
    val services =
      object : FakeIdeProfilerServices() {
        override fun fetchLeakInsight(rawTrace: String): Flow<String> {
          return flowOf("This is an AI insight about the leak.")
        }
      }
    val model = LeakInsightModel(services, this)
    val leak = mock(Leak::class.java)
    whenever(leak.signature).thenReturn("leak1")
    whenever(leak.toString()).thenReturn("dummy trace")

    model.onLeakSelection(leak)
    model.fetchInsight(leak)

    val state = model.currentInsight.first { it !is LoadingState.Loading }
    assertThat(state).isInstanceOf(LoadingState.Ready::class.java)
    val readyState = state as LoadingState.Ready
    assertThat(readyState.value?.rawInsight).isEqualTo("This is an AI insight about the leak.")
  }

  @Test
  fun fetchInsight_genericFailure() = runBlocking {
    val services =
      object : FakeIdeProfilerServices() {
        override fun fetchLeakInsight(rawTrace: String): Flow<String> {
          return flow { throw RuntimeException("Some generic error occurred") }
        }
      }
    val model = LeakInsightModel(services, this)
    val leak = mock(Leak::class.java)
    whenever(leak.signature).thenReturn("leak2")
    whenever(leak.toString()).thenReturn("dummy trace")

    model.onLeakSelection(leak)
    model.fetchInsight(leak)

    val state = model.currentInsight.first { it !is LoadingState.Loading }
    assertThat(state).isInstanceOf(LoadingState.Failure::class.java)
    val failureState = state as LoadingState.Failure
    assertThat(failureState.message).isEqualTo("Some generic error occurred")
  }

  @Test
  fun fetchInsight_networkFailure() = runBlocking {
    val services =
      object : FakeIdeProfilerServices() {
        override fun fetchLeakInsight(rawTrace: String): Flow<String> {
          return flow { throw java.net.UnknownHostException("cloudcode-pa.googleapis.com") }
        }
      }
    val model = LeakInsightModel(services, this)
    val leak = mock(Leak::class.java)
    whenever(leak.signature).thenReturn("leak3")
    whenever(leak.toString()).thenReturn("dummy trace")

    model.onLeakSelection(leak)
    model.fetchInsight(leak)

    val state = model.currentInsight.first { it !is LoadingState.Loading }
    assertThat(state).isInstanceOf(LoadingState.Failure::class.java)
    val failureState = state as LoadingState.Failure
    assertThat(failureState.message).isEqualTo(LeakInsightModel.NETWORK_ERROR_MESSAGE)
  }
}
