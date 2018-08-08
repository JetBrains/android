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
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem
import com.android.tools.idea.testing.IdeComponents
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.IdeaTestCase
import junit.framework.AssertionFailedError
import org.jetbrains.android.AndroidTestBase
import org.mockito.Mockito
import org.mockito.Mockito.times
import java.io.File


class GradleModuleSystemTest : IdeaTestCase() {
  private lateinit var gradleDependencyManager: GradleDependencyManager
  private lateinit var gradleProjectSystem: GradleProjectSystem
  private lateinit var gradleModuleSystem: GradleModuleSystem

  private val mavenRepository = object : GoogleMavenRepository(File(AndroidTestBase.getTestDataPath(),
      "../../project-system-gradle/testData/repoIndex"), cacheExpiryHours = Int.MAX_VALUE) {
    override fun readUrlData(url: String, timeout: Int): ByteArray? = throw AssertionFailedError("shouldn't try to read!")

    override fun error(throwable: Throwable, message: String?) {}
  }

  override fun setUp() {
    super.setUp()
    gradleDependencyManager = IdeComponents(myProject).mockProjectService(GradleDependencyManager::class.java)
    gradleProjectSystem = GradleProjectSystem(myModule.project, mavenRepository)
    gradleModuleSystem = GradleModuleSystem(myModule)
  }

  fun testRegisterDependency() {
    val coordinate = GoogleMavenArtifactId.CONSTRAINT_LAYOUT.getCoordinate("+")
    gradleModuleSystem.registerDependency(coordinate)
    Mockito.verify<GradleDependencyManager>(gradleDependencyManager, times(1))
      .addDependenciesWithoutSync(myModule, listOf(coordinate))
  }

  fun testPreviewDependencyIsAvailable() {
    // In the test data, NAVIGATION only has a preview version
    assertThat(gradleProjectSystem.getAvailableDependency(GoogleMavenArtifactId.NAVIGATION.getCoordinate("+"), true)).isNotNull()
  }

  fun testPreviewDependencyIsUnavailable() {
    // In the test data, NAVIGATION only has a preview version
    assertThat(gradleProjectSystem.getAvailableDependency(GoogleMavenArtifactId.NAVIGATION.getCoordinate("+"), false)).isNull()
  }

  fun testNoAndroidModuleModel() {
    // The AndroidModuleModel shouldn't be created when running from an IdeaTestCase.
    assertThat(AndroidModuleModel.get(myModule)).isNull()
    assertThat(gradleModuleSystem.getResolvedDependency(GoogleMavenArtifactId.APP_COMPAT_V7.getCoordinate("+"))).isNull()
  }
}
