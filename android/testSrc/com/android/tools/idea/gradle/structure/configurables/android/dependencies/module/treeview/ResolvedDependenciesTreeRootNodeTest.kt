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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.module.treeview

import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsNode
import com.android.tools.idea.gradle.structure.model.PsProject
import com.android.tools.idea.gradle.structure.model.android.DependencyTestCase
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.testing.TestProjectPaths
import com.intellij.openapi.project.Project
import com.intellij.util.containers.nullize
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.assertThat


class ResolvedDependenciesTreeRootNodeTest : DependencyTestCase() {
  private lateinit var resolvedProject: Project
  private lateinit var project: PsProject

  override fun setUp() {
    super.setUp()
    loadProject(TestProjectPaths.PSD_DEPENDENCY)
    reparse()
  }

  private fun reparse() {
    resolvedProject = myFixture.project
    project = PsProject(resolvedProject)
  }

  fun testTreeStructure() {
    val appModule = project.findModuleByGradlePath(":app") as PsAndroidModule
    val node = ResolvedDependenciesTreeRootNode(appModule, PsUISettings())

    // Note: indentation matters!
    val expectedProjectStructure = """app
    freeDebug
        mainModule
            lib1:1.0
                lib2:1.0
                    lib3:1.0
                        lib4:1.0
    freeDebugAndroidTest
        freeDebug
            mainModule
                lib1:1.0
                    lib2:1.0
                        lib3:1.0
                            lib4:1.0
    freeDebugUnitTest
        freeDebug
            mainModule
                lib1:1.0
                    lib2:1.0
                        lib3:1.0
                            lib4:1.0
    freeRelease
        mainModule
            lib1:1.0,0.9.1→1.0
                lib2:1.0
                    lib3:1.0
                        lib4:1.0
    freeReleaseUnitTest
        freeRelease
            mainModule
                lib1:1.0,0.9.1→1.0
                    lib2:1.0
                        lib3:1.0
                            lib4:1.0
    paidDebug
        mainModule
            lib1:1.0
                lib2:1.0
                    lib3:1.0
                        lib4:1.0
    paidDebugAndroidTest
        paidDebug
            mainModule
                lib1:1.0
                    lib2:1.0
                        lib3:1.0
                            lib4:1.0
    paidDebugUnitTest
        paidDebug
            mainModule
                lib1:1.0
                    lib2:1.0
                        lib3:1.0
                            lib4:1.0
    paidRelease
        mainModule
            lib1:1.0,0.9.1→1.0
                lib2:1.0
                    lib3:1.0
                        lib4:1.0
    paidReleaseUnitTest
        paidRelease
            mainModule
                lib1:1.0,0.9.1→1.0
                    lib2:1.0
                        lib3:1.0
                            lib4:1.0"""
    val treeStructure = node.testStructure({ !it.startsWith("appcompat-v7") })
    // Note: If fails see a nice diff by clicking <Click to see difference> in the IDEA output window.
    assertThat(treeStructure.toString(), equalTo(expectedProjectStructure))
  }
}

private data class TestTree(val text: String, val children: List<TestTree>?) {
  override fun toString(): String =
    listOfNotNull(text, children?.nullize()?.joinToString("\n")?.prependIndent("    ")).joinToString("\n")
}

private fun AbstractPsNode.testStructure(filter: (String) -> Boolean = { true }): TestTree =
  TestTree(name,
    this.children.mapNotNull { it as? AbstractPsNode }.filter { filter(it.name) }.map { it.testStructure(filter) })

