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
package com.android.tools.idea.run.util

import com.android.tools.idea.projectsystem.NamedIdeaSourceProvider
import com.android.tools.idea.projectsystem.SourceProviders
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.DumbService
import com.intellij.testFramework.replaceService
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

class LaunchUtilsDumbModeTest {
  @get:Rule val projectRule = AndroidProjectRule.onDisk()

  private val facet
    get() = AndroidFacet.getInstance(projectRule.module)!!

  @Test
  fun testIsWatchFeatureRequiredInDumbMode() {
    setupManifests(
      """<manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <uses-feature android:name="android.hardware.type.watch" android:required="true" />
            </manifest>"""
    )
    setupDumbMode()
    assertThat(checkWatchFeatureRequired()).isTrue()
  }

  @Test
  fun testIsWatchFeatureRequiredInDumbMode_NotRequired() {
    setupManifests(
      """<manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <uses-feature android:name="android.hardware.camera" android:required="true" />
            </manifest>"""
    )
    setupDumbMode()
    assertThat(checkWatchFeatureRequired()).isFalse()
  }

  /*
  FOCUS
   */
  @Test
  fun testIsWatchFeatureRequiredInDumbMode_RequiredFalse() {
    setupManifests(
      """<manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <uses-feature android:name="android.hardware.type.watch" android:required="false" />
            </manifest>"""
    )
    setupDumbMode()
    assertThat(checkWatchFeatureRequired()).isFalse()
  }

  @Test
  fun testIsWatchFeatureRequiredInDumbMode_MultipleManifests() {
    setupManifests(
      """<manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <uses-feature android:name="android.hardware.camera" android:required="true" />
            </manifest>""",
      """<manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <uses-feature android:name="android.hardware.type.watch" android:required="true" />
            </manifest>""",
    )
    setupDumbMode()
    assertThat(checkWatchFeatureRequired()).isTrue()
  }

  private fun setupDumbMode() {
    val mockDumbService = mock<DumbService>()
    whenever(mockDumbService.isDumb).thenReturn(true)
    projectRule.project.replaceService(DumbService::class.java, mockDumbService, projectRule.testRootDisposable)
  }

  private fun setupManifests(vararg contents: String) {
    val manifestFiles =
      contents.mapIndexed { index, content ->
        projectRule.fixture.addFileToProject("src/manifest_$index/AndroidManifest.xml", content.trimIndent()).virtualFile
      }
    val sourceProvider = mock<NamedIdeaSourceProvider>()
    whenever(sourceProvider.manifestFiles).thenReturn(manifestFiles)
    SourceProviders.replaceForTest(facet, projectRule.testRootDisposable, sourceProvider)
  }

  private fun checkWatchFeatureRequired(): Boolean {
    return ReadAction.nonBlocking<Boolean> { LaunchUtils.isWatchFeatureRequired(facet) }
      .submit(AppExecutorUtil.getAppExecutorService())
      .get()
  }
}
