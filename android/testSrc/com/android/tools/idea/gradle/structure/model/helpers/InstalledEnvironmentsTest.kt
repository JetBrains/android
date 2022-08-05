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
import com.android.sdklib.IAndroidTarget
import com.android.sdklib.internal.androidTarget.MockAddonTarget
import com.android.sdklib.internal.androidTarget.MockPlatformTarget
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.gradle.structure.model.meta.ValueDescriptor
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations.initMocks

class InstalledEnvironmentsTest {

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

  private lateinit var targets: Collection<IAndroidTarget>

  @Before
  fun setUp() {
    initMocks(this)
    whenever(sdkManager.packages)
        .thenReturn(
            RepositoryPackages(
                listOf(localPackage1, localPackage2, localPackage3, localPackage4),
                listOf()))
    targets = listOf(target1, target2, target3, target4)
  }

  @Test
  fun installedEnvironment_androidSdks() {
    assertThat(
        installedEnvironments(sdkManager, targets).androidSdks,
        equalTo(
            listOf(
                ValueDescriptor(24, "API 24: Android 7.0 (Nougat)"),
                ValueDescriptor(26, "API 26: Android 8.0 (Oreo)"),
                ValueDescriptor(27, "API 27: Android 8.1 (Oreo)")
            )))

  }

  @Test
  fun installedEnvironment_buildTools() {
    assertThat(
        installedEnvironments(sdkManager, targets).buildTools,
        equalTo(
            listOf(
                ValueDescriptor("24.0.3", null),
                ValueDescriptor("27.0.0", null),
                ValueDescriptor("4109860.0.0", null)
            )))

  }

  @Test
  fun installedEnvironment_compiledApis() {
    assertThat(
        installedEnvironments(sdkManager, targets).compiledApis,
        equalTo(
            listOf(
                ValueDescriptor("24", "API 24: Android 7.0 (Nougat)"),
                ValueDescriptor("vendor 24:ADDON:24", "ADDON (API 24)"),
                ValueDescriptor("26", "API 26: Android 8.0 (Oreo)"),
                ValueDescriptor("27", "API 27: Android 8.1 (Oreo)")
            )))

  }
}