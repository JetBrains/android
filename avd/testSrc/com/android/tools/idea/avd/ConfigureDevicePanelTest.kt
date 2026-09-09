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
package com.android.tools.idea.avd

import com.android.sdklib.AndroidVersion
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ConfigureDevicePanelTest {
  @Test
  fun relevantVersions_empty() {
    assertThat(emptyList<AndroidVersion>().relevantVersions()).isEmpty()
  }

  @Test
  fun relevantVersions_stableVersions_sortedDescending() {
    val versions = listOf(AndroidVersion(31, 0), AndroidVersion(34, 0), AndroidVersion(33, 0))

    val relevant = versions.relevantVersions()

    assertThat(relevant)
      .containsExactly(
        AndroidVersion(34, 0).withBaseExtensionLevel(),
        AndroidVersion(33, 0).withBaseExtensionLevel(),
        AndroidVersion(31, 0).withBaseExtensionLevel(),
      )
      .inOrder()
  }

  @Test
  fun relevantVersions_stripsExtensionLevelsAndDeduplicates() {
    val versions =
      listOf(
        AndroidVersion(33, 0).withExtensionLevel(4),
        AndroidVersion(33, 0).withExtensionLevel(5),
        AndroidVersion(33, 0).withBaseExtensionLevel(),
      )

    val relevant = versions.relevantVersions()

    assertThat(relevant).containsExactly(AndroidVersion(33, 0).withBaseExtensionLevel())
  }

  @Test
  fun relevantVersions_previewNewerThanLatestStable_included() {
    val preview = AndroidVersion(35, "VanillaIceCream")
    val stable34 = AndroidVersion(34, 0)
    val stable33 = AndroidVersion(33, 0)
    val versions = listOf(stable33, preview, stable34)

    val relevant = versions.relevantVersions()

    assertThat(relevant)
      .containsExactly(
        preview.withBaseExtensionLevel(),
        stable34.withBaseExtensionLevel(),
        stable33.withBaseExtensionLevel(),
      )
      .inOrder()
  }

  @Test
  fun relevantVersions_previewOlderThanLatestStable_excluded() {
    val oldPreview = AndroidVersion(33, "TiramisuPrivacySandbox")
    val stable34 = AndroidVersion(34, 0)
    val stable33 = AndroidVersion(33, 0)
    val versions = listOf(stable34, oldPreview, stable33)

    val relevant = versions.relevantVersions()

    assertThat(relevant)
      .containsExactly(
        stable34.withBaseExtensionLevel(),
        stable33.withBaseExtensionLevel(),
      )
      .inOrder()
  }

  @Test
  fun relevantVersions_multipleOldCanaryVersions_onlyLatestIncluded() {
    val stable36 = AndroidVersion(36, 0)
    val canary1 = AndroidVersion(35, 0).withCanaryNumber(20250101)
    val canary2 = AndroidVersion(35, 0).withCanaryNumber(20250201)
    val versions = listOf(stable36, canary1, canary2)

    val relevant = versions.relevantVersions()

    assertThat(relevant)
      .containsExactly(
        stable36.withBaseExtensionLevel(),
        canary2.withBaseExtensionLevel(),
      )
      .inOrder()
  }

  @Test
  fun relevantVersions_onlyPreviews() {
    val preview1 = AndroidVersion(34, "PreviewOne")
    val preview2 = AndroidVersion(35, "PreviewTwo")
    val versions = listOf(preview1, preview2)

    val relevant = versions.relevantVersions()

    assertThat(relevant)
      .containsExactly(
        preview2.withBaseExtensionLevel(),
        preview1.withBaseExtensionLevel(),
      )
      .inOrder()
  }
}
