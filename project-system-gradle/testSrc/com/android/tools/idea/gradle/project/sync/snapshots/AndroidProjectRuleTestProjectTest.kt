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
package com.android.tools.idea.gradle.project.sync.snapshots

import com.android.tools.idea.gradle.model.IdeModuleWellKnownSourceSet
import com.android.tools.idea.projectsystem.getAndroidTestModule
import com.android.tools.idea.projectsystem.gradle.GradleSourceSetProjectPath
import com.android.tools.idea.projectsystem.gradle.getGradleProjectPath
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.gradleModule
import com.google.common.truth.Truth.assertThat
import com.intellij.util.PathUtil
import org.junit.Rule
import org.junit.Test

class AndroidProjectRuleTestProjectTest {
  @get:Rule
  val projectRule = AndroidProjectRule.testProject(AndroidCoreTestProject.SIMPLE_APPLICATION)

  @Test
  fun testSync() {
    assertThat(projectRule.module.getGradleProjectPath())
      .isEqualTo(
        GradleSourceSetProjectPath(
          PathUtil.toSystemIndependentName(projectRule.testHelpers.projectRoot.path),
          ":app",
          sourceSet = IdeModuleWellKnownSourceSet.MAIN
        )
      )
    val appAndroidTestModule = projectRule.project.gradleModule(":app")!!.getAndroidTestModule()!!
  }

  @Test
  fun testSync2() {
    assertThat(projectRule.module.getGradleProjectPath())
      .isEqualTo(
        GradleSourceSetProjectPath(
          PathUtil.toSystemIndependentName(projectRule.testHelpers.projectRoot.path),
          ":app",
          sourceSet = IdeModuleWellKnownSourceSet.MAIN
        )
      )
    val appAndroidTestModule = projectRule.project.gradleModule(":app")!!.getAndroidTestModule()!!
    projectRule.testHelpers.selectModule(appAndroidTestModule)
    assertThat(projectRule.module).isEqualTo(appAndroidTestModule)
  }
}