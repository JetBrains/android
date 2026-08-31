/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.dependencies

import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.gradle.structure.model.PsModule
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.android.psTestWithProject
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.CheckboxTree
import com.intellij.ui.CheckedTreeNode
import javax.swing.JLabel
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class ModuleDependenciesFormTest {

  @get:Rule val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @Test
  fun testAvailableModulesExcludesSelfAndExistingDependencies() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_DEPENDENCY)
    projectRule.psTestWithProject(preparedProject) {
      val appModule = project.findModuleByName("app") as PsAndroidModule
      val form = ModuleDependenciesForm(appModule)

      assertThat(form.panel).isNotNull()
      assertThat(form.preferredFocusedComponent).isNotNull()
      assertThat(form.selectedModules).isEmpty()

      val tree = form.preferredFocusedComponent as CheckboxTree
      val root = tree.model.root as CheckedTreeNode
      val availableModules = (0 until root.childCount).map { (root.getChildAt(it) as CheckedTreeNode).userObject as PsModule }

      val availableNames = availableModules.map { it.name }
      // Available modules must filter out 'app' (self) and 'mainModule' (already a module dependency of 'app')
      assertThat(availableNames).containsNoneOf("app", "mainModule")
      assertThat(availableNames).isNotEmpty()
    }
  }

  @Test
  fun testModuleSelectionAndDeselectionUpdatesFormState() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_DEPENDENCY)
    projectRule.psTestWithProject(preparedProject) {
      val appModule = project.findModuleByName("app") as PsAndroidModule
      val form = ModuleDependenciesForm(appModule)

      val tree = form.preferredFocusedComponent as CheckboxTree
      val root = tree.model.root as CheckedTreeNode
      assertThat(root.childCount).isAtLeast(2)

      val firstChild = root.getChildAt(0) as CheckedTreeNode
      val selectedModule1 = firstChild.userObject as PsModule

      // 1. Verify checking node updates selectedModules and label text
      tree.setNodeState(firstChild, true)
      assertThat(form.selectedModules).containsExactly(selectedModule1)
      val modulesLabel = form.modulesLabel as JLabel
      assertThat(modulesLabel.text).contains(selectedModule1.name)

      // 2. Verify multiple selections update state appropriately
      val secondChild = root.getChildAt(1) as CheckedTreeNode
      val selectedModule2 = secondChild.userObject as PsModule
      tree.setNodeState(secondChild, true)
      assertThat(form.selectedModules).containsExactly(selectedModule1, selectedModule2)
      assertThat(modulesLabel.text).contains(selectedModule1.name)
      assertThat(modulesLabel.text).contains(selectedModule2.name)
      tree.setNodeState(secondChild, false)

      // 3. Verify unchecking node clears selectedModules and resets label text
      tree.setNodeState(firstChild, false)
      assertThat(form.selectedModules).isEmpty()
      assertThat(modulesLabel.text.isBlank()).isTrue()
    }
  }
}
