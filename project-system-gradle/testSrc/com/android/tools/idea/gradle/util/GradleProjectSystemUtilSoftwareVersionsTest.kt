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
package com.android.tools.idea.gradle.util

import com.android.testutils.TestUtils.KOTLIN_VERSION_FOR_TESTS
import com.android.tools.idea.gradle.project.sync.snapshots.PreparedTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TemplateBasedTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.replaceContent
import com.android.tools.idea.testing.AndroidGradleTests
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files

class GradleProjectSystemUtilSoftwareVersionsTest {
  @get:Rule
  val projectRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @Test
  fun testCurrentKotlin() {
    val preparedProject = projectRule.prepareTestProject(TestProject.KOTLIN_KAPT)
    preparedProject
      .open { project ->
        val kotlinVersionInUse = GradleProjectSystemUtil.getKotlinVersionInUse(project, project.basePath!!)
        assertThat(kotlinVersionInUse).isNotNull()
        assertThat(kotlinVersionInUse).isEqualTo(KOTLIN_VERSION_FOR_TESTS)
      }
  }

  @Test
  fun testOlderKotlin() {
    val preparedProject = projectRule.prepareTestProject(TestProject.KOTLIN_KAPT)
    val buildGradle = preparedProject.root.resolve("build.gradle")
    buildGradle.replaceContent { contents ->
      AndroidGradleTests.replaceRegexGroup(contents, "ext.kotlin_version ?= ?['\"](.+)['\"]", "1.6.21")
    }
    preparedProject
      .open { project ->
        val kotlinVersionInUse = GradleProjectSystemUtil.getKotlinVersionInUse(project, project.basePath!!)
        assertThat(kotlinVersionInUse).isNotNull()
        assertThat(kotlinVersionInUse).isEqualTo("1.6.21")
      }

  }
}