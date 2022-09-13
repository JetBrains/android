/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables

import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.gradle.structure.configurables.ui.TestTree
import com.android.tools.idea.gradle.structure.configurables.ui.testStructure
import com.android.tools.idea.gradle.structure.model.android.psTestWithContext
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.EdtAndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class ModulesPerspectiveConfigurableTest  {

  @get:Rule
  val projectRule: EdtAndroidProjectRule = AndroidProjectRule.withAndroidModels().onEdt()

  @Test
  fun testModulesTreeWithBasicSingleModuleProject() {
    val testStructure = getModulesTreeStructureFromConfigurableForProject(AndroidCoreTestProject.BASIC)
    // Note: indentation matters!
    val expectedStructure = """
      root
          project
      """.trimIndent()
    // Note: If fails see a nice diff by clicking <Click to see difference> in the IDEA output window.
    Truth.assertThat(testStructure.toString()).isEqualTo(expectedStructure)
  }

  @Test
  fun testModulesTreeWhenPluginInTheRootWithSubmodules() {
    val testStructure = getModulesTreeStructureFromConfigurableForProject(AndroidCoreTestProject.NESTED_MODULE)
    // Note: indentation matters!
    val expectedStructure = """
      root
          project
              app
      """.trimIndent()
    // Note: If fails see a nice diff by clicking <Click to see difference> in the IDEA output window.
    Truth.assertThat(testStructure.toString()).isEqualTo(expectedStructure)
  }

  @Test
  fun testModulesTreeWhenPluginInSubmodulesOnly() {
    val testStructure = getModulesTreeStructureFromConfigurableForProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    // Note: indentation matters!
    val expectedStructure = """
      root
          app
          dyn_feature
          jav
          lib
          nested1
              deep
          nested2
              deep
              trans
                  deep2
      """.trimIndent()
    // Note: If fails see a nice diff by clicking <Click to see difference> in the IDEA output window.
    Truth.assertThat(testStructure.toString()).isEqualTo(expectedStructure)
  }

  private fun getModulesTreeStructureFromConfigurableForProject(testProject: TestProjectDefinition): TestTree {
    val preparedProject = projectRule.prepareTestProject(testProject)
    return projectRule.psTestWithContext(preparedProject, disableAnalysis = true, resolveModels = false) {

      val configurable = ModulesPerspectiveConfigurable(context).also {
        Disposer.register(context, it)
        it.reset()
      }

      (configurable.tree.model as ConfigurablesTreeModel).rootNode.testStructure()
    }
  }
}