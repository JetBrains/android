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
package com.android.tools.profilers.cpu.config

import com.android.tools.profiler.proto.Trace
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LeakCanaryConfigurationTest {

  @Test
  fun testDefaultValues() {
    val config = LeakCanaryConfiguration("MyConfig")
    assertThat(config.name).isEqualTo("MyConfig")
    assertThat(config.source).isEqualTo(LeakCanaryMode.STUDIO)
    assertThat(config.threshold).isEqualTo(5)
    assertThat(config.getTraceType()).isEqualTo(ProfilingConfiguration.TraceType.LEAKCANARY)
    assertThat(config.requiredDeviceLevel).isEqualTo(0)
  }

  @Test
  fun testIsVisible() {
    val config = LeakCanaryConfiguration("MyConfig")

    // Sub-options should always remain visible to prevent layout shifts
    config.source = LeakCanaryMode.STUDIO
    assertThat(config.isVisible("threshold")).isTrue()

    config.source = LeakCanaryMode.NATIVE
    assertThat(config.isVisible("threshold")).isTrue()
  }

  @Test
  fun testGetDescription() {
    val config = LeakCanaryConfiguration("MyConfig")

    assertThat(config.getDescription("source", LeakCanaryMode.STUDIO))
      .isEqualTo("Ignores your apps LeakCanary.Config or Shark customization logic.")
    assertThat(config.getDescription("source", LeakCanaryMode.NATIVE))
      .isEqualTo("Uses the LeakCanary.Config or Shark customization logic currently running on the device.")
    assertThat(config.getDescription("other", LeakCanaryMode.STUDIO)).isNull()
  }

  @Test
  fun testAddOptions() {
    val config = LeakCanaryConfiguration("MyConfig")
    val builder = Trace.TraceConfiguration.newBuilder()

    config.addOptions(builder, emptyMap())

    assertThat(builder.hasLeakcanaryOptions()).isTrue()
    assertThat(builder.leakcanaryOptions.mode).isEqualTo(Trace.LeakCanaryOptions.LeakCanaryMode.ON_HOST)
  }
}
