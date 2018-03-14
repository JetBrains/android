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
package com.android.tools.datastore.energy

import com.android.tools.datastore.energy.PowerProfile.DefaultPowerProfile.*
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PowerProfileTest {
  @Test
  fun renormalizeTest() {
    assertThat(renormalizeFrequency(PowerProfile.CpuCoreUsage(1.0, 1.0, 500, 1000, 500), MAX_BIG_CORE_FREQ_KHZ)).isWithin(0.1).of(
        MIN_CORE_FREQ_KHZ.toDouble() * 0.001)
    assertThat(renormalizeFrequency(PowerProfile.CpuCoreUsage(1.0, 1.0, 500, 1000, 1000), MAX_BIG_CORE_FREQ_KHZ)).isWithin(0.1).of(
        MAX_BIG_CORE_FREQ_KHZ.toDouble() * 0.001)
    assertThat(renormalizeFrequency(PowerProfile.CpuCoreUsage(1.0, 1.0, 500, 1000, 750), MAX_BIG_CORE_FREQ_KHZ)).isWithin(0.1).of(
        (MAX_BIG_CORE_FREQ_KHZ + MIN_CORE_FREQ_KHZ).toDouble() * 0.0005)
  }
}