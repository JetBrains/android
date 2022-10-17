/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.upgrade

import com.android.ide.common.repository.AgpVersion
import com.android.testutils.ignore.IgnoreTestRule
import com.android.tools.idea.gradle.plugin.LatestKnownPluginVersionProvider
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Expect
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.PsiFile
import com.intellij.testFramework.RunsInEdt
import com.intellij.usages.UsageViewManager
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class AgpUpgradeRefactoringProcessorShowUsagesTest {
  @get:Rule
  val projectRule = AndroidProjectRule.withSdk().onEdt()

  @get:Rule
  val ignoreTests = IgnoreTestRule()

  @get:Rule
  val expect = Expect.createAndEnableStackTrace()

  val project by lazy { projectRule.project }

  val currentAgpVersion = AgpVersion.parse("4.1.0")
  val latestKnown by lazy { AgpVersion.parse(LatestKnownPluginVersionProvider.INSTANCE.get()) }

  @Test
  fun testShowUsageViewNoBuildFiles() {
    val processor = AgpUpgradeRefactoringProcessor(project, currentAgpVersion, latestKnown)
    val usages = processor.doFindUsages()
    processor.doPreviewRefactoring(usages)
    val usageView = UsageViewManager.getInstance(project).selectedUsageView!!
    assertThat(usageView.presentation.tabText).isEqualTo("Upgrade Usages")
    assertThat(usageView.presentation.targetsNodeText).isEqualTo("AGP Upgrade Assistant")
    assertThat(usageView.usagesCount).isEqualTo(0)
  }

  @Test
  fun testShowUsageViewMinimalBuildFile() {
    addMinimalBuildGradleToProject()
    val processor = AgpUpgradeRefactoringProcessor(project, currentAgpVersion, latestKnown)
    val usages = processor.doFindUsages()
    processor.doPreviewRefactoring(usages)
    val usageView = UsageViewManager.getInstance(project).selectedUsageView!!
    assertThat(usageView.presentation.tabText).isEqualTo("Upgrade Usages")
    assertThat(usageView.presentation.targetsNodeText).isEqualTo("AGP Upgrade Assistant")
    assertThat(usageView.usagesCount).isEqualTo(1)
  }

  private fun addMinimalBuildGradleToProject() : PsiFile {
    return projectRule.fixture.addFileToProject(
      "build.gradle",
      """
        buildscript {
          dependencies {
            classpath 'com.android.tools.build:gradle:$currentAgpVersion'
          }
        }
      """.trimIndent()
    )
  }
}