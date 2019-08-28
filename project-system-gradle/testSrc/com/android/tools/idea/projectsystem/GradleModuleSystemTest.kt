/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.projectsystem

import com.android.ide.common.repository.GoogleMavenRepository
import com.android.tools.idea.gradle.dependencies.GradleDependencyManager
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.projectsystem.gradle.GradleModuleSystem
import com.android.tools.idea.testing.IdeComponents
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.JavaProjectTestCase
import junit.framework.AssertionFailedError
import org.jetbrains.android.AndroidTestBase
import org.mockito.Mockito
import org.mockito.Mockito.times
import java.io.File


/**
 * These unit tests use a local test maven repo "project-system-gradle/testData/repoIndex". To see
 * what dependencies are available to test with, go to that folder and look at the group-indices.
 *
 * TODO:
 * Some cases of getAvailableDependency cannot be tested without AndroidGradleTestCase because it relies on real gradle models.
 * Because of this tests for getAvailableDependency with matching platform support libs reside in [GradleModuleSystemIntegrationTest].
 * Once we truly move dependency versioning logic into GradleDependencyManager the tests can be implemented there.
 */
class GradleModuleSystemTest : JavaProjectTestCase() {
  private lateinit var gradleDependencyManager: GradleDependencyManager
  private lateinit var gradleModuleSystem: GradleModuleSystem

  private val mavenRepository = object : GoogleMavenRepository(File(AndroidTestBase.getTestDataPath(),
      "../../project-system-gradle/testData/repoIndex"), cacheExpiryHours = Int.MAX_VALUE) {
    override fun readUrlData(url: String, timeout: Int): ByteArray? = throw AssertionFailedError("shouldn't try to read!")

    override fun error(throwable: Throwable, message: String?) {}
  }

  override fun setUp() {
    super.setUp()
    gradleDependencyManager = IdeComponents.mockProjectService(myProject, GradleDependencyManager::class.java, testRootDisposable)
    gradleModuleSystem = GradleModuleSystem(myModule, mavenRepository)
    assertThat(gradleModuleSystem.getResolvedDependentLibraries()).isEmpty()
  }

  fun testRegisterDependency() {
    val coordinate = GoogleMavenArtifactId.CONSTRAINT_LAYOUT.getCoordinate("+")
    gradleModuleSystem.registerDependency(coordinate)
    Mockito.verify<GradleDependencyManager>(gradleDependencyManager, times(1))
      .addDependenciesWithoutSync(myModule, listOf(coordinate))
  }

  fun testNoAndroidModuleModel() {
    // The AndroidModuleModel shouldn't be created when running from an JavaProjectTestCase.
    assertThat(AndroidModuleModel.get(myModule)).isNull()
    assertThat(gradleModuleSystem.getResolvedDependency(GoogleMavenArtifactId.APP_COMPAT_V7.getCoordinate("+"))).isNull()
  }

  fun testGetAvailableDependency_fallbackToPreview() {
    // In the test repo NAVIGATION only has a preview version 0.0.1-alpha1
    val coordinate = gradleModuleSystem.getLatestCompatibleDependency(
      GoogleMavenArtifactId.NAVIGATION.mavenGroupId, GoogleMavenArtifactId.NAVIGATION.mavenArtifactId)
    assertThat(coordinate).isNotNull()
    assertThat(coordinate?.isPreview).isTrue()
    assertThat(coordinate?.revision).isEqualTo("0.0.1-alpha1")
  }

  fun testGetAvailableDependency_returnsLatestStable() {
    // In the test repo CONSTRAINT_LAYOUT has a stable version of 1.0.2 and a beta version of 1.1.0-beta3
    val coordinate = gradleModuleSystem.getLatestCompatibleDependency(
      GoogleMavenArtifactId.CONSTRAINT_LAYOUT.mavenGroupId, GoogleMavenArtifactId.CONSTRAINT_LAYOUT.mavenArtifactId)
    assertThat(coordinate).isNotNull()
    assertThat(coordinate?.isPreview).isFalse()
    assertThat(coordinate?.revision).isEqualTo("1.0.2")
  }

  fun testGetAvailableDependency_returnsNullWhenNoneMatches() {
    // The test repo does not have any version of PLAY_SERVICES_ADS.
    val coordinate = gradleModuleSystem.getLatestCompatibleDependency(
      GoogleMavenArtifactId.PLAY_SERVICES_ADS.mavenGroupId, GoogleMavenArtifactId.PLAY_SERVICES_ADS.mavenArtifactId)
    assertThat(coordinate).isNull()
  }
}
