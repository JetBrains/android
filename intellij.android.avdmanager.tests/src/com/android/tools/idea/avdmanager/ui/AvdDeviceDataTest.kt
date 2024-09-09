/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.avdmanager.ui

import com.android.resources.Density
import com.android.tools.idea.avdmanager.DeviceManagerConnection
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

class AvdDeviceDataTest {
  @get:Rule val androidProjectRule = AndroidProjectRule.withSdk()

  @Test
  fun densityNewDevice() {
    val data = AvdDeviceData()

    assertThat(data.density().get()).isEqualTo(Density.XXHIGH)

    data.diagonalScreenSize().set(6.0)

    assertThat(data.density().get()).isEqualTo(Density.XHIGH)
  }

  @Test
  fun densityExistingDevice() {
    val pixel5 =
      DeviceManagerConnection.getDefaultDeviceManagerConnection().getDevice("pixel_5", "Google")
    val data = AvdDeviceData(pixel5, null)

    // Most devices don't have densities that match the predefined buckets well, and use numeric
    // density.
    assertThat(data.density().get()).isEqualTo(Density(440))

    // We coerce the density to a bucket when we compute it. (We could perhaps use more buckets.)
    data.diagonalScreenSize().set(6.1)
    assertThat(data.density().get()).isEqualTo(Density.XXHIGH)
    data.diagonalScreenSize().set(5.9)
    assertThat(data.density().get()).isEqualTo(Density.XXHIGH)

    // When the inputs go back to the original values, we restore the original density.
    data.diagonalScreenSize().set(6.0)
    assertThat(data.density().get()).isEqualTo(Density(440))
  }
}
