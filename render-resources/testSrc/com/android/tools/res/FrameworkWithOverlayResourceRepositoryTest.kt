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
package com.android.tools.res

import com.android.ide.common.rendering.api.ResourceNamespace.ANDROID
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.ide.common.resources.getConfiguredResources
import com.android.resources.ResourceType.DIMEN
import com.android.resources.ResourceType.STRING
import com.android.resources.aar.FrameworkResourceRepository
import com.android.testutils.TestUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FrameworkWithOverlayResourceRepositoryTest {

  @Test
  fun testOverlayPriority() {
    val frameworkResJar = TestUtils.resolveWorkspacePath("prebuilts/studio/layoutlib/data/framework_res.jar")
    val folderConfig = FolderConfiguration.createDefault()
    val baseRepo = FrameworkResourceRepository.create(frameworkResJar, null, null, true)
    val overlay1 = FrameworkResourceRepository.createForOverlay(frameworkResJar, FrameworkOverlay.NAV_GESTURE.overlayName, null, null, true)
    val overlay2 = FrameworkResourceRepository.createForOverlay(frameworkResJar, FrameworkOverlay.NAV_3_BUTTONS.overlayName, null, null, true)
    val basePlusOverlay1 = FrameworkWithOverlaysResourceRepository(baseRepo, listOf(overlay1))
    val basePlusBothOverlays = FrameworkWithOverlaysResourceRepository(baseRepo, listOf(overlay2, overlay1))

    run {
      val baseValue = baseRepo.getConfiguredResources(ANDROID, DIMEN, folderConfig)["navigation_bar_height"]
      val overlay1Value = overlay1.getConfiguredResources(ANDROID, DIMEN, folderConfig)["navigation_bar_height"]
      val basePlusOverlay1Value = basePlusOverlay1.getConfiguredResources(ANDROID, DIMEN, folderConfig)["navigation_bar_height"]

      assertThat(overlay1Value).isNotEqualTo(baseValue)
      assertThat(basePlusOverlay1Value).isEqualTo(overlay1Value)
    }

    run {
      val baseValue = baseRepo.getConfiguredResources(ANDROID, STRING, folderConfig)["navigation_bar_mode_title"]
      val overlay1Value = overlay1.getConfiguredResources(ANDROID, STRING, folderConfig)["navigation_bar_mode_title"]
      val overlay2Value = overlay2.getConfiguredResources(ANDROID, STRING, folderConfig)["navigation_bar_mode_title"]
      val basePlusOverlay1Value = basePlusOverlay1.getConfiguredResources(ANDROID, STRING, folderConfig)["navigation_bar_mode_title"]
      val basePlusBothOverlaysValue = basePlusBothOverlays.getConfiguredResources(ANDROID, STRING, folderConfig)["navigation_bar_mode_title"]

      assertThat(overlay1Value).isNotEqualTo(baseValue)
      assertThat(overlay2Value).isNotEqualTo(baseValue)
      assertThat(basePlusOverlay1Value).isEqualTo(overlay1Value)
      assertThat(basePlusBothOverlaysValue).isNotEqualTo(overlay1Value)
      assertThat(basePlusBothOverlaysValue).isEqualTo(overlay2Value)
    }
  }
}