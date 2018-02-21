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
package com.android.tools.idea.gradle.structure.model.android

import com.android.tools.idea.gradle.structure.model.PsProject
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.AndroidGradleTests
import com.android.tools.idea.testing.AndroidGradleTests.getLocalRepositories
import com.android.tools.idea.testing.TestProjectPaths.PSD_DEPENDENCY
import com.android.tools.idea.testing.TestProjectPaths.PSD_SAMPLE_REPO
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil.toSystemDependentName
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.CoreMatchers.nullValue
import org.jetbrains.android.AndroidTestBase
import org.junit.Assert.assertThat
import java.io.File

class DependencyManagementTest : AndroidGradleTestCase() {

  private lateinit var resolvedProject: Project
  private lateinit var project: PsProject

  override fun updateVersionAndDependencies(projectRoot: File) {
    val localRepositories = getLocalRepositories()
    val testRepositoryPath = File(AndroidTestBase.getTestDataPath(), toSystemDependentName(PSD_SAMPLE_REPO)).absolutePath!!
    val repositories = """
      maven {
        name 'test'
        url 'file:$testRepositoryPath'
      }
      $localRepositories
      """
    AndroidGradleTests.updateGradleVersions(projectRoot, repositories, null)
  }

  override fun setUp() {
    super.setUp()
    loadProject(PSD_DEPENDENCY)
    reparse()
  }

  private fun reparse() {
    resolvedProject = myFixture.project
    project = PsProject(resolvedProject)
  }

  fun testDependencies() {
    val appModule = project.findModuleByName("app") as PsAndroidModule
    assertThat(appModule.findLibraryDependency("com.example.libs:lib1:1.0"), nullValue())
    assertThat(appModule.findModuleDependency(":mainModule"), notNullValue())
    val libModule = project.findModuleByName("mainModule") as PsAndroidModule
    assertThat(libModule.findLibraryDependency("com.example.libs:lib1:1.0"), notNullValue())
  }

  fun testAddLibraryDependency() {
    var module = project.findModuleByName("moduleA") as PsAndroidModule
    assertThat(module.findLibraryDependency("com.example.libs:lib1:1.0"), nullValue())
    module.addLibraryDependency("com.example.libs:lib1:1.0", listOf("implementation"))
    assertThat(module.findLibraryDependency("com.example.libs:lib1:1.0"), notNullValue())

    // TODO(solodkyy): Fix adding the second dependency without syncing.
//    module.addLibraryDependency("com.example.libs:lib2:1.0", listOf("implementation"))
//    assertThat(module.findLibraryDependency("com.example.libs:lib1:1.0"), notNullValue())
//    assertThat(module.findLibraryDependency("com.example.libs:lib2:1.0"), notNullValue())

    project.applyChanges()
    requestSyncAndWait()
    reparse()

    module = project.findModuleByName("moduleA") as PsAndroidModule
    assertThat(module.findLibraryDependency("com.example.libs:lib1:1.0"), notNullValue())
  }
}
