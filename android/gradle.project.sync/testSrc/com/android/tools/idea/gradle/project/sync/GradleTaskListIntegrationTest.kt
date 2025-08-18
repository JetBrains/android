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
package com.android.tools.idea.gradle.project.sync

import com.android.tools.idea.gradle.project.GradleExperimentalSettings
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION
import com.android.tools.idea.testing.findAppModule
import com.google.common.truth.Truth
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import org.jetbrains.plugins.gradle.util.GradleUtil
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Tests to verify the functionality of disabling gradle task list during Gradle Sync.
 */
class GradleTaskListIntegrationTest {
  @get:Rule
  val projectRule = AndroidGradleProjectRule()

  private var myOriginalTaskListSetting: Boolean = false

  @Before
  fun setup() {
    myOriginalTaskListSetting = GradleExperimentalSettings.getInstance().SKIP_GRADLE_TASKS_LIST
  }

  @After
  fun teardown() {
    GradleExperimentalSettings.getInstance().SKIP_GRADLE_TASKS_LIST = myOriginalTaskListSetting
  }

  @Test
  fun testSyncWithGradleTaskListSkipped() {
    GradleExperimentalSettings.getInstance().SKIP_GRADLE_TASKS_LIST = true
    projectRule.loadProject(SIMPLE_APPLICATION)

    val moduleData = GradleUtil.findGradleModuleData(projectRule.project.findAppModule())

    // Verify that only test tasks are being created.
    val taskNodeData = ExternalSystemApiUtil.findAll(moduleData!!, ProjectKeys.TASK)
    Truth.assertThat(taskNodeData).isNotEmpty()
    Truth.assertThat(taskNodeData.map { it.data.name }).isEqualTo(listOf("testDebugUnitTest"))
  }

  @Test
  fun testSyncWithGradleTaskListNotSkipped() {
    GradleExperimentalSettings.getInstance().SKIP_GRADLE_TASKS_LIST = false
    projectRule.loadProject(SIMPLE_APPLICATION)

    val moduleData = GradleUtil.findGradleModuleData(projectRule.project.findAppModule())

    // Verify that TaskData DataNode is not empty.
    val taskNodeData = ExternalSystemApiUtil.findAll(moduleData!!, ProjectKeys.TASK)
    Truth.assertThat(taskNodeData).isNotEmpty()
    Truth.assertThat(taskNodeData.size).isGreaterThan(2)
  }
}