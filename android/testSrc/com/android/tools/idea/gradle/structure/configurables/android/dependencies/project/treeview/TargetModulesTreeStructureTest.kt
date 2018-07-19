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
                  com.example.libs:lib1:1.0
                      implementation
                      debugImplementation
              freeRelease
                  com.example.libs:lib1:1.0
                      implementation
                      releaseImplementation
              paidDebug
                  com.example.libs:lib1:1.0
                      implementation
                      debugImplementation
              paidRelease
                  com.example.libs:lib1:1.0
                      implementation
                      releaseImplementation
              freeDebug (androidTest)
                  com.example.libs:lib1:1.0
              paidDebug (androidTest)
                  com.example.libs:lib1:1.0
              freeDebug (test)
                  com.example.libs:lib1:1.0
              freeRelease (test)
                  com.example.libs:lib1:1.0
              paidDebug (test)
                  com.example.libs:lib1:1.0
              paidRelease (test)
                  com.example.libs:lib1:1.0
          modulePlus
              debug
                  com.example.libs:lib1:0.9.1
                      implementation
              release
                  com.example.libs:lib1:0.9.1
                      implementation
              debug (androidTest)
                  com.example.libs:lib1:0.9.1
              debug (test)
                  com.example.libs:lib1:0.9.1
              release (test)
                  com.example.libs:lib1:0.9.1""".trimIndent()
    treeStructure = node.testStructure({ !it.name.startsWith("appcompat-v7") })
    // Note: If fails see a nice diff by clicking <Click to see difference> in the IDEA output window.
    assertThat(treeStructure.toString(), equalTo(expectedProjectStructure))

    targetModulesTreeStructure.displayTargetModules(findModelsForSelection("com.example.jlib", "lib4"))
    // Note: indentation matters!
    expectedProjectStructure = """
      (null)
          mainModule
              freeDebug
                  com.example.jlib:lib3:1.0
                      com.example.libs:lib2:1.0
                          com.example.libs:lib1:1.0
                              implementation
                              debugImplementation
                      freeImplementation
              freeRelease
                  com.example.jlib:lib3:1.0
                      com.example.libs:lib2:1.0
                          com.example.libs:lib1:1.0
                              implementation
                              releaseImplementation
                      freeImplementation
              paidDebug
                  com.example.jlib:lib3:1.0
                      com.example.libs:lib2:1.0
                          com.example.libs:lib1:1.0
                              implementation
                              debugImplementation
              paidRelease
                  com.example.jlib:lib3:1.0
                      com.example.libs:lib2:1.0
                          com.example.libs:lib1:1.0
                              implementation
                              releaseImplementation
              freeDebug (androidTest)
                  com.example.jlib:lib3:1.0
                      com.example.libs:lib2:1.0
                          com.example.libs:lib1:1.0
              paidDebug (androidTest)
                  com.example.jlib:lib3:1.0
                      com.example.libs:lib2:1.0
                          com.example.libs:lib1:1.0
              freeDebug (test)
                  com.example.jlib:lib3:1.0
                      com.example.libs:lib2:1.0
                          com.example.libs:lib1:1.0
              freeRelease (test)
                  com.example.jlib:lib3:1.0
                      com.example.libs:lib2:1.0
                          com.example.libs:lib1:1.0
              paidDebug (test)
                  com.example.jlib:lib3:1.0
                      com.example.libs:lib2:1.0
                          com.example.libs:lib1:1.0
              paidRelease (test)
                  com.example.jlib:lib3:1.0
                      com.example.libs:lib2:1.0
                          com.example.libs:lib1:1.0
          modulePlus
              debug
                  com.example.jlib:lib3:0.9.1
                      com.example.libs:lib2:0.9.1
                          com.example.libs:lib1:0.9.1
                              implementation
              release
                  com.example.jlib:lib3:0.9.1
                      com.example.libs:lib2:0.9.1
                          com.example.libs:lib1:0.9.1
                              implementation
              debug (androidTest)
                  com.example.jlib:lib3:0.9.1
                      com.example.libs:lib2:0.9.1
                          com.example.libs:lib1:0.9.1
              debug (test)
                  com.example.jlib:lib3:0.9.1
                      com.example.libs:lib2:0.9.1
                          com.example.libs:lib1:0.9.1
              release (test)
                  com.example.jlib:lib3:0.9.1
                      com.example.libs:lib2:0.9.1
                          com.example.libs:lib1:0.9.1""".trimIndent()
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