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
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.gradle.structure.model.PsLibraryDependency
import com.android.tools.idea.gradle.structure.model.PsProject
import com.android.tools.idea.gradle.structure.model.PsProjectImpl
import com.android.tools.idea.gradle.structure.model.targetModuleResolvedDependencies
import com.android.tools.idea.gradle.structure.model.testResolve
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.EdtAndroidProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.onEdt
import com.intellij.openapi.project.Project
import com.intellij.testFramework.RunsInEdt
import org.hamcrest.CoreMatchers.*
import org.junit.Assert.assertThat
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class PsModuleAndroidDependencyTest {

  @get:Rule
  val projectRule: EdtAndroidProjectRule = AndroidProjectRule.withAndroidModels().onEdt()

  @Test
  fun testModuleDependenciesAreResolved() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_DEPENDENCY)
    projectRule.psTestWithProject(preparedProject) {
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
  }

  @Test
  fun testUnitTestArtifactModuleDependenciesAreResolved() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_DEPENDENCY)
    projectRule.psTestWithProject(preparedProject) {
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
  }

  @Test
  fun testDeclaredDependenciesReindexed() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_DEPENDENCY)
    projectRule.psTestWithProject(preparedProject) {
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
}
