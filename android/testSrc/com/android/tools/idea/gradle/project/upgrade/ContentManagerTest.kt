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
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.LightPlatformTestCase

class ContentManagerTest : LightPlatformTestCase() {
  val currentAgpVersion by lazy { GradleVersion.parse("4.1.0") }
  val latestAgpVersion  by lazy { GradleVersion.parse(LatestKnownPluginVersionProvider.INSTANCE.get()) }

  fun testContentManagerConstructable() {
    val contentManager = ContentManager(project)
  }

  fun testToolWindowModelConstructable() {
    val toolWindowModel = ToolWindowModel(project, currentAgpVersion)
  }

  fun testToolWindowModelStartsWithLatestAgpVersionSelected() {
    val toolWindowModel = ToolWindowModel(project, currentAgpVersion)
    assertThat(toolWindowModel.selectedVersion.valueOrNull).isEqualTo(latestAgpVersion)
  }

  fun testToolWindowModelStartsWithValidProcessor() {
    val toolWindowModel = ToolWindowModel(project, currentAgpVersion)
    assertThat(toolWindowModel.processor?.current).isEqualTo(currentAgpVersion)
    assertThat(toolWindowModel.processor?.new).isEqualTo(latestAgpVersion)
  }

  fun testToolWindowModelStartsEnabled() {
    val toolWindowModel = ToolWindowModel(project, currentAgpVersion)
    assertThat(toolWindowModel.runEnabled.get()).isTrue()
    assertThat(toolWindowModel.runDisabledTooltip.get()).isEmpty()
  }

  fun testToolWindowModelIsNotLoading() {
    val toolWindowModel = ToolWindowModel(project, currentAgpVersion)
    assertThat(toolWindowModel.showLoadingState.get()).isFalse()
  }

  fun testToolWindowModelIsNotEnabledForNullSelectedVersion() {
    val toolWindowModel = ToolWindowModel(project, currentAgpVersion)
    toolWindowModel.selectedVersion.clear()
    assertThat(toolWindowModel.runEnabled.get()).isFalse()
  }

  fun testToolWindowModelIsNotLoadingForNullSelectedVersion() {
    val toolWindowModel = ToolWindowModel(project, currentAgpVersion)
    toolWindowModel.selectedVersion.clear()
    assertThat(toolWindowModel.showLoadingState.get()).isFalse()
  }
}