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
package com.android.tools.idea.gradle.structure.model

import com.android.tools.idea.gradle.structure.model.android.DependencyTestCase
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.java.PsJavaModule
import com.android.tools.idea.testing.TestProjectPaths
import com.google.common.truth.Truth

/**
 * Tests for [PsModuleType].
 */
class PsModuleTypeTest : DependencyTestCase() {

  fun testProjectTypeDetection() {
    loadProject(TestProjectPaths.PSD_SAMPLE_GROOVY)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject)

    Truth.assertThat(moduleWithSyncedModel(project, "app").projectType).isEqualTo(PsModuleType.ANDROID_APP)
    Truth.assertThat(moduleWithSyncedModel(project, "lib").projectType).isEqualTo(PsModuleType.ANDROID_LIBRARY)
    Truth.assertThat(moduleWithSyncedModel(project, "jav").projectType).isEqualTo(PsModuleType.JAVA)
  }

  fun testFallbackProjectTypeDetection() {
    loadProject(TestProjectPaths.PSD_SAMPLE_GROOVY)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject)

    Truth.assertThat(moduleWithoutSyncedModel(project, "app").projectType).isEqualTo(PsModuleType.ANDROID_APP)
    Truth.assertThat(moduleWithoutSyncedModel(project, "lib").projectType).isEqualTo(PsModuleType.ANDROID_LIBRARY)
    Truth.assertThat(moduleWithoutSyncedModel(project, "jav").projectType).isEqualTo(PsModuleType.JAVA)
    Truth.assertThat(moduleWithoutSyncedModel(project, "app").parsedModel?.parsedModelModuleType()).isEqualTo(PsModuleType.ANDROID_APP)
    Truth.assertThat(moduleWithoutSyncedModel(project, "lib").parsedModel?.parsedModelModuleType()).isEqualTo(PsModuleType.ANDROID_LIBRARY)
    Truth.assertThat(moduleWithoutSyncedModel(project, "jav").parsedModel?.parsedModelModuleType()).isEqualTo(PsModuleType.JAVA)
  }
}

private fun moduleWithoutSyncedModel(project: PsProject, name: String): PsModule {
  val moduleWithSyncedModel = project.findModuleByName(name)
  return when (moduleWithSyncedModel) {
    is PsAndroidModule -> PsAndroidModule(project, moduleWithSyncedModel.gradlePath).apply {
      init(moduleWithSyncedModel.name, null, null, null, null, moduleWithSyncedModel.parsedModel)
    }
    is PsJavaModule -> PsJavaModule(project, moduleWithSyncedModel.gradlePath).apply {
      init(moduleWithSyncedModel.name, null, null, moduleWithSyncedModel.parsedModel)
    }
    else -> throw IllegalArgumentException()
  }
}

private fun moduleWithSyncedModel(project: PsProject, name: String): PsModule = project.findModuleByName(name) as PsModule
