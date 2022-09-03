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
package com.android.tools.idea.gradle.project.sync.errors

import com.android.SdkConstants
import com.android.ide.common.repository.GradleVersion
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.gradle.plugin.LatestKnownPluginVersionProvider
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.GradleSyncListener
import com.android.tools.idea.gradle.project.sync.quickFixes.UpgradeGradleVersionsQuickFix
import com.android.tools.idea.gradle.project.upgrade.AndroidPluginVersionUpdater
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.IdeComponents
import com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.testFramework.TestDataProvider
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.any
import org.mockito.Mockito.eq
import org.mockito.Mockito.verify

@org.junit.Ignore("b/244804255")
class UpgradeGradleVersionsQuickFixTest {
  @JvmField
  @Rule
  val gradleProjectRule = AndroidGradleProjectRule()

  @Test
  fun `run with AndroidPluginVersionUpdater success`() {
    verifyUpdaterRun(success = true)
  }

  @Test
  fun `run with AndroidPluginVersionUpdater no success`() {
    verifyUpdaterRun(success = false)
  }

  @Test
  fun `run with AgpUpgradeRefactoringProcessor success`() {
    verifyProcessorRun(success = true)
  }

  @Test
  fun `run with AgpUpgradeRefactoringProcessor no success`() {
    verifyProcessorRun(success = false)
  }

  private fun verifyUpdaterRun(success: Boolean) {
    val project = gradleProjectRule.project
    val latestGradleVersion = GradleVersion.parse(SdkConstants.GRADLE_LATEST_VERSION)
    val latestAgpVersion = GradleVersion.parse(LatestKnownPluginVersionProvider.INSTANCE.get())
    val quickFix = UpgradeGradleVersionsQuickFix(latestGradleVersion, latestAgpVersion, "latest")
    val ideComponents = IdeComponents(project)
    val mockedUpdater = ideComponents.mockProjectService(AndroidPluginVersionUpdater::class.java);
    var receivedRequest: GradleSyncInvoker.Request? = null
    val fakeSyncInvoker = object: GradleSyncInvoker.FakeInvoker() {
      override fun requestProjectSync(project: Project, request: GradleSyncInvoker.Request, listener: GradleSyncListener?) {
        super.requestProjectSync(project, request, listener)
        assertThat(receivedRequest).isNull()
        receivedRequest = request
      }
    }
    ideComponents.replaceApplicationService(GradleSyncInvoker::class.java, fakeSyncInvoker)
    whenever(mockedUpdater.updatePluginVersion(any(), any())).thenReturn(success)
    val result = quickFix.runQuickFix(project, TestDataProvider(project) as DataContext).get()
    assertThat(result).isEqualTo(success)
    verify(mockedUpdater).updatePluginVersion(eq(latestAgpVersion), eq(latestGradleVersion))
    if (success) {
      assertThat(receivedRequest).isNotNull()
    }
    else {
      assertThat(receivedRequest).isNull()
    }
  }

  private fun verifyProcessorRun(success: Boolean) {
    gradleProjectRule.loadProject(SIMPLE_APPLICATION)
    val project = gradleProjectRule.project
    val latestGradleVersion = GradleVersion.parse(SdkConstants.GRADLE_LATEST_VERSION)
    val latestAgpVersion = GradleVersion.parse(LatestKnownPluginVersionProvider.INSTANCE.get())
    val quickFix = UpgradeGradleVersionsQuickFix(latestGradleVersion, latestAgpVersion, "latest")
    quickFix.showDialogResult(success)
    val ideComponents = IdeComponents(project)
    val mockSyncInvoker = ideComponents.mockApplicationService(GradleSyncInvoker::class.java)
    val result = quickFix.runQuickFix(project, TestDataProvider(project) as DataContext).get()
    assertThat(result).isEqualTo(success)
    Mockito.verifyZeroInteractions(mockSyncInvoker)
  }
}