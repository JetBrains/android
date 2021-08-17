/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.instrumented.testsuite.model

import com.android.sdklib.AndroidVersion
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDeviceType.LOCAL_PHYSICAL_DEVICE
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Unit tests for [AndroidDevice].
 */
@RunWith(JUnit4::class)
class AndroidDeviceTest {
  @Test
  fun getName() {
    // Use "id" as name if no other information is available.
    assertThat(AndroidDevice("id", "", "", LOCAL_PHYSICAL_DEVICE, AndroidVersion(28)).getName())
      .isEqualTo("id")
    // Use manufacturer and model name if available.
    assertThat(
      AndroidDevice("id", "", "", LOCAL_PHYSICAL_DEVICE, AndroidVersion(28),
                    mutableMapOf("Manufacturer" to "Google",
                                 "Model" to "Pixel 4")).getName()).isEqualTo("Google Pixel 4")
    // If device name is given by the constructor, always use it.
    assertThat(
      AndroidDevice("id", "device name", "", LOCAL_PHYSICAL_DEVICE, AndroidVersion(28),
                    mutableMapOf("Manufacturer" to "Google",
                                 "Model" to "Pixel 4")).getName()).isEqualTo("device name")
  }
}