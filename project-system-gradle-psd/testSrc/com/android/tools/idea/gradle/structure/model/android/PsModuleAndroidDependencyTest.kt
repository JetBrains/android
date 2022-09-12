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

import com.android.tools.idea.gradle.model.IdeArtifactName
import com.android.tools.idea.gradle.structure.model.PsLibraryDependency
import com.android.tools.idea.gradle.structure.model.PsProject
import com.android.tools.idea.gradle.structure.model.PsProjectImpl
import com.android.tools.idea.gradle.structure.model.targetModuleResolvedDependencies
import com.android.tools.idea.gradle.structure.model.testResolve
import com.android.tools.idea.testing.TestProjectPaths
import com.intellij.openapi.project.Project
import org.hamcrest.CoreMatchers.*
import org.junit.Assert.assertThat

class PsModuleAndroidDependencyTest : DependencyTestCase() {

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

  fun testModuleDependenciesAreResolved() {
    val appModule = project.findModuleByName("app") as PsAndroidModule

    val freeReleaseMainArtifact = appModule.findVariant("freeRelease")?.findArtifact(IdeArtifactName.MAIN)!!
    val artifactDependencies = freeReleaseMainArtifact.dependencies

    val moduleDependency = artifactDependencies.findModuleDependency(":mainModule")
    assertThat(moduleDependency, notNullValue())

    val referredArtifact = (moduleDependency?.targetModuleResolvedDependencies as? PsAndroidArtifactDependencyCollection)?.artifact
    assertThat(referredArtifact, notNullValue())
    assertThat(referredArtifact!!.parent.name, equalTo("freeRelease"))
    assertThat(referredArtifact.resolvedName, equalTo(IdeArtifactName.MAIN))
  }

  fun testUnitTestArtifactModuleDependenciesAreResolved() {
    val appModule = project.findModuleByName("app") as PsAndroidModule

    val freeReleaseMainArtifact = appModule.findVariant("freeRelease")?.findArtifact(IdeArtifactName.UNIT_TEST)!!
    val artifactDependencies = freeReleaseMainArtifact.dependencies

    val moduleDependency = artifactDependencies.findModuleDependency(":mainModule")
    assertThat(moduleDependency, notNullValue())

    val referredArtifact = (moduleDependency?.targetModuleResolvedDependencies as? PsAndroidArtifactDependencyCollection)?.artifact
    assertThat(referredArtifact, notNullValue())
    assertThat(referredArtifact!!.parent.name, equalTo("freeRelease"))
    assertThat(referredArtifact.resolvedName, equalTo(IdeArtifactName.MAIN))
  }

  fun testDeclaredDependenciesReindexed() {
    val appModule = project.findModuleByName("mainModule") as PsAndroidModule

    fun findLib(name: String, version: String) =
      appModule.dependencies
        .findLibraryDependencies("com.example.libs", name)
        .find { (it as? PsLibraryDependency)?.spec?.version == version }

    val releaseImplementationLib1 = findLib("lib1", "0.9.1")

    assertThat(releaseImplementationLib1, notNullValue())

    // Make a change that requires re-indexing.
    releaseImplementationLib1!!.parsedModel.name().setValue("lib2")
    appModule.dependencies.reindex()

    assertThat(findLib("lib1", "0.9.1"), nullValue())
    assertThat(findLib("lib2", "0.9.1"), notNullValue())
  }
}
