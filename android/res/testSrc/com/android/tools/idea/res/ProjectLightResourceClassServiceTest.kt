/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.res

import com.android.tools.idea.projectsystem.PROJECT_SYSTEM_BUILD_TOPIC
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.AndroidProjectRule.Companion.inMemory
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ProjectLightResourceClassServiceTest {
  @get:Rule val rule: AndroidProjectRule = inMemory()

  private fun getProjectRClasses(): List<PsiClass> = runReadAction {
    ProjectLightResourceClassService.getInstance(rule.project)
      .getLightRClassesDefinedByModule(rule.module)
      .toList()
  }

  @Test
  fun buildInvalidatesRClass() {
    val rClasses = getProjectRClasses()

    assertThat(rClasses).hasSize(2)
    assertThat(rClasses[0].name).isEqualTo("R")
    assertThat(rClasses[1].name).isEqualTo("R")

    assertWithMessage("Before a build, the same R classes should be returned")
      .that(getProjectRClasses()).containsExactlyElementsIn(rClasses)

    rule.project.messageBus
      .syncPublisher(PROJECT_SYSTEM_BUILD_TOPIC)
      .buildCompleted(
        ProjectSystemBuildManager.BuildResult(
          ProjectSystemBuildManager.BuildMode.COMPILE,
          ProjectSystemBuildManager.BuildStatus.SUCCESS,
          0
        )
      )

    val newRClasses = getProjectRClasses()
    assertWithMessage("After a build, R classes should re-generate")
      .that(newRClasses).containsNoneIn(rClasses)
    assertThat(newRClasses).hasSize(2)
  }
}
