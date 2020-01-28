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

import com.android.tools.idea.gradle.structure.configurables.ui.TestTree
import com.android.tools.idea.gradle.structure.configurables.ui.testStructure
import com.android.tools.idea.gradle.structure.model.PsProject
import com.android.tools.idea.gradle.structure.model.PsProjectImpl
import com.android.tools.idea.gradle.structure.model.android.DependencyTestCase
import com.android.tools.idea.testing.TestProjectPaths
import com.google.common.truth.Truth
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer

class ModulesPerspectiveConfigurableTest : DependencyTestCase() {

  private lateinit var resolvedProject: Project
  private lateinit var project: PsProject
  private lateinit var context: PsContext

  fun testModulesTreeWithBasicSingleModuleProject() {
    val testStructure = getModulesTreeStructureFromConfigurableForProject(TestProjectPaths.BASIC)
    // Note: indentation matters!
    val expectedStructure = """
      root
          ${projectFolderPath.name}
      """.trimIndent()
    // Note: If fails see a nice diff by clicking <Click to see difference> in the IDEA output window.
    Truth.assertThat(testStructure.toString()).isEqualTo(expectedStructure)
  }

  fun testModulesTreeWhenPluginInTheRootWithSubmodules() {
    val testStructure = getModulesTreeStructureFromConfigurableForProject(TestProjectPaths.NESTED_MODULE)
    // Note: indentation matters!
    val expectedStructure = """
      root
          ${projectFolderPath.name}
              app
      """.trimIndent()
    // Note: If fails see a nice diff by clicking <Click to see difference> in the IDEA output window.
    Truth.assertThat(testStructure.toString()).isEqualTo(expectedStructure)
  }

  fun testModulesTreeWhenPluginInSubmodulesOnly() {
    val testStructure = getModulesTreeStructureFromConfigurableForProject(TestProjectPaths.PSD_SAMPLE_GROOVY)
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

  private fun reparse() {
    resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject)
    this.project = project
    context = PsContextImpl(project, testRootDisposable, disableAnalysis = true, disableResolveModels = true)
      .also { Disposer.register(testRootDisposable, it) }
  }

  private fun getModulesTreeStructureFromConfigurableForProject(relativePath: String): TestTree {
    loadProject(relativePath)
    reparse()

    val configurable = ModulesPerspectiveConfigurable(context).also {
      Disposer.register(context, it)
      it.reset()
    }

    return (configurable.tree.model as ConfigurablesTreeModel).rootNode.testStructure()
  }
}