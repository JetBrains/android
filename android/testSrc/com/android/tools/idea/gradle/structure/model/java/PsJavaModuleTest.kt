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
package com.android.tools.idea.gradle.structure.model.java

import com.android.tools.idea.gradle.structure.model.PsModule
import com.android.tools.idea.gradle.structure.model.PsProject
import com.android.tools.idea.gradle.structure.model.PsProjectImpl
import com.android.tools.idea.gradle.structure.model.android.DependencyTestCase
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.testResolve
import com.android.tools.idea.testing.TestProjectPaths
import com.google.common.truth.Truth

/**
 * Tests for [PsAndroidModule].
 */
class PsJavaModuleTest : DependencyTestCase() {

  fun testImportantConfigurations() {
    loadProject(TestProjectPaths.PSD_SAMPLE_GROOVY)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    val appModule = moduleWithSyncedModel(project, "jav")
    assertNotNull(appModule)

    Truth.assertThat(appModule.getConfigurations(onlyImportantFor = PsModule.ImportantFor.LIBRARY)).containsExactly(
      "implementation",
      "annotationProcessor",
      "api",
      "compile",
      "runtime",
      "testAnnotationProcessor",
      "testImplementation",
      "testRuntime")

    Truth.assertThat(appModule.getConfigurations(onlyImportantFor = PsModule.ImportantFor.MODULE)).containsExactly(
      "implementation",
      "annotationProcessor",
      "api",
      "compile",
      "runtime",
      "testAnnotationProcessor",
      "testImplementation",
      "testRuntime")
  }

  // TODO(b/117969438) : Find out what the correct configurations are and implement.
  fun /*testC*/onfigurations() {
    loadProject(TestProjectPaths.PSD_SAMPLE_GROOVY)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    val appModule = moduleWithSyncedModel(project, "jav")
    assertNotNull(appModule)

    TODO("b/117969438")
  }

}

private fun moduleWithoutSyncedModel(project: PsProject, name: String): PsJavaModule {
  val moduleWithSyncedModel = project.findModuleByName(name) as PsJavaModule
  return PsJavaModule(project, moduleWithSyncedModel.gradlePath).apply {
    init(moduleWithSyncedModel.name, null, null, moduleWithSyncedModel.parsedModel)
  }
}

private fun moduleWithSyncedModel(project: PsProject, name: String): PsJavaModule = project.findModuleByName(name) as PsJavaModule
