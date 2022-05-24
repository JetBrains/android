/*
 * Copyright (C) 2021 The Android Open Source Project
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
package org.jetbrains.android.actions

import com.android.tools.idea.project.AndroidProjectInfo
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.replaceService
import org.mockito.Mockito


/**
 * Tests for [GenerateSignedApkAction]
 */
class GenerateSignedApkActionTest: AndroidGradleTestCase() {
  fun testNullProjectBundleDisabled() {
    assertThat(GenerateSignedApkAction.allowBundleSigning(null)).isFalse()
  }

  fun testApkProjectBundleDisabled() {
    loadSimpleApplication()
    val mockInfo = Mockito.mock(AndroidProjectInfo::class.java)
    Mockito.`when`(mockInfo.isApkProject).thenReturn(true)
    project.replaceService(AndroidProjectInfo::class.java, mockInfo, testRootDisposable)
    assertThat(GenerateSignedApkAction.allowBundleSigning(project)).isFalse()
  }

  fun testSimpleProjectBundleEnabled() {
    loadSimpleApplication()
    assertThat(GenerateSignedApkAction.allowBundleSigning(project)).isTrue()
  }
}