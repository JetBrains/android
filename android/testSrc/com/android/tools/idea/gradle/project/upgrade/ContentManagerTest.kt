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
package com.android.tools.idea.gradle.project.upgrade

import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.plugin.LatestKnownPluginVersionProvider
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.MANDATORY_CODEPENDENT
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.CheckedTreeNode
import org.apache.commons.io.FileSystem
import org.jetbrains.kotlin.idea.util.application.runWriteAction
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class ContentManagerTest {
  val currentAgpVersion by lazy { GradleVersion.parse("4.1.0") }
  val latestAgpVersion by lazy { GradleVersion.parse(LatestKnownPluginVersionProvider.INSTANCE.get()) }

  @get:Rule
  val projectRule = AndroidProjectRule.withSdk().onEdt()

  val project by lazy { projectRule.project }

  @Test
  fun testContentManagerConstructable() {
    val contentManager = ContentManager(project)
  }

  @Test
  fun testToolWindowModelConstructable() {
    val toolWindowModel = ToolWindowModel(project, currentAgpVersion)
  }

  @Test
  fun testToolWindowModelStartsWithLatestAgpVersionSelected() {
    val toolWindowModel = ToolWindowModel(project, currentAgpVersion)
    assertThat(toolWindowModel.selectedVersion.valueOrNull).isEqualTo(latestAgpVersion)
  }

  @Test
  fun testToolWindowModelStartsWithValidProcessor() {
    val toolWindowModel = ToolWindowModel(project, currentAgpVersion)
    assertThat(toolWindowModel.processor?.current).isEqualTo(currentAgpVersion)
    assertThat(toolWindowModel.processor?.new).isEqualTo(latestAgpVersion)
  }

  @Test
  fun testToolWindowModelStartsEnabled() {
    val toolWindowModel = ToolWindowModel(project, currentAgpVersion)
    assertThat(toolWindowModel.runEnabled.get()).isTrue()
    assertThat(toolWindowModel.runDisabledTooltip.get()).isEmpty()
  }

  @Test
  fun testToolWindowModelIsNotLoading() {
    val toolWindowModel = ToolWindowModel(project, currentAgpVersion)
    assertThat(toolWindowModel.showLoadingState.get()).isFalse()
  }

  @Test
  fun testToolWindowModelIsNotEnabledForNullSelectedVersion() {
    val toolWindowModel = ToolWindowModel(project, currentAgpVersion)
    toolWindowModel.selectedVersion.clear()
    assertThat(toolWindowModel.runEnabled.get()).isFalse()
  }

  @Test
  fun testToolWindowModelIsNotLoadingForNullSelectedVersion() {
    val toolWindowModel = ToolWindowModel(project, currentAgpVersion)
    toolWindowModel.selectedVersion.clear()
    assertThat(toolWindowModel.showLoadingState.get()).isFalse()
  }

  @Test
  fun testTreeModelInitialState() {
    projectRule.fixture.addFileToProject(
      "build.gradle",
      """
        buildscript {
          dependencies {
            classpath 'com.android.tools.build:gradle:$currentAgpVersion'
          }
        }
      """.trimIndent()
    )
    val toolWindowModel = ToolWindowModel(project, currentAgpVersion)
    val treeModel = toolWindowModel.treeModel
    val root = treeModel.root as? CheckedTreeNode
    assertThat(root).isInstanceOf(CheckedTreeNode::class.java)
    assertThat(root!!.childCount).isEqualTo(1)
    val mandatoryCodependent = root.firstChild as CheckedTreeNode
    assertThat(mandatoryCodependent.userObject).isEqualTo(MANDATORY_CODEPENDENT)
    assertThat(mandatoryCodependent.isEnabled).isTrue()
    assertThat(mandatoryCodependent.isChecked).isTrue()
    assertThat(mandatoryCodependent.childCount).isEqualTo(1)
    val step = mandatoryCodependent.firstChild as CheckedTreeNode
    assertThat(step.isEnabled).isFalse()
    assertThat(step.isChecked).isTrue()
    val stepPresentation = step.userObject as ToolWindowModel.DefaultStepPresentation
    assertThat(stepPresentation.processor).isInstanceOf(AgpClasspathDependencyRefactoringProcessor::class.java)
    assertThat(stepPresentation.treeText).contains("Upgrade AGP dependency from $currentAgpVersion to $latestAgpVersion")
  }
}