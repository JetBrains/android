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
package com.android.tools.idea.projectsystem.gradle

import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.EdtAndroidProjectRule
import com.android.tools.idea.testing.findAppModule
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Expect
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class GradleClassFileFinderTest {

  @get:Rule
  val projectRule: EdtAndroidProjectRule = AndroidProjectRule.withAndroidModels().onEdt()

  @get:Rule
  val expect: Expect = Expect.createAndEnableStackTrace()

  /**
   * Regression test for b/206060369
   */
  @Test
  fun testClassFileFinder() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.UNIT_TESTING)
    preparedProject.open { project ->
      GradleProjectSystemBuildManager(project).compileProject()

      val classFinder = GradleClassFileFinder(project.findAppModule())
      expect.that(classFinder.findClassFile("com.example.app.AppJavaClass")).isNotNull()
      expect.that(classFinder.findClassFile("com.example.app.AppKotlinClass")).isNotNull()
    }
  }

  @Test
  fun testClassFileFinder_androidTest() {
    val preparedProject = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION)
    preparedProject.open { project ->
      GradleProjectSystemBuildManager(project).compileProject()

      val classFinder = GradleClassFileFinder(project.findAppModule(), true)

      // Fqcns present in the main module or in android test files should be found
      expect.that(classFinder.findClassFile("google.simpleapplication.ApplicationTest")).isNotNull()
      expect.that(classFinder.findClassFile("google.simpleapplication.MyActivity")).isNotNull()
    }
  }

  @Test
  fun testClassFileFinder_nonAndroidTest() {
    val preparedProject = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION)
    preparedProject.open { project ->
      GradleProjectSystemBuildManager(project).compileProject()

      val classFinder = GradleClassFileFinder(project.findAppModule())

      // Only fqcns present in the main module should be found
      expect.that(classFinder.findClassFile("google.simpleapplication.ApplicationTest")).isNull()
      expect.that(classFinder.findClassFile("google.simpleapplication.MyActivity")).isNotNull()
    }
  }
}