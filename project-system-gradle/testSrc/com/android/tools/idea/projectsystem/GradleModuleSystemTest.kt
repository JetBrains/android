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
import com.android.ide.common.repository.GradleCoordinate
import com.android.tools.idea.gradle.dependencies.GradleDependencyManager
import com.android.tools.idea.projectsystem.gradle.GradleDependencyVersion
import com.android.tools.idea.projectsystem.gradle.GradleModuleSystem
import com.android.tools.idea.testing.IdeComponents
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.IdeaTestCase
import junit.framework.AssertionFailedError
import org.jetbrains.android.AndroidTestBase
import org.mockito.Mockito
import org.mockito.Mockito.times
import java.io.File
import kotlin.test.assertFailsWith


class GradleModuleSystemTest : IdeaTestCase() {
  private lateinit var ideComponents: IdeComponents
  private lateinit var gradleDependencyManager: GradleDependencyManager
  private lateinit var gradleModuleSystem: GradleModuleSystem

  private val mavenRepository = object : GoogleMavenRepository(File(AndroidTestBase.getTestDataPath(),
      "../../project-system-gradle/testData/repoIndex")) {
    override fun readUrlData(url: String, timeout: Int): ByteArray? = throw AssertionFailedError("shouldn't try to read!")

    override fun error(throwable: Throwable, message: String?) {}
  }

  override fun setUp() {
    super.setUp()
    ideComponents = IdeComponents(myProject)
    gradleDependencyManager = ideComponents.mockProjectService(GradleDependencyManager::class.java)
    gradleModuleSystem = GradleModuleSystem(myModule, mavenRepository)
  }

  override fun tearDown() {
    try {
      ideComponents.restore()
    }
    finally {
      super.tearDown()
    }
  }

  // fails; see http://b/72033729
  fun ignore_testAddDependency() {
    val toAdd = GoogleMavenArtifactId.CONSTRAINT_LAYOUT

    gradleModuleSystem.addDependencyWithoutSync(toAdd, null, false)

    Mockito.verify<GradleDependencyManager>(gradleDependencyManager, times(1))
        .addDependenciesWithoutSync(myModule, listOf(getLatestCoordinateForArtifactId(toAdd, false)!!))
  }

  fun testAddPreviewDependency() {
    // In the test data, NAVIGATION only has a preview version
    val toAdd = GoogleMavenArtifactId.NAVIGATION

    gradleModuleSystem.addDependencyWithoutSync(toAdd, null, true)

    Mockito.verify<GradleDependencyManager>(gradleDependencyManager, times(1))
        .addDependenciesWithoutSync(myModule, listOf(getLatestCoordinateForArtifactId(toAdd, true)!!))
  }

  fun testFailToAddPreviewDependency() {
    // In the test data, NAVIGATION only has a preview version
    val toAdd = GoogleMavenArtifactId.NAVIGATION

    val ex = assertFailsWith(DependencyManagementException::class, "Expected add to fail!") {
      gradleModuleSystem.addDependencyWithoutSync(toAdd, null, false)
    }
    assertThat(ex.errorCode).isEqualTo(DependencyManagementException.ErrorCodes.INVALID_ARTIFACT)
  }

  fun testAddDependencyWithBadVersion() {
    val toAdd = GoogleMavenArtifactId.CONSTRAINT_LAYOUT
    val version = GradleDependencyVersion(null)

    try {
      gradleModuleSystem.addDependencyWithoutSync(toAdd, version, false)
      fail("addDependencyWithoutSync should have thrown an exception.")
    }
    catch (e: DependencyManagementException) {
      assertThat(e.errorCode).isEqualTo(DependencyManagementException.ErrorCodes.INVALID_ARTIFACT)
    }
  }

  private fun getLatestCoordinateForArtifactId(id: GoogleMavenArtifactId, allowPreview: Boolean): GradleCoordinate? {
    val wildCardCoordinate = GradleCoordinate.parseCoordinateString(id.toString() + ":+")!!
    val version = mavenRepository.findVersion(wildCardCoordinate, null, allowPreview)
    return GradleCoordinate.parseCoordinateString(id.toString() + ":" + version)
  }
}
