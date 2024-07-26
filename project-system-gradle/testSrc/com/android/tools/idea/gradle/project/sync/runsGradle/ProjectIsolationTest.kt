/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.runsGradle

import com.android.SdkConstants
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.tools.idea.testing.OpenPreparedProjectOptions
import com.android.tools.idea.testing.openPreparedProject
import com.google.common.truth.Expect
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test

class ProjectIsolationTest {

  @get:Rule
  val expect: Expect = Expect.createAndEnableStackTrace()

  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @Test
  fun testProjectIsolationIssues() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)
    preparedProject.root.resolve(SdkConstants.FN_GRADLE_PROPERTIES).let {
      it.appendText("\norg.gradle.unsafe.isolated-projects=true")
    }

    val stdout = StringBuilder()
    projectRule.openPreparedProject(
      "project",
      OpenPreparedProjectOptions(
        outputHandler = {message ->
          stdout.append(message)
        },
      )
    ) {
      stdout.toString().let {
        Truth.assertThat(it).contains("""
          problems were found storing the configuration cache, 2 of which seem unique.
          """.trimIndent())
        Truth.assertThat(it).doesNotContain("""
          Project ':' cannot access 'Project.apply' functionality on subprojects via 'allprojects'
          """.trimIndent())
        Truth.assertThat(it).doesNotContain("""
          Plugin class 'com.android.ide.gradle.model.builder.AndroidStudioToolingPlugin': Project ':app' cannot dynamically look up a property
        """.trimIndent())
      }
    }
  }
}