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
package com.android.tools.idea.run.editor

import com.android.tools.idea.gradle.model.IdeAndroidProjectType.PROJECT_TYPE_DYNAMIC_FEATURE
import com.android.tools.idea.gradle.model.IdeAndroidProjectType.PROJECT_TYPE_LIBRARY
import com.android.tools.idea.gradle.model.IdeAndroidProjectType.PROJECT_TYPE_TEST
import com.android.tools.idea.projectsystem.getAndroidTestModule
import com.android.tools.idea.projectsystem.getMainModule
import com.android.tools.idea.run.AndroidRunConfigurationType
import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfigurationType
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.EdtAndroidProjectRule
import com.android.tools.idea.testing.JavaModuleModelBuilder.Companion.rootModuleBuilder
import com.android.tools.idea.testing.gradleModule
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Expect
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class AndroidRunConfigurationEditorTest {
  @get:Rule
  val projectRule: EdtAndroidProjectRule = AndroidProjectRule
    .withAndroidModels(
      rootModuleBuilder,
      AndroidModuleModelBuilder(":app", "debug", AndroidProjectBuilder(dynamicFeatures = { listOf(":feature") })),
      AndroidModuleModelBuilder(":feature", "debug", AndroidProjectBuilder(projectType = { PROJECT_TYPE_DYNAMIC_FEATURE })),
      AndroidModuleModelBuilder(":lib", "debug", AndroidProjectBuilder(projectType = { PROJECT_TYPE_LIBRARY })),
      AndroidModuleModelBuilder(":test_only", "debug", AndroidProjectBuilder(projectType = { PROJECT_TYPE_TEST })),
    )
    .onEdt()

  @get:Rule
  val expect: Expect = Expect.createAndEnableStackTrace()

  @Test
  fun `android run configuration`() {
    val runConfiguration = createConfiguration(AndroidRunConfigurationType.getInstance())
    val availableModules = getAvailableModules(runConfiguration)
    assertThat(availableModules)
      .containsExactly(
        module(":app").getMainModule(),
        module(":feature").getMainModule(),
      )
  }

  @Test
  fun `android test run configuration`() {
    val runConfiguration = createConfiguration(AndroidTestRunConfigurationType.getInstance())
    val availableModules = getAvailableModules(runConfiguration)
    assertThat(availableModules)
      .containsExactly(
        module(":app").getAndroidTestModule(),
        module(":lib").getAndroidTestModule(),
        module(":feature").getAndroidTestModule(),
        module(":test_only").getMainModule(),
      )
  }

  private fun createConfiguration(configurationType: ConfigurationType): RunConfiguration {
    val configurationFactory = configurationType.configurationFactories.single()
    return configurationFactory.createConfiguration(null, configurationFactory.createTemplateConfiguration(projectRule.project))
  }

  private fun module(moduleGradlePath: String): Module {
    return projectRule.project.gradleModule(moduleGradlePath) ?: error("Holder module for $moduleGradlePath not found")
  }

  private fun getAvailableModules(runConfiguration: RunConfiguration): List<Module> {
    val editor = runConfiguration.configurationEditor as AndroidRunConfigurationEditor<*>
    return ModuleManager.getInstance(projectRule.project).modules.filter { editor.moduleSelector.isModuleAccepted(it) }
  }
}