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
import com.android.tools.idea.gradle.structure.configurables.dependencies.treeview.ResolvedDependenciesTreeRootNodeTest
import com.android.tools.idea.gradle.structure.model.android.psTestWithProject
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.intellij.testFramework.RunsInEdt

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
      project.runTestAndroidTreeStructure()
    }
  }
}