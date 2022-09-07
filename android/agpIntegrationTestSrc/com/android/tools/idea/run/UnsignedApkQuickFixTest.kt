/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.run

import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

class UnsignedApkQuickFixTest {
  @get:Rule
  val projectRule = AndroidGradleProjectRule()

  @Test
  fun updatesBuildWithSelectedConfig() {
    projectRule.load(TestProjectPaths.SIMPLE_APPLICATION)

    // There should be a default debug signing config.
    val module = projectRule.fixture.module
    val buildModel = ProjectBuildModel.get(projectRule.project).getModuleBuildModel(module)!!
    val signingConfigs = buildModel.android().signingConfigs()
    assertThat(signingConfigs).hasSize(1)
    assertThat(signingConfigs[0].name()).isEqualTo("debug")

    // Fake a selector that picks the default debug signing config.
    val fakeSelector = object : SigningConfigSelector {
      override fun showAndGet() = true
      override fun selectedConfig() = signingConfigs[0]
    }

    // Release build doesn't have a signing config assigned.
    val releaseBuildSigningConfig = buildModel.android().buildTypes().find { it.name() == "release" }?.signingConfig()
    assertThat(releaseBuildSigningConfig?.valueAsString()).isNull()

    val unsignedApkQuickFix = UnsignedApkQuickFix(module, "release") { fakeSelector }
    unsignedApkQuickFix.run()
    val updatedBuildModel = ProjectBuildModel.get(projectRule.project).getModuleBuildModel(module)!!

    // Release build is now assigned the debug signing config.
    val expectedSigningConfig = updatedBuildModel.android().buildTypes().find { it.name() == "release" }?.signingConfig()
    assertThat(expectedSigningConfig?.valueAsString()).contains("debug")
  }
}