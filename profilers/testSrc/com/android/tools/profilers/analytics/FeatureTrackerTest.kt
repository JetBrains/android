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
package com.android.tools.profilers.analytics

import com.android.tools.profilers.FakeFeatureTracker
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidProfilerEvent.Loading
import org.junit.Before
import org.junit.Test
import kotlin.test.fail

class FeatureTrackerTest {
  private data class LoggingFeatureTracker(val log: MutableList<Any>, val backend: FeatureTracker): FeatureTracker by backend {
    override fun trackLoading(loading: Loading) {
      log.add(loading)
    }
  }
  private val log = mutableListOf<Any>()
  private val tracker = LoggingFeatureTracker(log, FakeFeatureTracker())

  @Before
  fun setUp() {
    log.clear()
  }

  @Test
  fun `successful loading tracked at start and finish`() {
    tracker.trackLoading(Loading.Type.UNSPECIFIED,
                         sizeKb = 1024,
                         measure = { 42L.also { log.add("measured") } }) {
      log.add("run")
    }

    assertThat(log).hasSize(4)
    // track attempt
    assertThat(log[0]).isInstanceOf(Loading::class.java)
    assertThat((log[0] as Loading).isSuccess).isFalse()
    // run
    assertThat(log[1]).isEqualTo("run")
    // measure
    assertThat(log[2]).isEqualTo("measured")
    // track success
    assertThat(log[3]).isInstanceOf(Loading::class.java)
    assertThat((log[3] as Loading).isSuccess).isTrue()
  }

  @Test
  fun `failed loading does not send success metrics`() {
    try {
      tracker.trackLoading(Loading.Type.UNSPECIFIED,
                           sizeKb = 1024,
                           measure = { 42L.also { log.add("measured") } }) {
        log.add("run")
        throw RuntimeException()
      }
    } catch(_: Exception) {
    } finally {
      assertThat(log).hasSize(2)
      // track attempt
      assertThat(log[0]).isInstanceOf(Loading::class.java)
      assertThat((log[0] as Loading).isSuccess).isFalse()
      // run
      assertThat(log[1]).isEqualTo("run")
    }
  }
}