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
package com.android.tools.idea.util

import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.projectsystem.*
import com.google.common.truth.Truth
import com.intellij.openapi.extensions.Extensions
import com.intellij.testFramework.IdeaTestCase
import com.intellij.testFramework.PlatformTestUtil
import java.util.*

/**
 * Tests for [DependencyManagement].
 */
class DependencyManagementTest : IdeaTestCase() {

  private lateinit var projectSystem: TestProjectSystem

  override fun setUp() {
    super.setUp()
    projectSystem = TestProjectSystem(myProject, availableDependencies = PLATFORM_SUPPORT_LIBS + NON_PLATFORM_SUPPORT_LAYOUT_LIBS)
    PlatformTestUtil.registerExtension<AndroidProjectSystemProvider>(Extensions.getArea(project), EP_NAME,
        projectSystem, testRootDisposable)
  }

  fun testUserConfirmationMultipleArtifactsMessage() {
    val artifacts = listOf(
      GoogleMavenArtifactId.DESIGN.getCoordinate("+"),
      GoogleMavenArtifactId.APP_COMPAT_V7.getCoordinate("+")
    )
    val correctMessage = "This operation requires the libraries com.android.support:design:+, " +
        "com.android.support:appcompat-v7:+. \n\nWould you like to add these now?"

    Truth.assertThat(createAddDependencyMessage(artifacts)).isEqualTo(correctMessage)
  }

  fun testUserConfirmationSingleArtifactsMessage() {
    val artifacts = listOf(GoogleMavenArtifactId.DESIGN.getCoordinate("+"))
    val correctMessage = "This operation requires the library com.android.support:design:+. \n\n" +
        "Would you like to add this now?"

    Truth.assertThat(createAddDependencyMessage(artifacts)).isEqualTo(correctMessage)
  }

  fun testDependsOnWhenDependencyExists() {
    projectSystem.addDependency(GoogleMavenArtifactId.APP_COMPAT_V7, myModule, GradleVersion(1337, 600613))

    Truth.assertThat(myModule.dependsOn(GoogleMavenArtifactId.APP_COMPAT_V7)).isTrue()
  }

  fun testDependsOnWhenDependencyDoesNotExist() {
    projectSystem.addDependency(GoogleMavenArtifactId.APP_COMPAT_V7, myModule, GradleVersion(1337, 600613))

    Truth.assertThat(myModule.dependsOn(GoogleMavenArtifactId.DESIGN)).isFalse();
  }

  fun testAddEmptyListOfDependencies() {
    projectSystem.addDependency(GoogleMavenArtifactId.APP_COMPAT_V7, myModule, GradleVersion(1337, 600613))

    val dependenciesNotAdded = myModule.addDependencies(Collections.emptyList(), false)

    Truth.assertThat(dependenciesNotAdded.isEmpty()).isTrue()
  }

  fun testAddNonPlatformSupportDependency() {
    projectSystem.addDependency(GoogleMavenArtifactId.APP_COMPAT_V7, myModule, GradleVersion(1337, 600613))

    val constraintLayoutQuery = GoogleMavenArtifactId.CONSTRAINT_LAYOUT.getCoordinate("+")
    val dependenciesNotAdded = myModule.addDependencies(
      Collections.singletonList(constraintLayoutQuery), false)

    Truth.assertThat(myModule.getModuleSystem().getRegisteredDependency(constraintLayoutQuery)).isEqualTo(
      constraintLayoutQuery)
    Truth.assertThat(dependenciesNotAdded.isEmpty()).isTrue()
  }

  fun testAddPlatformSupportDependencyWithExistingPlatformSupportDependency() {
    // Add a platform support lib artifact to the list of existing dependencies.
    projectSystem.addDependency(GoogleMavenArtifactId.APP_COMPAT_V7, myModule, GradleVersion(1337, 600613))

    val dependenciesNotAdded = myModule.addDependencies(Collections.singletonList(GoogleMavenArtifactId.DESIGN.getCoordinate("+")), false)

    // Version of design lib should match the version of previously added appcompat.
    Truth.assertThat(getResolvedVersion(GoogleMavenArtifactId.DESIGN)).isEqualTo(GradleVersion(1337, 600613))
    Truth.assertThat(dependenciesNotAdded).isEmpty()
  }

  fun testAddPlatformSupportDependencyWithoutExistingPlatformSupportDependency() {
    // Add a non-platform support lib artifact to the list of existing dependencies.
    projectSystem.addDependency(GoogleMavenArtifactId.ESPRESSO_CORE, myModule, GradleVersion(1337, 600613))

    val dependenciesNotAdded = myModule.addDependencies(Collections.singletonList(GoogleMavenArtifactId.DESIGN.getCoordinate("+")), false)

    // Version of design lib should be [TestProjectSystem.TEST_VERSION_LATEST] because there does not already exist a platform support lib.
    Truth.assertThat(getResolvedVersion(GoogleMavenArtifactId.DESIGN)).isEqualTo(TestProjectSystem.TEST_VERSION_LATEST.mavenVersion)
    Truth.assertThat(dependenciesNotAdded).isEmpty()
  }

  fun testAddMultiplePlatformSupportDependencyWithoutExistingPlatformSupportDependency() {
    // Add a non-platform support lib artifact to the list of existing dependencies.
    projectSystem.addDependency(GoogleMavenArtifactId.ESPRESSO_CORE, myModule, GradleVersion(1337, 600613))

    val dependenciesNotAdded = myModule.addDependencies(
      listOf(GoogleMavenArtifactId.DESIGN.getCoordinate("+"), GoogleMavenArtifactId.LEANBACK_V17.getCoordinate("+")), false)

    // Version of both libraries should be [TestProjectSystem.TEST_VERSION_LATEST] because there does not already exist a platform support lib.
    Truth.assertThat(getResolvedVersion(GoogleMavenArtifactId.DESIGN)).isEqualTo(TestProjectSystem.TEST_VERSION_LATEST.mavenVersion)
    Truth.assertThat(getResolvedVersion(GoogleMavenArtifactId.LEANBACK_V17)).isEqualTo(TestProjectSystem.TEST_VERSION_LATEST.mavenVersion)
    Truth.assertThat(dependenciesNotAdded).isEmpty()
  }

  fun testAddMultiplePlatformSupportDependencyWithExistingPlatformSupportDependency() {
    // Add a platform support lib artifact to the list of existing dependencies.
    projectSystem.addDependency(GoogleMavenArtifactId.APP_COMPAT_V7, myModule, GradleVersion(1337, 600613))

    val dependenciesNotAdded = myModule.addDependencies(
      listOf(GoogleMavenArtifactId.DESIGN.getCoordinate("+"), GoogleMavenArtifactId.LEANBACK_V17.getCoordinate("+")), false)

    // Version of both libraries should match version of AppCompat GradleVersion(1337, 600613) added above.
    Truth.assertThat(getResolvedVersion(GoogleMavenArtifactId.DESIGN)).isEqualTo(GradleVersion(1337, 600613))
    Truth.assertThat(getResolvedVersion(GoogleMavenArtifactId.LEANBACK_V17)).isEqualTo(GradleVersion(1337, 600613))
    Truth.assertThat(dependenciesNotAdded).isEmpty()
  }

  private fun getResolvedVersion(artifactId: GoogleMavenArtifactId): GradleVersion? =
    myModule.getModuleSystem().getResolvedDependency(artifactId.getCoordinate("+"))?.version
}