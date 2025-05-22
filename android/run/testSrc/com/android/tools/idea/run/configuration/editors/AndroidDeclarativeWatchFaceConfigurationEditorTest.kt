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
package com.android.tools.idea.run.configuration.editors

import com.android.tools.adtui.TreeWalker
import com.android.tools.idea.gradle.model.IdeAndroidProjectType.PROJECT_TYPE_DYNAMIC_FEATURE
import com.android.tools.idea.gradle.model.IdeAndroidProjectType.PROJECT_TYPE_LIBRARY
import com.android.tools.idea.gradle.model.IdeAndroidProjectType.PROJECT_TYPE_TEST
import com.android.tools.idea.projectsystem.gradle.getHolderModule
import com.android.tools.idea.run.AndroidRunConfigurationModule
import com.android.tools.idea.run.configuration.AndroidDeclarativeWatchFaceConfiguration
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.JavaModuleModelBuilder.Companion.rootModuleBuilder
import com.android.tools.idea.testing.gradleModule
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.application.options.ModulesComboBox
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.RunsInEdt
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunsInEdt
class AndroidDeclarativeWatchFaceConfigurationEditorTest {

  @get:Rule
  val projectRule =
    AndroidProjectRule.withAndroidModels(
        rootModuleBuilder,
        AndroidModuleModelBuilder(
          ":app",
          "debug",
          AndroidProjectBuilder(dynamicFeatures = { listOf(":feature") }),
        ),
        AndroidModuleModelBuilder(
          ":feature",
          "debug",
          AndroidProjectBuilder(projectType = { PROJECT_TYPE_DYNAMIC_FEATURE }),
        ),
        AndroidModuleModelBuilder(
          ":lib",
          "debug",
          AndroidProjectBuilder(projectType = { PROJECT_TYPE_LIBRARY }),
        ),
        AndroidModuleModelBuilder(
          ":test_only",
          "debug",
          AndroidProjectBuilder(projectType = { PROJECT_TYPE_TEST }),
        ),
      )
      .onEdt()

  private lateinit var editor: AndroidDeclarativeWatchFaceConfigurationEditor

  private val AndroidDeclarativeWatchFaceConfigurationEditor.modulesComboBox
    get() = TreeWalker(component).descendants().filterIsInstance<ModulesComboBox>().first()

  @Before
  fun setup() {
    editor = AndroidDeclarativeWatchFaceConfigurationEditor(projectRule.project)
    Disposer.register(projectRule.testRootDisposable, editor)
  }

  @Test
  fun `only app modules are available for selection`() {
    val availableModules =
      ModuleManager.getInstance(projectRule.project).modules.filter {
        editor.moduleSelector.isModuleAccepted(it)
      }
    assertThat(availableModules).containsExactly(module(":app").getHolderModule())
  }

  @Test
  fun `reset editor from configuration`() {
    val module = module(":app").getHolderModule()
    val configuration = mock<AndroidDeclarativeWatchFaceConfiguration>()
    val configurationModule = mock<AndroidRunConfigurationModule>()
    whenever(configuration.configurationModule).thenReturn(configurationModule)
    whenever(configurationModule.module).thenReturn(module)

    editor.modulesComboBox.selectedModule = null
    editor.resetFrom(configuration)

    assertThat(editor.modulesComboBox.selectedModule).isEqualTo(module)
  }

  @Test
  fun `apply editor to configuration`() =
    runBlocking<Unit> {
      val module = module(":app").getHolderModule()
      val configuration = mock<AndroidDeclarativeWatchFaceConfiguration>()

      editor.modulesComboBox.selectedModule = module
      editor.applyTo(configuration)

      verify(configuration).setModule(module)
    }

  private fun module(moduleGradlePath: String): Module {
    return projectRule.project.gradleModule(moduleGradlePath)
      ?: error("Holder module for $moduleGradlePath not found")
  }
}
