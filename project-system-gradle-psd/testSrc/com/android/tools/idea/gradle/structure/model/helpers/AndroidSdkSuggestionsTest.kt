/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.model.helpers

import com.android.repository.Revision
import com.android.repository.api.LocalPackage
import com.android.repository.api.RepoManager
import com.android.repository.impl.meta.RepositoryPackages
import com.android.repository.testframework.FakePackage
import com.android.sdklib.AndroidApiLevel
import com.android.sdklib.AndroidVersion
import com.android.sdklib.IAndroidTarget
import com.android.sdklib.internal.androidTarget.MockAddonTarget
import com.android.sdklib.internal.androidTarget.MockPlatformTarget
import com.android.tools.idea.gradle.structure.model.meta.ValueDescriptor
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations.initMocks
import org.mockito.kotlin.whenever

class AndroidSdkSuggestionsTest {

  @Mock private lateinit var sdkManager: RepoManager

  private val localPackage1: LocalPackage = FakePackage.FakeLocalPackage("build-tools;24.0.3").apply {
    setRevision(Revision.parseRevision("24.0.3"))
  }

  private val localPackage2: LocalPackage = FakePackage.FakeLocalPackage("build-tools;27.0.0").apply {
    setRevision(Revision.parseRevision("27.0.0"))
  }

  private val localPackage3: LocalPackage = FakePackage.FakeLocalPackage("build-tools;4109860.0.0").apply {
    setRevision(Revision.parseRevision("4109860.0.0"))
  }

  private val localPackage4: LocalPackage =
      FakePackage.FakeLocalPackage("extras;m2repository;com;android;support;constraint;constraint-layout;1.0.2").apply {
        setRevision(Revision.parseRevision("1.0.2"))
      }

  private val target1: IAndroidTarget = MockPlatformTarget(24, 0)
  private val target2: IAndroidTarget = MockAddonTarget("ADDON", target1, 0)
  private val target3: IAndroidTarget = MockPlatformTarget(26, 0)
  private val target4: IAndroidTarget = MockPlatformTarget(27, 0)
  private val target5: IAndroidTarget = MockPlatformTarget(AndroidVersion(AndroidApiLevel(35, 0), null,14, false), 0)
  private val target6: IAndroidTarget = MockPlatformTarget(AndroidVersion(35, "Baklava"), 0)
  private val target7: IAndroidTarget = MockPlatformTarget(36, 0)
  private val target8: IAndroidTarget = MockPlatformTarget(AndroidVersion(36, 1), 0)

  private lateinit var targets: Collection<IAndroidTarget>

  private val staticallyKnownAndroidVersions = setOf(AndroidVersion(34, 0), AndroidVersion(36, 0), AndroidVersion(36, 1))

  @Before
  fun setUp() {
    initMocks(this)
    whenever(sdkManager.packages)
        .thenReturn(
            RepositoryPackages(
                listOf(localPackage1, localPackage2, localPackage3, localPackage4),
                listOf()))
    targets = listOf(target1, target2, target3, target4, target5, target6, target7, target8)
  }

  @Test
  fun installedEnvironment_minSdks() {
    assertThat(
      androidSdkSuggestions(sdkManager, targets, staticallyKnownAndroidVersions).minSdks,
      equalTo(
            listOf(
                ValueDescriptor("24", "API 24 (\"Nougat\"; Android 7.0)"),
                ValueDescriptor("26", "API 26 (\"Oreo\"; Android 8.0)"),
                ValueDescriptor("27", "API 27 (\"Oreo\"; Android 8.1)"),
                ValueDescriptor("34", "API 34 (\"UpsideDownCake\"; Android 14.0)"),
                ValueDescriptor("android-Baklava", "API Baklava Preview"),
                ValueDescriptor("36", "API 36 (\"Baklava\"; Android 16.0)"),
            )))
  }

  @Test
  fun installedEnvironment_targetSdks() {
    assertThat(
      androidSdkSuggestions(sdkManager, targets, staticallyKnownAndroidVersions).targetSdks,
      equalTo(
        listOf(
          ValueDescriptor("24", "API 24 (\"Nougat\"; Android 7.0)"),
          ValueDescriptor("26", "API 26 (\"Oreo\"; Android 8.0)"),
          ValueDescriptor("27", "API 27 (\"Oreo\"; Android 8.1)"),
          ValueDescriptor("34", "API 34 (\"UpsideDownCake\"; Android 14.0)"),
          ValueDescriptor("android-Baklava", "API Baklava Preview"),
          ValueDescriptor("36", "API 36 (\"Baklava\"; Android 16.0)"),
        )))
  }

  @Test
  fun installedEnvironment_maxSdks() {
    assertThat(
      androidSdkSuggestions(sdkManager, targets, staticallyKnownAndroidVersions).maxSdks,
      equalTo(
        listOf(
          ValueDescriptor(24, "API 24 (\"Nougat\"; Android 7.0)"),
          ValueDescriptor(26, "API 26 (\"Oreo\"; Android 8.0)"),
          ValueDescriptor(27, "API 27 (\"Oreo\"; Android 8.1)"),
          ValueDescriptor(34, "API 34 (\"UpsideDownCake\"; Android 14.0)"),
          ValueDescriptor(36, "API 36 (\"Baklava\"; Android 16.0)"),
        )))
  }

  @Test
  fun installedEnvironment_buildTools() {
    assertThat(
      androidSdkSuggestions(sdkManager, targets, setOf()).buildTools,
      equalTo(
            listOf(
                ValueDescriptor("24.0.3", null),
                ValueDescriptor("27.0.0", null),
                ValueDescriptor("4109860.0.0", null)
            )))

  }

  @Test
  fun installedEnvironment_compileSdks() {
    assertThat(
      androidSdkSuggestions(sdkManager, targets, setOf()).compileSdks,
      equalTo(
            listOf(
                ValueDescriptor("24", "API 24 (\"Nougat\"; Android 7.0)"),
                ValueDescriptor("vendor 24:ADDON:24", "ADDON (API 24)"),
                ValueDescriptor("26", "API 26 (\"Oreo\"; Android 8.0)"),
                ValueDescriptor("27", "API 27 (\"Oreo\"; Android 8.1)"),
                ValueDescriptor("android-35-ext14", "API 35 ext. 14 (\"VanillaIceCream\"; Android 15.0)"),
                ValueDescriptor("android-Baklava", "API Baklava Preview"),
                ValueDescriptor("36", "API 36.0 (\"Baklava\"; Android 16.0)"),
                ValueDescriptor("android-36.1", "API 36.1 (\"Baklava\"; Android 16.0)"),
            )))

  }
}