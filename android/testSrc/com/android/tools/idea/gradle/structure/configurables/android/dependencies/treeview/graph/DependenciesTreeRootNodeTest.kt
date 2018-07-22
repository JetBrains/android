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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.graph

import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings
import com.android.tools.idea.gradle.structure.configurables.ui.testStructure
import com.android.tools.idea.gradle.structure.model.PsProject
import com.android.tools.idea.gradle.structure.model.PsProjectImpl
import com.android.tools.idea.gradle.structure.model.android.DependencyTestCase
import com.android.tools.idea.gradle.structure.model.android.testResolve
import com.android.tools.idea.testing.TestProjectPaths
import com.intellij.openapi.project.Project
import org.hamcrest.CoreMatchers
import org.junit.Assert

class DependenciesTreeRootNodeTest : DependencyTestCase() {
  private lateinit var resolvedProject: Project
  private lateinit var project: PsProject

  override fun setUp() {
    super.setUp()
    loadProject(TestProjectPaths.PSD_DEPENDENCY)
    reparse()
  }

  private fun reparse() {
    resolvedProject = myFixture.project
    project = PsProjectImpl(resolvedProject).also { it.testResolve() }
  }

  fun testTreeStructure() {
    val node = DependenciesTreeRootNode(project, PsUISettings())

    // Note: indentation matters!
    val expectedProjectStructure = """
      testTreeStructure
          android.arch.core:common:1.1.0
          android.arch.core:runtime:1.1.0
          android.arch.lifecycle:common:1.1.0
          android.arch.lifecycle:livedata-core:1.1.0
          android.arch.lifecycle:runtime:1.1.0
          android.arch.lifecycle:viewmodel:1.1.0
          com.android.support:animated-vector-drawable:27.1.1
          com.android.support:appcompat-v7
          support-annotations:→27.1.1
          com.android.support:support-compat:27.1.1
          com.android.support:support-core-ui:27.1.1
          com.android.support:support-core-utils:27.1.1
          com.android.support:support-fragment:27.1.1
          com.android.support:support-vector-drawable:27.1.1
          com.example.jlib:lib3
              lib3:0.6
              lib3:1.0
              lib3:0.9.1
          com.example.jlib:lib4
              lib4:1.0
              lib4:0.9.1
          com.example.libs:lib1
              lib1:1.0
              lib1:0.9.1
              lib1:0.+
          com.example.libs:lib2
              lib2:1.0
              lib2:0.9.1
          jModuleK
          jModuleL
          mainModule""".trimIndent()
    val treeStructure = node.testStructure({ !it.name.startsWith("appcompat-v7") })
    // Note: If fails see a nice diff by clicking <Click to see difference> in the IDEA output window.
    Assert.assertThat(treeStructure.toString(), CoreMatchers.equalTo(expectedProjectStructure))
  }
}