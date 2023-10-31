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
package com.android.tools.idea.testartifacts.scopes

import com.android.tools.idea.testartifacts.TestConfigurationTesting
import com.android.tools.idea.testartifacts.createAndroidGradleConfigurationFromDirectory
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.project.Project

/**
 * Tests for verifying that there is no conflict creating an instrumented test after a Gradle unit test.
 */
class AndroidTestAndGradleConfigurationConflictsTest : AndroidGradleTestCase() {
  // See: http://b/173106394
  @Throws(Exception::class)
  fun testCanCreateInstrumentedTestConfiguration() {
    loadSimpleApplication()
    assertThat(createAndroidGradleConfigurationFromDirectory(project, "app/src/test/java/google/simpleapplication")).isNotNull()

    // Verify that that no configuration is created from the context of AndroidTest artifact.
    // This follows the workflow on AS when trying create a AndroidTest configuration, where we first check if any existing configuration
    // was created from the AndroidTest context, and return it.
    // The Gradle Configuration producer fails in detecting that the created unit test configuration isn't from the context of AndroidTest
    // because it doesn't check for the unique PSI location.
    var androidTestRunConfiguration = findExistingAndroidTestConfigurationFromDirectory(
      project, "app/src/androidTest/java/google/simpleapplication")
    assertThat(androidTestRunConfiguration).isNull()

    // Verify that we can successfully create an AndroidTest run configuration.
    androidTestRunConfiguration = TestConfigurationTesting.createAndroidTestConfigurationFromDirectory(
      project, "app/src/androidTest/java/google/simpleapplication")
    assertThat(androidTestRunConfiguration).isNotNull()
  }

  private fun findExistingAndroidTestConfigurationFromDirectory(project: Project, directory: String): RunConfiguration? {
    val element = TestConfigurationTesting.getPsiElement(project, directory, true)
    val context = TestConfigurationTesting.createContext(project, element)
    // Search for any existing run configuration that was created from this context.
    val existing = context.findExisting() ?: return null
    return existing.configuration
  }
}