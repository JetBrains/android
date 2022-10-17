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
package com.android.tools.idea.projectsystem.gradle

import com.android.tools.idea.gradle.project.importing.GradleProjectImporter
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.findAppModule
import com.android.tools.idea.testing.findModule
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.TruthJUnit
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.testFramework.PlatformTestUtil

class GradleModuleHierarchyProviderTest : AndroidGradleTestCase() {
  fun testCompositeStructure() {
    loadProject(TestProjectPaths.COMPOSITE_BUILD)
    val provider = GradleModuleHierarchyProvider(project)
    val mainProject = project.findModule(::testCompositeStructure.name)
    val project1 = project.findModule("TestCompositeLib1")
    val project2 = project.findModule("composite2")
    val project3 = project.findModule("TestCompositeLib3")
    val project4 = project.findModule("composite4")
    val project5 = project.findModule("compositeNest")
    assertThat(provider.forProject.submodules).containsExactly(mainProject, project1, project2, project3, project4, project5)
  }

  fun testUsualStructure() {
    loadProject(TestProjectPaths.SIMPLE_APPLICATION)
    val provider = GradleModuleHierarchyProvider(project)
    val app = project.findAppModule()
    assertThat(provider.forProject.submodules).containsExactly(app)
  }


  fun testFirstSyncFailedStructure() {
    prepareProjectForImport(TestProjectPaths.SIMPLE_APPLICATION, null, null, null, null, null)
    val buildFile = PlatformTestUtil.getOrCreateProjectBaseDir(project).findChild("build.gradle")!!
    runWriteAction {
      buildFile.setBinaryContent("*** this is an error ***".toByteArray())
    }
    GradleProjectImporter.configureNewProject(project)
    GradleProjectImporter.getInstance().importProjectNoSync(GradleProjectImporter.Request(project))
    requestSyncAndGetExpectedFailure()
    TruthJUnit.assume().that(ModuleManager.getInstance(project).modules).asList().hasSize(1)
    val provider = GradleModuleHierarchyProvider(project)
    // This case is handled by the AndroidViewProjectNode directly.
    assertThat(provider.forProject.submodules).isEmpty()
  }
}