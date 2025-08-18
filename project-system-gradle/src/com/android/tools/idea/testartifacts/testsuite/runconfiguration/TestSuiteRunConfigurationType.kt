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

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NotNullLazyValue
import icons.StudioIcons
import org.jetbrains.android.util.AndroidBundle

class TestSuiteRunConfigurationType :
  ConfigurationTypeBase(
    ID,
    AndroidBundle.message("test.suite.run.configuration.type.name"),
    AndroidBundle.message("test.suite.run.configuration.type.description"),
    // TODO(b/445374798): Add custom test suite icon
    NotNullLazyValue.createValue { StudioIcons.Shell.Filetree.ANDROID_TEST_ROOT },
  ),
  DumbAware {

  companion object {
    const val ID = "TestSuiteRunConfigurationType"
  }

  init {
    addFactory(
      object : ConfigurationFactory(this) {
        override fun getId() = "TestSuiteRunConfigurationFactory"

        override fun createTemplateConfiguration(project: Project) =
          TestSuiteRunConfiguration(project, this, "")
      }
    )
  }
}
