/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.imports

import com.android.tools.idea.projectsystem.DependencyType
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.EdtAndroidProjectRule
import com.android.tools.idea.testing.JavaModuleModelBuilder
import com.android.tools.idea.testing.gradleModule
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
@RunsInEdt
class AndroidMavenImportGradleTokenTest {

  @get:Rule val projectRule: EdtAndroidProjectRule = AndroidProjectRule.withAndroidModels().onEdt()

  private val token = AndroidMavenImportGradleToken()

  @Test
  fun testShouldMapKmpArtifactsWhenGradlePluginVersionIsNull() {
    projectRule.setupProjectFrom(JavaModuleModelBuilder.rootModuleBuilder)

    val rootModule = projectRule.project.gradleModule(":")!!
    // Java/non-Android module has no GradleAndroidModel, so getGradlePluginVersion() returns null
    assertThat(token.shouldMapKmpArtifacts(rootModule)).isFalse()
  }

  @Test
  fun testShouldMapKmpArtifactsWithOldAgpVersion() {
    projectRule.setupProjectFrom(
      JavaModuleModelBuilder.rootModuleBuilder,
      AndroidModuleModelBuilder(":oldApp", agpVersion = "8.3.0", selectedBuildVariant = "debug", projectBuilder = AndroidProjectBuilder()),
    )

    val oldAppModule = projectRule.project.gradleModule(":oldApp")!!
    assertThat(token.shouldMapKmpArtifacts(oldAppModule)).isFalse()
  }

  @Test
  fun testShouldMapKmpArtifactsWithNewAgpVersion() {
    projectRule.setupProjectFrom(
      JavaModuleModelBuilder.rootModuleBuilder,
      AndroidModuleModelBuilder(":newApp", agpVersion = "8.4.0", selectedBuildVariant = "debug", projectBuilder = AndroidProjectBuilder()),
    )

    val newAppModule = projectRule.project.gradleModule(":newApp")!!
    assertThat(token.shouldMapKmpArtifacts(newAppModule)).isTrue()
  }

  @Test
  fun testDependsOnInvalidArtifactReturnsFalse() {
    projectRule.setupProjectFrom(
      JavaModuleModelBuilder.rootModuleBuilder,
      AndroidModuleModelBuilder(":app", "debug", AndroidProjectBuilder()),
    )

    val appModule = projectRule.project.gradleModule(":app")!!
    val projectSystem = GradleProjectSystem(projectRule.project)

    assertThat(token.dependsOn(projectSystem, appModule, "invalid_artifact")).isFalse()
  }

  @Test
  fun testAddDependencyRegistersDependency() {
    projectRule.setupProjectFrom(
      JavaModuleModelBuilder.rootModuleBuilder,
      AndroidModuleModelBuilder(":app", "debug", AndroidProjectBuilder()),
    )

    val appModule = projectRule.project.gradleModule(":app")!!
    val projectSystem = GradleProjectSystem(projectRule.project)

    // Verify adding versioned dependency does not throw
    token.addDependency(projectSystem, appModule, "androidx.appcompat:appcompat", "1.6.0", DependencyType.IMPLEMENTATION)

    // Verify adding unversioned / empty version dependency does not throw
    token.addDependency(projectSystem, appModule, "androidx.core:core-ktx", null, DependencyType.IMPLEMENTATION)
  }
}
