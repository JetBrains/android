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
package com.android.tools.idea.testartifacts.testsuite.runconfiguration

import com.android.tools.idea.testartifacts.testsuite.GradleRunConfigurationExtension
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.runners.RunConfigurationWithSuppressedDefaultRunAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jdom.Element
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration

/**
 * A run configuration for running an AGP test suite.
 */
class TestSuiteRunConfiguration(project: Project, factory: ConfigurationFactory, name: String) :
  GradleRunConfiguration(project, factory, name), RunConfigurationWithSuppressedDefaultRunAction {

  init {
    isRunAsTest = true
  }

  private var testEngineIds: Set<String> = setOf()

  /**
   * Returns the test engine IDs used by this test suite.
   */
  fun getTestEngineIds(): Set<String> {
    return testEngineIds
  }

  /**
   * Sets the test engine IDs used by this test suite.
   */
  fun setTestEngineIds(testEngineIds: Set<String>) {
    this.testEngineIds = testEngineIds
  }

  /**
   * Adds a task name to the list of tasks executed by this run configuration.
   */
  fun addTaskName(taskName: String) {
    val encodedTaskName = if (taskName.contains(" ")) "\"$taskName\"" else taskName
    settings.taskNames = settings.taskNames + encodedTaskName
  }

  /**
   * Returns true if the given [taskName] is executed by this run configuration.
   */
  fun containsTask(taskName: String): Boolean {
    return settings.taskNames.contains(encodeTaskName(taskName))
  }

  /**
   * Returns the names of the Gradle tasks executed by this run configuration.
   */
  fun getTaskNames(): List<String> {
    return settings.taskNames
  }

  /**
   * Sets whether a test device should be started
   */
  fun setIsDeployableToDevice(isDeployableToDevice: Boolean) {
    putUserData<Boolean>(
      GradleRunConfigurationExtension.BooleanOptions.USE_ANDROID_DEVICE.userDataKey,
      isDeployableToDevice
    )
  }

  /**
   * Returns true if a test device should be started
   */
  fun isDeployableToDevice(): Boolean {
    return getUserData<Boolean>(GradleRunConfigurationExtension.BooleanOptions.USE_ANDROID_DEVICE.userDataKey) == true
  }

  /**
   * Sets whether to show the test results in the Android Test Suite view.
   */
  fun setShowsResultsInAndroidTestMatrix(showsResultsInAndroidTestMatrix: Boolean) {
    putUserData<Boolean>(
      GradleRunConfigurationExtension.BooleanOptions.SHOW_TEST_RESULT_IN_ANDROID_TEST_SUITE_VIEW
        .userDataKey,
      showsResultsInAndroidTestMatrix,
    )
  }

  /**
   * Returns true if the test results should be shown in the Android Test Suite view.
   */
  fun showsResultsInAndroidTestMatrix(): Boolean {
    return getUserData<Boolean>(GradleRunConfigurationExtension.BooleanOptions.SHOW_TEST_RESULT_IN_ANDROID_TEST_SUITE_VIEW.userDataKey) == true
  }

  fun getTestSuiteModule(): Module? = TestSuiteUtils.getTestSuiteModule(this)

  override fun writeExternal(element: Element) {
    super.writeExternal(element)

    element.addContent(Element(TEST_ENGINE_IDS_XML_CONTAINER_KEY).apply {
      for (testEngineId in testEngineIds) {
        val idElement = Element(TEST_ENGINE_IDS_XML_ENTRY_KEY)
        idElement.setAttribute("id", testEngineId)
        addContent(idElement)
      }
    })
  }

  override fun readExternal(element: Element) {
    super.readExternal(element)

    val testEngineIds = mutableSetOf<String>()
    element.getChild(TEST_ENGINE_IDS_XML_CONTAINER_KEY)?.getChildren(TEST_ENGINE_IDS_XML_ENTRY_KEY)?.forEach { element ->
      element.getAttributeValue("id")?.let { testEngineIds.add(it) }
    }
    this.testEngineIds = testEngineIds
  }

  private fun encodeTaskName(taskName: String): String {
    return if (taskName.contains(" ")) "\"$taskName\"" else taskName
  }

  companion object {
    private const val TEST_ENGINE_IDS_XML_CONTAINER_KEY = "testEngineIds"
    private const val TEST_ENGINE_IDS_XML_ENTRY_KEY = "testEngineId"
  }
}
