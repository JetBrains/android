/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.android.buildvariants

import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.android.tools.idea.gradle.structure.configurables.PsContextImpl
import com.android.tools.idea.gradle.structure.configurables.android.buildvariants.productflavors.ProductFlavorsConfigurable
import com.android.tools.idea.gradle.structure.configurables.createTreeModel
import com.android.tools.idea.gradle.structure.configurables.ui.testStructure
import com.android.tools.idea.gradle.structure.model.PsProject
import com.android.tools.idea.gradle.structure.model.PsProjectImpl
import com.android.tools.idea.gradle.structure.model.android.DependencyTestCase
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.testing.TestProjectPaths
import com.google.common.truth.Truth
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer

class AndroidModuleBuildVariantsConfigurableTest : DependencyTestCase() {

  private lateinit var resolvedProject: Project
  private lateinit var project: PsProject
  private lateinit var context: PsContext

  override fun setUp() {
    super.setUp()
    loadProject(TestProjectPaths.PSD_DEPENDENCY)
    reparse()
  }

  private fun reparse() {
    resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject)
    this.project = project
    context = PsContextImpl(project, testRootDisposable, disableAnalysis = true, disableResolveModels = true)
      .also { Disposer.register(testRootDisposable, it) }
  }

  fun testProductFlavorsTreeStructure() {
    val module = project.findModuleByName("app") as PsAndroidModule
    module.addNewFlavorDimension("foo")
    module.addNewProductFlavor("foo", "foo1")
    module.addNewProductFlavor("foo", "foo2")
    module.addNewProductFlavor("bar", "bar1")
    val treeModel = createTreeModel(ProductFlavorsConfigurable(module, context).also { Disposer.register(context, it) })
    val node = treeModel.rootNode

    // Note: indentation matters!
    val expectedStructure = """
      Flavor Dimensions
          dim1
              paid
              free
          foo
              foo1
              foo2
          (invalid)
              bar1""".trimIndent()
    val treeStructure = node.testStructure()
    // Note: If fails see a nice diff by clicking <Click to see difference> in the IDEA output window.
    Truth.assertThat(treeStructure.toString()).isEqualTo(expectedStructure)
  }
}