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

import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.findAppModule

class GradleClassFileFinderTest : AndroidGradleTestCase() {
  /**
   * Regression test for b/206060369
   */
  fun testClassFileFinder() {
    loadProject(TestProjectPaths.UNIT_TESTING)
    GradleProjectSystemBuildManager(project).compileProject()

    val classFinder = GradleClassFileFinder(project.findAppModule())
    assertNotNull(classFinder.findClassFile("com.example.app.AppJavaClass"))
    assertNotNull(classFinder.findClassFile("com.example.app.AppKotlinClass"))
  }

  fun testClassFileFinder_androidTest() {
    loadProject(TestProjectPaths.SIMPLE_APPLICATION)
    GradleProjectSystemBuildManager(project).compileProject()

    val classFinder = GradleClassFileFinder(project.findAppModule(), true)

    // Fqcns present in the main module or in android test files should be found
    assertNotNull(classFinder.findClassFile("google.simpleapplication.ApplicationTest"))
    assertNotNull(classFinder.findClassFile("google.simpleapplication.MyActivity"))
  }

  fun testClassFileFinder_nonAndroidTest() {
    loadProject(TestProjectPaths.SIMPLE_APPLICATION)
    GradleProjectSystemBuildManager(project).compileProject()

    val classFinder = GradleClassFileFinder(project.findAppModule())

    // Only fqcns present in the main module should be found
    assertNull(classFinder.findClassFile("google.simpleapplication.ApplicationTest"))
    assertNotNull(classFinder.findClassFile("google.simpleapplication.MyActivity"))
  }
}