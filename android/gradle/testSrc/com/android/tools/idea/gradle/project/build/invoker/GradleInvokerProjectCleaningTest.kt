/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.build.invoker

import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class GradleInvokerProjectCleaningTest {

  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @Test
  fun testCompositeBuildClean() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.COMPOSITE_BUILD)
    preparedProject.open { project ->
      val result = GradleBuildInvoker.getInstance(project).cleanProject()
      assertThat(result.get().tasks).containsExactly(
        ":app:clean",
        ":lib:clean",
        ":TestCompositeLib1:clean",
        ":TestCompositeLib2:clean",
        ":TestCompositeLib3:clean",
        ":TestCompositeLib4:clean",
      )
    }
  }


  @Test
  fun testBasicProjectClean() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.BASIC)
    preparedProject.open { project ->
      val result = GradleBuildInvoker.getInstance(project).cleanProject()
      assertThat(result.get().tasks).containsExactly(
        "clean",
      )
    }
  }

}
