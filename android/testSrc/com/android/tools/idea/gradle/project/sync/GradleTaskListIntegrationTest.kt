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
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.findAppModule
import com.google.common.truth.Truth
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import junit.framework.TestCase
import org.jetbrains.plugins.gradle.util.GradleUtil

/**
 * Tests to verify the functionality of disabling gradle task list during Gradle Sync.
 */
class GradleTaskListIntegrationTest : AndroidGradleTestCase() {
  private var myOriginalTaskListSetting: Boolean = false

  override fun setUp() {
    super.setUp()
    myOriginalTaskListSetting = GradleExperimentalSettings.getInstance().SKIP_GRADLE_TASKS_LIST
  }

  override fun tearDown() {
    try {
      super.tearDown()
    }
    finally {
      GradleExperimentalSettings.getInstance().SKIP_GRADLE_TASKS_LIST = myOriginalTaskListSetting
    }
  }

  fun testSyncWithGradleTaskListSkipped() {
    GradleExperimentalSettings.getInstance().SKIP_GRADLE_TASKS_LIST = true
    loadSimpleApplication()

    val moduleData = GradleUtil.findGradleModuleData(project.findAppModule())
    TestCase.assertNotNull(moduleData)

    // Verify that no TaskData DataNode is being created.
    val taskNodeData = ExternalSystemApiUtil.findAll(moduleData!!, ProjectKeys.TASK)
    Truth.assertThat(taskNodeData).isEmpty()
  }

  fun testSyncWithGradleTaskListNotSkipped() {
    GradleExperimentalSettings.getInstance().SKIP_GRADLE_TASKS_LIST = false
    loadSimpleApplication()

    val moduleData = GradleUtil.findGradleModuleData(project.findAppModule())
    TestCase.assertNotNull(moduleData)

    // Verify that TaskData DataNode is not empty.
    val taskNodeData = ExternalSystemApiUtil.findAll(moduleData!!, ProjectKeys.TASK)
    Truth.assertThat(taskNodeData).isNotEmpty()
  }
}