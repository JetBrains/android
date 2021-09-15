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
package com.android.tools.idea.testartifacts.gradle

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testartifacts.createAndroidGradleConfigurationFromDirectory
import com.android.tools.idea.testartifacts.createAndroidGradleConfigurationFromFile
import com.android.tools.idea.testartifacts.createAndroidGradleTestConfigurationFromClass

import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths.TEST_ARTIFACTS_KOTLIN
import com.android.tools.idea.testing.TestProjectPaths.UNIT_TESTING
import com.google.common.truth.Truth
import junit.framework.TestCase

/**
 * Tests for producing Gradle Run Configuration for Android unit test.
 */
class AndroidGradleConfigurationProducersTest : AndroidGradleTestCase() {
  override fun setUp() {
    super.setUp()
    StudioFlags.GRADLE_UNIT_TESTING.override(true)
  }

  @Throws(Exception::class)
  override fun tearDown() {
    super.tearDown()
    StudioFlags.GRADLE_UNIT_TESTING.clearOverride()
  }

  @Throws(Exception::class)
  fun testCanCreateGradleConfigurationFromTestClass() {
    loadSimpleApplication()
    TestCase.assertNotNull(createAndroidGradleTestConfigurationFromClass(project, "google.simpleapplication.UnitTest"))
  }

  @Throws(Exception::class)
  fun testCannotCreateGradleConfigurationFromTestClass() {
    loadSimpleApplication()
    TestCase.assertNull(createAndroidGradleTestConfigurationFromClass(project, "google.simpleapplication.ApplicationTest"))
  }

  @Throws(Exception::class)
  fun testCanCreateGradleConfigurationFromTestDirectory() {
    loadSimpleApplication()
    TestCase.assertNotNull(createAndroidGradleConfigurationFromDirectory(project, "app/src/test/java"))
  }

  @Throws(Exception::class)
  fun testCannotCreateGradleConfigurationFromTestDirectory() {
    loadSimpleApplication()
    TestCase.assertNull(createAndroidGradleConfigurationFromDirectory(project, "app/src/androidTest/java"))
  }

  @Throws(Exception::class)
  fun testCanCreateGradleConfigurationFromTestDirectoryKotlin() {
    loadProject(TEST_ARTIFACTS_KOTLIN)
    TestCase.assertNotNull(createAndroidGradleConfigurationFromDirectory(
      project, "app/src/test/java"))
  }

  @Throws(Exception::class)
  fun testCannotCreateGradleConfigurationFromTestDirectoryKotlin() {
    loadProject(TEST_ARTIFACTS_KOTLIN)
    TestCase.assertNull(createAndroidGradleConfigurationFromDirectory(
      project, "app/src/androidTest/java"))
  }

  @Throws(Exception::class)
  fun testCannotCreateGradleConfigurationFromTestClassKotlin() {
    loadProject(TEST_ARTIFACTS_KOTLIN)
    TestCase.assertNull(createAndroidGradleConfigurationFromFile(
      project, "app/src/androidTest/java/com/example/android/kotlin/ExampleInstrumentedTest.kt"))
  }

  @Throws(Exception::class)
  fun testAndroidGradleTestTasksProviderDoesntCreateJavaModulesTestTasks() {
    loadProject(UNIT_TESTING)
    // Here we test to verify that the configuration tasks aren't provided by the AndroidGradleTestTasksProvider.
    // We do that by verifying the tasks value.
    val gradleJavaConfiguration = createAndroidGradleTestConfigurationFromClass(
      project, "com.example.javalib.JavaLibJavaTest")
    TestCase.assertNotNull(gradleJavaConfiguration)
    Truth.assertThat(gradleJavaConfiguration!!.commandLine.toString())
      .isEqualTo(""":javalib:test --tests "com.example.javalib.JavaLibJavaTest"""")
  }
}