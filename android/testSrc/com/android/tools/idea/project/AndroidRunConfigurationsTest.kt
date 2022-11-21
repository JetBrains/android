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
package com.android.tools.idea.project

import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.run.AndroidRunConfigurationType
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.requestSyncAndWait
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.RunManager
import org.junit.Rule
import org.junit.Test

class AndroidRunConfigurationsTest {

  @get:Rule
  val projectRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @Test
  fun `set default activity launch for simple app`() {
    val preparedProject = projectRule.prepareTestProject(testProject = AndroidCoreTestProject.SIMPLE_APPLICATION)
    preparedProject.open { project ->
      project.requestSyncAndWait()
      val configurationFactory = AndroidRunConfigurationType.getInstance().factory

      val configurations = RunManager.getInstance(project).getConfigurationsList(configurationFactory.type)

      assertThat(configurations).hasSize(1)
      assertThat(configurations[0]).isInstanceOf(AndroidRunConfiguration::class.java)
      assertThat((configurations[0] as AndroidRunConfiguration).MODE).isEqualTo(AndroidRunConfiguration.LAUNCH_DEFAULT_ACTIVITY)
    }
  }

  @Test
  fun `set default activity launch for app with activity declared not in the main module`() {
    val preparedProject = projectRule.prepareTestProject(testProject = AndroidCoreTestProject.APP_WITH_ACTIVITY_IN_LIB)
    preparedProject.open { project ->
      project.requestSyncAndWait()
      val configurationFactory = AndroidRunConfigurationType.getInstance().factory

      val configurations = RunManager.getInstance(project).getConfigurationsList(configurationFactory.type)

      assertThat(configurations).hasSize(1)
      assertThat(configurations[0]).isInstanceOf(AndroidRunConfiguration::class.java)
      assertThat((configurations[0] as AndroidRunConfiguration).MODE).isEqualTo(AndroidRunConfiguration.LAUNCH_DEFAULT_ACTIVITY)
    }
  }

}