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

import com.android.builder.model.AndroidProject.ARTIFACT_MAIN
import com.android.builder.model.AndroidProject.ARTIFACT_UNIT_TEST
import com.android.tools.idea.gradle.structure.model.PsProject
import com.android.tools.idea.testing.TestProjectPaths
import com.intellij.openapi.project.Project
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.notNullValue
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
    project = PsProject(resolvedProject)
  }

  fun testModuleDependenciesAreResolved() {
    val appModule = project.findModuleByName("app") as PsAndroidModule

    val freeReleaseMainArtifact = appModule.findVariant("freeRelease")?.findArtifact(ARTIFACT_MAIN)!!
    val artifactDependencies = PsAndroidArtifactDependencyCollection(freeReleaseMainArtifact)

    val moduleDependency = artifactDependencies.findModuleDependency(":mainModule")
    assertThat(moduleDependency, notNullValue())

    val referredArtifact = moduleDependency?.findReferredArtifact()
    assertThat(referredArtifact, notNullValue())
    assertThat(referredArtifact!!.parent.name, equalTo("freeRelease"))
    assertThat(referredArtifact.resolvedName, equalTo(ARTIFACT_MAIN))
  }

  fun testUnitTestArtifactModuleDependenciesAreResolved() {
    val appModule = project.findModuleByName("app") as PsAndroidModule

    val freeReleaseMainArtifact = appModule.findVariant("freeRelease")?.findArtifact(ARTIFACT_UNIT_TEST)!!
    val artifactDependencies = PsAndroidArtifactDependencyCollection(freeReleaseMainArtifact)

    val moduleDependency = artifactDependencies.findModuleDependency(":mainModule")
    assertThat(moduleDependency, notNullValue())

    val referredArtifact = moduleDependency?.findReferredArtifact()
    assertThat(referredArtifact, notNullValue())
    assertThat(referredArtifact!!.parent.name, equalTo("freeRelease"))
    assertThat(referredArtifact.resolvedName, equalTo(ARTIFACT_MAIN))
  }
}