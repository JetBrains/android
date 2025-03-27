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
package com.android.tools.idea.testartifacts.testsuite

import com.android.tools.idea.execution.common.DeployableToDevice
import com.android.tools.idea.flags.StudioFlags.ENABLE_ADDITIONAL_TESTING_GRADLE_OPTIONS
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration
import com.intellij.openapi.externalSystem.service.execution.configuration.ExternalSystemReifiedRunConfigurationExtension
import com.intellij.openapi.externalSystem.service.execution.configuration.fragments.SettingsEditorFragmentContainer
import com.intellij.openapi.externalSystem.service.execution.configuration.fragments.addTag
import com.intellij.openapi.util.Key
import org.jdom.Element
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration

/**
 * Provides additional options to GradleRunConfiguration editor for showing test results in Android Test Suite view.
 */
class GradleRunConfigurationExtension :
  ExternalSystemReifiedRunConfigurationExtension<GradleRunConfiguration>(GradleRunConfiguration::class.java) {

  enum class BooleanOptions(
    val tagId: String,
    val tagName: String,
    val tagHint: String,
    val userDataKey: Key<Boolean>,
  ) {
    SHOW_TEST_RESULT_IN_ANDROID_TEST_SUITE_VIEW(
      tagId = "com.android.tools.idea.testartifacts.testsuite.showTestResultInAndroidTestSuiteView",
      tagName = "Show results in Android Test Suite",
      tagHint = "Displays test results in Android Test Suite View",
      userDataKey = Key.create("com.android.tools.idea.testartifacts.testsuite.SHOW_TEST_RESULT_IN_ANDROID_TEST_SUITE_VIEW"),
    ),
    USE_ANDROID_DEVICE(
      tagId = "com.android.tools.idea.testartifacts.testsuite.useAndroidDevice",
      tagName = "Use Android Device",
      tagHint = "Use Android Device to run this Gradle task",
      userDataKey = DeployableToDevice.KEY
    );
  }

  override fun isApplicableFor(configuration: ExternalSystemRunConfiguration): Boolean {
    return ENABLE_ADDITIONAL_TESTING_GRADLE_OPTIONS.get()
  }

  override fun isEnabledFor(applicableConfiguration: ExternalSystemRunConfiguration, runnerSettings: RunnerSettings?): Boolean {
    return ENABLE_ADDITIONAL_TESTING_GRADLE_OPTIONS.get()
  }

  override fun SettingsEditorFragmentContainer<GradleRunConfiguration>.configureFragments(
    configuration: GradleRunConfiguration) {
    BooleanOptions.entries.forEach {
      addTag(
        id = it.tagId,
        name = it.tagName,
        group = "Android Studio",
        hint = it.tagHint,
        getter = { getUserData<Boolean>(it.userDataKey) == true },
        setter = { newValue -> putUserData<Boolean>(it.userDataKey, newValue) },
      )
    }
  }

  override fun writeExternal(runConfiguration: ExternalSystemRunConfiguration, element: Element) {
    BooleanOptions.entries.forEach {
      element.addContent(Element(it.userDataKey.toString()).apply {
        setText(
          (runConfiguration.getUserData<Boolean>(it.userDataKey) == true).toString()
        )
      })
    }
  }

  override fun readExternal(runConfiguration: ExternalSystemRunConfiguration, element: Element) {
    BooleanOptions.entries.forEach {
      element.getChild(it.userDataKey.toString())?.text?.let { value ->
        runConfiguration.putUserData<Boolean>(it.userDataKey, value.toBoolean())
      }
    }
  }
}