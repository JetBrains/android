/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.npw

import com.android.sdklib.AndroidVersion.VersionCodes.KITKAT_WATCH
import com.android.sdklib.AndroidVersion.VersionCodes.LOLLIPOP
import com.android.sdklib.repository.targets.SystemImage
import org.junit.Test

import com.android.tools.adtui.device.FormFactor.AUTOMOTIVE
import com.android.tools.adtui.device.FormFactor.MOBILE
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.awt.geom.Arc2D.PIE

class FormFactorTest {
  @Test
  fun mobileSupportedOnLollipopApi() {
    assertTrue(MOBILE.isSupported(SystemImage.DEFAULT_TAG, LOLLIPOP))
  }

  @Test
  fun mobileNotSupportedOnAllowList() {
    assertFalse(MOBILE.isSupported(SystemImage.WEAR_TAG, LOLLIPOP))
    assertFalse(MOBILE.isSupported(null, LOLLIPOP))
  }

  @Test
  fun mobileNotSupportedOnWatchApi() {
    // Tests that mobile is on the block-list for the watch API
    assertFalse(MOBILE.isSupported(SystemImage.DEFAULT_TAG, KITKAT_WATCH))
  }

  @Test
  fun automotiveSupportedOnPieApi() {
    assertTrue(AUTOMOTIVE.isSupported(SystemImage.AUTOMOTIVE_TAG, PIE))
  }

  @Test
  fun automotiveNotSupportedOnAllowList() {
    assertFalse(AUTOMOTIVE.isSupported(SystemImage.WEAR_TAG, PIE))
    assertFalse(AUTOMOTIVE.isSupported(null, LOLLIPOP))
  }
}
