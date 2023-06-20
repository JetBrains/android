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
package com.android.tools.idea.compose.preview.runconfiguration

import com.android.AndroidProjectTypes
import com.android.tools.adtui.TreeWalker
import com.intellij.application.options.ModulesComboBox
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.TestFixtureBuilder
import com.intellij.ui.SortedComboBoxModel
import com.intellij.ui.components.JBTextField
import org.jetbrains.android.AndroidTestCase

class ComposePreviewSettingsEditorTest : AndroidTestCase() {

  private lateinit var runConfiguration: ComposePreviewRunConfiguration
  private lateinit var settingsEditor: ComposePreviewSettingsEditor

  override fun configureAdditionalModules(
    projectBuilder: TestFixtureBuilder<IdeaProjectTestFixture?>,
    modules: List<MyAdditionalModuleData?>
  ) {
    super.configureAdditionalModules(projectBuilder, modules)
    addModuleWithAndroidFacet(
      projectBuilder,
      modules,
      "library",
      AndroidProjectTypes.PROJECT_TYPE_LIBRARY,
      false
    )
  }

  override fun setUp() {
    super.setUp()

    val runConfigurationFactory = ComposePreviewRunConfigurationType().configurationFactories[0]
    runConfiguration = ComposePreviewRunConfiguration(project, runConfigurationFactory)
    settingsEditor = ComposePreviewSettingsEditor(project, runConfiguration)
  }

  fun testResetFrom() {
    runConfiguration.composableMethodFqn = "my.composable.NameKt"
    runConfiguration.setModule(myModule)
    settingsEditor.resetFrom(runConfiguration)

    val modulesComboBox =
      TreeWalker(settingsEditor.component).descendants().filterIsInstance<ModulesComboBox>().first()
    assertEquals(myModule, modulesComboBox.selectedModule)

    val composableText =
      TreeWalker(settingsEditor.component).descendants().filterIsInstance<JBTextField>().first()
    assertEquals("my.composable.NameKt", composableText.text)
  }

  // Regression test for b/266054909
  fun testAllowLibraryModules() {
    runConfiguration.composableMethodFqn = "my.composable.NameKt"
    runConfiguration.setModule(myModule)
    settingsEditor.resetFrom(runConfiguration)

    val modulesComboBox =
      TreeWalker(settingsEditor.component).descendants().filterIsInstance<ModulesComboBox>().first()
    assertTrue((modulesComboBox.model as SortedComboBoxModel).items.any { it?.name == "library" })
  }

  fun testApplyTo() {
    assertNull(runConfiguration.composableMethodFqn)
    assertEmpty(runConfiguration.modules)

    val modulesComboBox =
      TreeWalker(settingsEditor.component).descendants().filterIsInstance<ModulesComboBox>().first()
    modulesComboBox.selectedModule = myModule

    val composableText =
      TreeWalker(settingsEditor.component).descendants().filterIsInstance<JBTextField>().first()
    composableText.text = "my.composable.NameKt"

    settingsEditor.applyTo(runConfiguration)
    assertEquals(1, runConfiguration.modules.size)
    assertEquals(myModule, runConfiguration.modules[0])
    assertEquals("my.composable.NameKt", runConfiguration.composableMethodFqn)
  }
}
