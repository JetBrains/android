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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.project.treeview

import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings
import com.android.tools.idea.gradle.structure.configurables.ui.testStructure
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsNode
import com.android.tools.idea.gradle.structure.model.PsProject
import com.android.tools.idea.gradle.structure.model.PsProjectImpl
import com.android.tools.idea.gradle.structure.model.android.DependencyTestCase
import com.android.tools.idea.gradle.structure.model.android.PsAndroidDependency
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.android.testResolve
import com.android.tools.idea.testing.TestProjectPaths
import com.intellij.openapi.project.Project
import org.hamcrest.core.IsEqual.equalTo
import org.junit.Assert.assertThat
import java.util.function.Consumer

class TargetModulesTreeStructureTest: DependencyTestCase() {
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
    val targetModulesTreeStructure = TargetModulesTreeStructure(PsUISettings())
    val node = targetModulesTreeStructure.rootElement as AbstractPsNode

    targetModulesTreeStructure.displayTargetModules(findModelsForSelection("com.example.libs", "lib1"))

    // Note: indentation matters!
    var expectedProjectStructure = """
      (null)
          mainModule
              freeDebug
                  implementation
                  debugImplementation
              freeRelease
                  implementation
                  releaseImplementation
              paidDebug
                  implementation
                  debugImplementation
              paidRelease
                  implementation
                  releaseImplementation
              freeDebug (androidTest)
              paidDebug (androidTest)
              freeDebug (test)
              freeRelease (test)
              paidDebug (test)
              paidRelease (test)
          modulePlus
              debug
                  implementation
              release
                  implementation
              debug (androidTest)
              debug (test)
              release (test)""".trimIndent()
    var treeStructure = node.testStructure({ !it.name.startsWith("appcompat-v7") })
    // Note: If fails see a nice diff by clicking <Click to see difference> in the IDEA output window.
    assertThat(treeStructure.toString(), equalTo(expectedProjectStructure))

    targetModulesTreeStructure.displayTargetModules(findModelsForSelection("com.example.libs", "lib2"))

    // Note: indentation matters!
    // TODO(b/84996111): Tests artifact chains should also end with a declared dependency.
    expectedProjectStructure = """
      (null)
          mainModule
              freeDebug
                  lib1:1.0 (com.example.libs)
                      implementation
                      debugImplementation
              freeRelease
                  lib1:1.0 (com.example.libs)
                      implementation
                      releaseImplementation
              paidDebug
                  lib1:1.0 (com.example.libs)
                      implementation
                      debugImplementation
              paidRelease
                  lib1:1.0 (com.example.libs)
                      implementation
                      releaseImplementation
              freeDebug (androidTest)
                  lib1:1.0 (com.example.libs)
              paidDebug (androidTest)
                  lib1:1.0 (com.example.libs)
              freeDebug (test)
                  lib1:1.0 (com.example.libs)
              freeRelease (test)
                  lib1:1.0 (com.example.libs)
              paidDebug (test)
                  lib1:1.0 (com.example.libs)
              paidRelease (test)
                  lib1:1.0 (com.example.libs)
          modulePlus
              debug
                  lib1:0.9.1 (com.example.libs)
                      implementation
              release
                  lib1:0.9.1 (com.example.libs)
                      implementation
              debug (androidTest)
                  lib1:0.9.1 (com.example.libs)
              debug (test)
                  lib1:0.9.1 (com.example.libs)
              release (test)
                  lib1:0.9.1 (com.example.libs)""".trimIndent()
    treeStructure = node.testStructure({ !it.name.startsWith("appcompat-v7") })
    // Note: If fails see a nice diff by clicking <Click to see difference> in the IDEA output window.
    assertThat(treeStructure.toString(), equalTo(expectedProjectStructure))

    targetModulesTreeStructure.displayTargetModules(findModelsForSelection("com.example.jlib", "lib4"))
    expectedProjectStructure = """
      (null)
          mainModule
              freeDebug
                  lib3:1.0 (com.example.jlib)
                      lib2:1.0 (com.example.libs)
                          lib1:1.0 (com.example.libs)
                              implementation
                              debugImplementation
                      freeImplementation
              freeRelease
                  lib3:1.0 (com.example.jlib)
                      lib2:1.0 (com.example.libs)
                          lib1:1.0 (com.example.libs)
                              implementation
                              releaseImplementation
                      freeImplementation
              paidDebug
                  lib3:1.0 (com.example.jlib)
                      lib2:1.0 (com.example.libs)
                          lib1:1.0 (com.example.libs)
                              implementation
                              debugImplementation
              paidRelease
                  lib3:1.0 (com.example.jlib)
                      lib2:1.0 (com.example.libs)
                          lib1:1.0 (com.example.libs)
                              implementation
                              releaseImplementation
              freeDebug (androidTest)
                  lib3:1.0 (com.example.jlib)
                      lib2:1.0 (com.example.libs)
                          lib1:1.0 (com.example.libs)
              paidDebug (androidTest)
                  lib3:1.0 (com.example.jlib)
                      lib2:1.0 (com.example.libs)
                          lib1:1.0 (com.example.libs)
              freeDebug (test)
                  lib3:1.0 (com.example.jlib)
                      lib2:1.0 (com.example.libs)
                          lib1:1.0 (com.example.libs)
              freeRelease (test)
                  lib3:1.0 (com.example.jlib)
                      lib2:1.0 (com.example.libs)
                          lib1:1.0 (com.example.libs)
              paidDebug (test)
                  lib3:1.0 (com.example.jlib)
                      lib2:1.0 (com.example.libs)
                          lib1:1.0 (com.example.libs)
              paidRelease (test)
                  lib3:1.0 (com.example.jlib)
                      lib2:1.0 (com.example.libs)
                          lib1:1.0 (com.example.libs)
          modulePlus
              debug
                  lib3:0.9.1 (com.example.jlib)
                      lib2:0.9.1 (com.example.libs)
                          lib1:0.9.1 (com.example.libs)
                              implementation
              release
                  lib3:0.9.1 (com.example.jlib)
                      lib2:0.9.1 (com.example.libs)
                          lib1:0.9.1 (com.example.libs)
                              implementation
              debug (androidTest)
                  lib3:0.9.1 (com.example.jlib)
                      lib2:0.9.1 (com.example.libs)
                          lib1:0.9.1 (com.example.libs)
              debug (test)
                  lib3:0.9.1 (com.example.jlib)
                      lib2:0.9.1 (com.example.libs)
                          lib1:0.9.1 (com.example.libs)
              release (test)
                  lib3:0.9.1 (com.example.jlib)
                      lib2:0.9.1 (com.example.libs)
                          lib1:0.9.1 (com.example.libs)""".trimIndent()
    // Note: indentation matters!
    treeStructure = node.testStructure({ !it.name.startsWith("appcompat-v7") })
    // Note: If fails see a nice diff by clicking <Click to see difference> in the IDEA output window.
    assertThat(treeStructure.toString(), equalTo(expectedProjectStructure))
  }

  private fun findModelsForSelection(groupId: String, name: String): List<List<PsAndroidDependency>> {
    val nodeModels = mutableListOf<PsAndroidDependency>()

    // Simulate all-modules dependencies view single node selection.
    project.forEachModule(Consumer { module ->
      if (module is PsAndroidModule) {
        nodeModels.addAll(module.dependencies.findLibraryDependencies(groupId, name))
        module.variants.forEach { variant ->
          variant.forEachArtifact { artifact ->
            nodeModels.addAll(artifact.dependencies.findLibraryDependencies(groupId, name))
          }
        }
      }
    })
    return listOf(nodeModels)
  }
}