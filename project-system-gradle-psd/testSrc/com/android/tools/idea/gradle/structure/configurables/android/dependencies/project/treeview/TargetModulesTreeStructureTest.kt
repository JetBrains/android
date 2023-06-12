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

import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings
import com.android.tools.idea.gradle.structure.configurables.ui.testStructure
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsNode
import com.android.tools.idea.gradle.structure.model.android.PsAndroidDependency
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.android.PsTestProject
import com.android.tools.idea.gradle.structure.model.android.psTestWithProject
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.google.common.truth.Truth
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test
import java.util.function.Consumer

@RunsInEdt
class TargetModulesTreeStructureTest {

  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @Test
  fun testTreeStructure() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_DEPENDENCY)
    projectRule.psTestWithProject(preparedProject) {

      val targetModulesTreeStructure = TargetModulesTreeStructure(PsUISettings())
      val node = targetModulesTreeStructure.rootElement as AbstractPsNode

      targetModulesTreeStructure.displayTargetModules(findModelsForSelection("com.example.libs", "lib1"))

      // Note: indentation matters!
      var expectedProjectStructure = """
      (null)
          mainModule
              freeDebug
                  (by) implementation
                  (by) debugImplementation
              freeRelease
                  (by) implementation
                  (by) releaseImplementation
              paidDebug
                  (by) implementation
                  (by) debugImplementation
              paidRelease
                  (by) implementation
                  (by) releaseImplementation
              freeDebug (androidTest)
              paidDebug (androidTest)
              freeDebug (test)
              freeRelease (test)
              paidDebug (test)
              paidRelease (test)
          modulePlus
              debug
                  (by) implementation
              release
                  (by) implementation
                  (by) releaseImplementation
              debug (androidTest)
              debug (test)
              release (test)""".trimIndent()
      var treeStructure = node.testStructure({ !it.name.startsWith("appcompat-v7") })
      // Note: If fails see a nice diff by clicking <Click to see difference> in the IDEA output window.
      Truth.assertThat(treeStructure.toString()).isEqualTo(expectedProjectStructure)

      targetModulesTreeStructure.displayTargetModules(findModelsForSelection("com.example.libs", "lib2"))

      // Note: indentation matters!
      // TODO(b/84996111): Tests artifact chains should also end with a declared dependency.
      expectedProjectStructure = """
      (null)
          mainModule
              freeDebug
                  (via) lib1:1.0 (com.example.libs)
                      (by) implementation
                      (by) debugImplementation
              freeRelease
                  (via) lib1:1.0 (com.example.libs)
                      (by) implementation
                      (by) releaseImplementation
              paidDebug
                  (via) lib1:1.0 (com.example.libs)
                      (by) implementation
                      (by) debugImplementation
              paidRelease
                  (via) lib1:1.0 (com.example.libs)
                      (by) implementation
                      (by) releaseImplementation
              freeDebug (androidTest)
                  (via) lib1:1.0 (com.example.libs)
              paidDebug (androidTest)
                  (via) lib1:1.0 (com.example.libs)
              freeDebug (test)
                  (via) lib1:1.0 (com.example.libs)
              freeRelease (test)
                  (via) lib1:1.0 (com.example.libs)
              paidDebug (test)
                  (via) lib1:1.0 (com.example.libs)
              paidRelease (test)
                  (via) lib1:1.0 (com.example.libs)
          modulePlus
              debug
                  (via) lib1:0.9.1 (com.example.libs)
                      (by) implementation
              release
                  (via) lib1:0.9.1 (com.example.libs)
                      (by) implementation
                      (by) releaseImplementation
              debug (androidTest)
                  (via) lib1:0.9.1 (com.example.libs)
              debug (test)
                  (via) lib1:0.9.1 (com.example.libs)
              release (test)
                  (via) lib1:0.9.1 (com.example.libs)""".trimIndent()
      treeStructure = node.testStructure({ !it.name.startsWith("appcompat-v7") })
      // Note: If fails see a nice diff by clicking <Click to see difference> in the IDEA output window.
      Truth.assertThat(treeStructure.toString()).isEqualTo(expectedProjectStructure)

      targetModulesTreeStructure.displayTargetModules(findModelsForSelection("com.example.jlib", "lib4"))
      expectedProjectStructure = """
      (null)
          mainModule
              freeDebug
                  (via) lib3:1.0 (com.example.jlib)
                      (via) lib2:1.0 (com.example.libs)
                          (via) lib1:1.0 (com.example.libs)
                              (by) implementation
                              (by) debugImplementation
                      (by) freeImplementation
              freeRelease
                  (via) lib3:1.0 (com.example.jlib)
                      (via) lib2:1.0 (com.example.libs)
                          (via) lib1:1.0 (com.example.libs)
                              (by) implementation
                              (by) releaseImplementation
                      (by) freeImplementation
              paidDebug
                  (via) lib3:1.0 (com.example.jlib)
                      (via) lib2:1.0 (com.example.libs)
                          (via) lib1:1.0 (com.example.libs)
                              (by) implementation
                              (by) debugImplementation
              paidRelease
                  (via) lib3:1.0 (com.example.jlib)
                      (via) lib2:1.0 (com.example.libs)
                          (via) lib1:1.0 (com.example.libs)
                              (by) implementation
                              (by) releaseImplementation
              freeDebug (androidTest)
                  (via) lib3:1.0 (com.example.jlib)
                      (via) lib2:1.0 (com.example.libs)
                          (via) lib1:1.0 (com.example.libs)
              paidDebug (androidTest)
                  (via) lib3:1.0 (com.example.jlib)
                      (via) lib2:1.0 (com.example.libs)
                          (via) lib1:1.0 (com.example.libs)
              freeDebug (test)
                  (via) lib3:1.0 (com.example.jlib)
                      (via) lib2:1.0 (com.example.libs)
                          (via) lib1:1.0 (com.example.libs)
              freeRelease (test)
                  (via) lib3:1.0 (com.example.jlib)
                      (via) lib2:1.0 (com.example.libs)
                          (via) lib1:1.0 (com.example.libs)
              paidDebug (test)
                  (via) lib3:1.0 (com.example.jlib)
                      (via) lib2:1.0 (com.example.libs)
                          (via) lib1:1.0 (com.example.libs)
              paidRelease (test)
                  (via) lib3:1.0 (com.example.jlib)
                      (via) lib2:1.0 (com.example.libs)
                          (via) lib1:1.0 (com.example.libs)
          modulePlus
              debug
                  (via) lib3:1.0 (com.example.jlib)
                      (via) lib2:0.9.1 (com.example.libs)
                          (via) lib1:0.9.1 (com.example.libs)
                              (by) implementation
              release
                  (via) lib3:1.0 (com.example.jlib)
                      (via) lib2:0.9.1 (com.example.libs)
                          (via) lib1:0.9.1 (com.example.libs)
                              (by) implementation
                              (by) releaseImplementation
              debug (androidTest)
                  (via) lib3:1.0 (com.example.jlib)
                      (via) lib2:0.9.1 (com.example.libs)
                          (via) lib1:0.9.1 (com.example.libs)
              debug (test)
                  (via) lib3:1.0 (com.example.jlib)
                      (via) lib2:0.9.1 (com.example.libs)
                          (via) lib1:0.9.1 (com.example.libs)
              release (test)
                  (via) lib3:1.0 (com.example.jlib)
                      (via) lib2:0.9.1 (com.example.libs)
                          (via) lib1:0.9.1 (com.example.libs)""".trimIndent()
      treeStructure = node.testStructure({ !it.name.startsWith("appcompat-v7") })
      // Note: If fails see a nice diff by clicking <Click to see difference> in the IDEA output window.
      Truth.assertThat(treeStructure.toString()).isEqualTo(expectedProjectStructure)
    }
  }

  private fun PsTestProject.findModelsForSelection(groupId: String, name: String): List<List<PsAndroidDependency>> {
    val nodeModels = mutableListOf<PsAndroidDependency>()

    // Simulate all-modules dependencies view single node selection.
    project.forEachModule(Consumer { module ->
      if (module is PsAndroidModule) {
        nodeModels.addAll(module.dependencies.findLibraryDependencies(groupId, name))
        module.resolvedVariants.forEach { variant ->
          variant.forEachArtifact { artifact ->
            nodeModels.addAll(artifact.dependencies.findLibraryDependencies(groupId, name))
          }
        }
      }
    })
    return listOf(nodeModels)
  }
}