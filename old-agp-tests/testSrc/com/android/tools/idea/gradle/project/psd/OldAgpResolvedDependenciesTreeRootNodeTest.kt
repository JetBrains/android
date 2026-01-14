/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.psd

import com.android.testutils.junit4.OldAgpTest
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.gradle.structure.configurables.dependencies.treeview.ResolvedDependenciesTreeRootNode
import com.android.tools.idea.gradle.structure.configurables.dependencies.treeview.ResolvedDependenciesTreeRootNodeTest
import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings
import com.android.tools.idea.gradle.structure.configurables.ui.testStructure
import com.android.tools.idea.gradle.structure.model.PsProjectImpl
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.android.psTestWithProject
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.intellij.testFramework.RunsInEdt
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.assertThat

/**
 * This test covers the same case as [ResolvedDependenciesTreeRootNodeTest] but for older versions of AGP where transitive dependency
 * information is not available in the mode. There should be no change in the resulting dependency tree apart from where the information
 * is being obtained from.
 */
@OldAgpTest(agpVersions = ["7.1.0"], gradleVersions = ["7.2"])
@RunsInEdt
class OldAgpResolvedDependenciesTreeRootNodeTest : ResolvedDependenciesTreeRootNodeTest() {
  override fun testTreeStructure() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_DEPENDENCY,
                                                         agpVersion = AgpVersionSoftwareEnvironmentDescriptor.AGP_71)
    projectRule.psTestWithProject(preparedProject) {
      project.runOldAgpTestAndroidTreeStructure()
    }
  }

  private fun PsProjectImpl.runOldAgpTestAndroidTreeStructure() {
    val appModule = findModuleByGradlePath(":app") as PsAndroidModule
    val node = ResolvedDependenciesTreeRootNode(appModule, PsUISettings())

    // Note: indentation matters!
    val expectedProjectStructure = """
    app
        freeDebug
            mainModule
                lib1:1.0 (com.example.libs)
                    lib2:1.0 (com.example.libs)
                        lib3:1.0 (com.example.jlib)
                            lib4:1.0 (com.example.jlib)
                lib3:0.6→1.0 (com.example.jlib)
                    lib4:1.0 (com.example.jlib)
        freeDebugAndroidTest
            freeDebug
                mainModule
                    lib1:1.0 (com.example.libs)
                        lib2:1.0 (com.example.libs)
                            lib3:1.0 (com.example.jlib)
                                lib4:1.0 (com.example.jlib)
                    lib3:0.6→1.0 (com.example.jlib)
                        lib4:1.0 (com.example.jlib)
        freeDebugUnitTest
            freeDebug
                mainModule
                    lib1:1.0 (com.example.libs)
                        lib2:1.0 (com.example.libs)
                            lib3:1.0 (com.example.jlib)
                                lib4:1.0 (com.example.jlib)
                    lib3:0.6→1.0 (com.example.jlib)
                        lib4:1.0 (com.example.jlib)
        freeRelease
            mainModule
                lib1:1.0,0.9.1→1.0 (com.example.libs)
                    lib2:1.0 (com.example.libs)
                        lib3:1.0 (com.example.jlib)
                            lib4:1.0 (com.example.jlib)
                lib3:0.6→1.0 (com.example.jlib)
                    lib4:1.0 (com.example.jlib)
        freeReleaseUnitTest
            freeRelease
                mainModule
                    lib1:1.0,0.9.1→1.0 (com.example.libs)
                        lib2:1.0 (com.example.libs)
                            lib3:1.0 (com.example.jlib)
                                lib4:1.0 (com.example.jlib)
                    lib3:0.6→1.0 (com.example.jlib)
                        lib4:1.0 (com.example.jlib)
        paidDebug
            mainModule
                lib1:1.0 (com.example.libs)
                    lib2:1.0 (com.example.libs)
                        lib3:1.0 (com.example.jlib)
                            lib4:1.0 (com.example.jlib)
        paidDebugAndroidTest
            paidDebug
                mainModule
                    lib1:1.0 (com.example.libs)
                        lib2:1.0 (com.example.libs)
                            lib3:1.0 (com.example.jlib)
                                lib4:1.0 (com.example.jlib)
        paidDebugUnitTest
            paidDebug
                mainModule
                    lib1:1.0 (com.example.libs)
                        lib2:1.0 (com.example.libs)
                            lib3:1.0 (com.example.jlib)
                                lib4:1.0 (com.example.jlib)
        paidRelease
            mainModule
                lib1:1.0,0.9.1→1.0 (com.example.libs)
                    lib2:1.0 (com.example.libs)
                        lib3:1.0 (com.example.jlib)
                            lib4:1.0 (com.example.jlib)
        paidReleaseUnitTest
            paidRelease
                mainModule
                    lib1:1.0,0.9.1→1.0 (com.example.libs)
                        lib2:1.0 (com.example.libs)
                            lib3:1.0 (com.example.jlib)
                                lib4:1.0 (com.example.jlib)""".trimIndent()
    val treeStructure = node.testStructure { !it.name.startsWith("appcompat-v7") }
    // Note: If fails see a nice diff by clicking <Click to see difference> in the IDEA output window.
    assertThat(treeStructure.toString(), equalTo(expectedProjectStructure))
  }
}
