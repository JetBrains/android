/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.serverflags

import com.android.testutils.time.FakeClock
import com.android.tools.idea.serverflags.protos.FlagValue
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ProjectRule
import java.time.Instant
import kotlin.time.Duration.Companion.minutes
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DynamicServerFlagServiceTest {

  @get:Rule val projectRule = ProjectRule()

  private val clock = FakeClock(Instant.now())
  private var flags = flagBuilder("before update")
  private lateinit var service: DynamicServerFlagService

  @Before
  fun setup() {
    ServerFlagServiceImpl.initializer = { ServerFlagInitializationData(-1, flags) }

    service = DynamicServerFlagServiceImpl(clock, ServerFlagServiceImpl())
  }

  @Test
  fun testNewServiceCreatedOnFirstCallToUpdateFlags() {
    assertThat(service.getString("string")).isEqualTo("before update")

    flags = flagBuilder("after update")
    service.updateFlags()
    assertThat(service.getString("string")).isEqualTo("after update")
  }

  @Test
  fun testUpdateFlagWhenTimeLessThanCacheDuration() {
    service.updateFlags()
    assertThat(service.getString("string")).isEqualTo("before update")

    flags = flagBuilder("after update")
    clock.advanceTimeBy(3.minutes)
    service.updateFlags()
    assertThat(service.getString("string")).isEqualTo("before update")
  }

  @Test
  fun testUpdateFlagsWhenTimeMoreThanCacheDuration() {
    assertThat(service.getString("string")).isEqualTo("before update")

    flags = flagBuilder("after update")
    clock.advanceTimeBy(10.minutes)
    service.updateFlags()
    assertThat(service.getString("string")).isEqualTo("after update")
  }

  private fun flagBuilder(value: String) =
    mapOf(
      "string" to
        ServerFlagValueData(0, FlagValue.newBuilder().apply { stringValue = value }.build())
    )
}
