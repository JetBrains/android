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
import com.android.tools.idea.testing.TestProjectPaths.PSD_DEPENDENCY
import com.intellij.openapi.project.Project
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.CoreMatchers.nullValue
import org.junit.Assert.assertThat

class DependencyManagementTest : DependencyTestCase() {

  private lateinit var resolvedProject: Project
  private lateinit var project: PsProject

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
    assertThat(appModule.dependencies.findLibraryDependency("com.example.libs:lib1:1.0"), nullValue())
    assertThat(appModule.dependencies.findModuleDependency(":mainModule"), notNullValue())
    val libModule = project.findModuleByName("mainModule") as PsAndroidModule
    assertThat(libModule.dependencies.findLibraryDependency("com.example.libs:lib1:1.0"), notNullValue())
  }

  fun testAddLibraryDependency() {
    var module = project.findModuleByName("moduleA") as PsAndroidModule
    assertThat(module.dependencies.findLibraryDependency("com.example.libs:lib1:1.0"), nullValue())
    module.addLibraryDependency("com.example.libs:lib1:1.0", listOf("implementation"))
    assertThat(module.dependencies.findLibraryDependency("com.example.libs:lib1:1.0"), notNullValue())

    module.addLibraryDependency("com.example.libs:lib2:1.0", listOf("implementation"))
    assertThat(module.dependencies.findLibraryDependency("com.example.libs:lib1:1.0"), notNullValue())
    assertThat(module.dependencies.findLibraryDependency("com.example.libs:lib2:1.0"), notNullValue())

    project.applyChanges()
    requestSyncAndWait()
    reparse()

    module = project.findModuleByName("moduleA") as PsAndroidModule
    assertThat(module.dependencies.findLibraryDependency("com.example.libs:lib1:1.0"), notNullValue())
    assertThat(module.dependencies.findLibraryDependency("com.example.libs:lib2:1.0"), notNullValue())
  }

  fun testAddModuleDependency() {
    var module = project.findModuleByName("mainModule") as PsAndroidModule
    assertThat(module.dependencies.findModuleDependency(":moduleA"), nullValue())
    module.addModuleDependency(":moduleA", listOf("implementation"))
    assertThat(module.dependencies.findModuleDependency(":moduleA"), notNullValue())

    module.addModuleDependency(":moduleB", listOf("implementation"))
    assertThat(module.dependencies.findModuleDependency(":moduleA"), notNullValue())
    assertThat(module.dependencies.findModuleDependency(":moduleB"), notNullValue())

    project.applyChanges()
    requestSyncAndWait()
    reparse()

    module = project.findModuleByName("mainModule") as PsAndroidModule
    assertThat(module.dependencies.findModuleDependency(":moduleA"), notNullValue())
    assertThat(module.dependencies.findModuleDependency(":moduleB"), notNullValue())
  }

  fun testAddJavaModuleDependency() {
    var module = project.findModuleByName("mainModule") as PsAndroidModule
    assertThat(module.dependencies.findModuleDependency(":jModuleK"), nullValue())
    module.addModuleDependency(":jModuleK", listOf("implementation"))
    assertThat(module.dependencies.findModuleDependency(":jModuleK"), notNullValue())

    project.applyChanges()
    requestSyncAndWait()
    reparse()

    module = project.findModuleByName("mainModule") as PsAndroidModule
    assertThat(module.dependencies.findModuleDependency(":jModuleK"), notNullValue())
  }

  // TODO(solodkyy): Implement support for Java to Java module dependencies.
  fun /*test*/AddJavaModuleDependencyToJavaModule() {
//    var module = project.findModuleByName("jModuleK") as PsJavaModule
//    assertThat(module.findModuleDependency(":jModuleL"), nullValue())
//    module.addModuleDependency(":jModuleL", listOf("implementation"))
//    assertThat(module.findModuleDependency(":jModuleL"), notNullValue())
//
//    project.applyChanges()
//    requestSyncAndWait()
//    reparse()
//
//    module = project.findModuleByName("jModuleL") as PsJavaModule
//    assertThat(module.findModuleDependency(":jModuleL"), notNullValue())
  }
}
