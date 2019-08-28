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

import com.android.ide.common.repository.GradleCoordinate
import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.projectsystem.*
import com.google.common.truth.Truth
import com.intellij.testFramework.JavaProjectTestCase
import com.intellij.testFramework.registerExtension
import java.util.*

/**
 * Tests for [DependencyManagement].
 */
class DependencyManagementTest : JavaProjectTestCase() {
  private lateinit var projectSystem: TestProjectSystem
  private lateinit var syncManager: ProjectSystemSyncManager

  override fun setUp() {
    super.setUp()
    projectSystem = TestProjectSystem(myProject, availableDependencies = PLATFORM_SUPPORT_LIBS + NON_PLATFORM_SUPPORT_LAYOUT_LIBS,
                                      lastSyncResult = ProjectSystemSyncManager.SyncResult.UNKNOWN)
    project.registerExtension<AndroidProjectSystemProvider>(EP_NAME, projectSystem, testRootDisposable)
    syncManager = projectSystem.getSyncManager()
  }

  fun testDependsOnAndroidX() {
    projectSystem.addDependency(GoogleMavenArtifactId.APP_COMPAT_V7, myModule, GradleVersion(1337, 600613))
    projectSystem.addDependency(GoogleMavenArtifactId.ANDROIDX_APP_COMPAT_V7, myModule, GradleVersion(1337, 600613))

    Truth.assertThat(myModule.dependsOnAndroidx()).isTrue()
  }

  fun testDependsOnOldSupportLib() {
    projectSystem.addDependency(GoogleMavenArtifactId.APP_COMPAT_V7, myModule, GradleVersion(1337, 600613))
    projectSystem.addDependency(GoogleMavenArtifactId.ANDROIDX_APP_COMPAT_V7, myModule, GradleVersion(1337, 600613))

    Truth.assertThat(myModule.dependsOnOldSupportLib()).isTrue()
  }

  fun testDoesNotDependOnAndroidX() {
    projectSystem.addDependency(GoogleMavenArtifactId.APP_COMPAT_V7, myModule, GradleVersion(1337, 600613))

    Truth.assertThat(myModule.dependsOnAndroidx()).isFalse()
  }

  fun testDoesNotDependOnOldSupportLib() {
    projectSystem.addDependency(GoogleMavenArtifactId.ANDROIDX_APP_COMPAT_V7, myModule, GradleVersion(1337, 600613))

    Truth.assertThat(myModule.dependsOnOldSupportLib()).isFalse()
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

    Truth.assertThat(myModule.dependsOn(GoogleMavenArtifactId.DESIGN)).isFalse()
  }

  fun testAddEmptyListOfDependencies() {
    projectSystem.addDependency(GoogleMavenArtifactId.APP_COMPAT_V7, myModule, GradleVersion(1337, 600613))

    val dependenciesNotAdded = myModule.addDependencies(Collections.emptyList(), false)

    Truth.assertThat(dependenciesNotAdded.isEmpty()).isTrue()
    Truth.assertThat(syncManager.getLastSyncResult()).isSameAs(ProjectSystemSyncManager.SyncResult.UNKNOWN)
  }

  fun testAddDependency() {
    val constraintLayout = GoogleMavenArtifactId.CONSTRAINT_LAYOUT.getCoordinate("+")
    val dependenciesNotAdded = myModule.addDependencies(Collections.singletonList(constraintLayout), false)

    Truth.assertThat(myModule.getModuleSystem().getRegisteredDependency(constraintLayout)).isEqualTo(constraintLayout)
    Truth.assertThat(dependenciesNotAdded.isEmpty()).isTrue()
    Truth.assertThat(syncManager.getLastSyncResult()).isSameAs(ProjectSystemSyncManager.SyncResult.SUCCESS)
  }

  fun testAddMultipleDependencies() {
    val appCompat = GoogleMavenArtifactId.APP_COMPAT_V7.getCoordinate("+")
    val constraintLayout = GoogleMavenArtifactId.CONSTRAINT_LAYOUT.getCoordinate("+")
    val dependenciesNotAdded = myModule.addDependencies(listOf(constraintLayout, appCompat), false)

    Truth.assertThat(myModule.getModuleSystem().getRegisteredDependency(appCompat)).isEqualTo(appCompat)
    Truth.assertThat(myModule.getModuleSystem().getRegisteredDependency(constraintLayout)).isEqualTo(constraintLayout)
    Truth.assertThat(dependenciesNotAdded.isEmpty()).isTrue()
    Truth.assertThat(syncManager.getLastSyncResult()).isSameAs(ProjectSystemSyncManager.SyncResult.SUCCESS)
  }

  fun testAddSingleUnavailableDependencies() {
    // Note that during setup PLAY_SERVICES is not included in the list of available dependencies.
    val playServices = GoogleMavenArtifactId.PLAY_SERVICES.getCoordinate("+")
    val dependenciesNotAdded = myModule.addDependencies(listOf(playServices), false)

    Truth.assertThat(myModule.getModuleSystem().getRegisteredDependency(playServices)).isNull()
    Truth.assertThat(dependenciesNotAdded).containsExactly(playServices)
    Truth.assertThat(syncManager.getLastSyncResult()).isSameAs(ProjectSystemSyncManager.SyncResult.UNKNOWN)
  }

  fun testAddMultipleUnavailableDependencies() {
    // Note that during setup PLAY_SERVICES and PLAY_SERVICES_MAPS are not included in the list of available dependencies.
    val playServicesMaps = GoogleMavenArtifactId.PLAY_SERVICES_MAPS.getCoordinate("+")
    val playServices = GoogleMavenArtifactId.PLAY_SERVICES.getCoordinate("+")
    val dependenciesNotAdded = myModule.addDependencies(listOf(playServicesMaps, playServices), false)

    Truth.assertThat(myModule.getModuleSystem().getRegisteredDependency(playServicesMaps)).isNull()
    Truth.assertThat(myModule.getModuleSystem().getRegisteredDependency(playServices)).isNull()
    Truth.assertThat(dependenciesNotAdded).containsExactly(playServices, playServicesMaps)
    Truth.assertThat(syncManager.getLastSyncResult()).isSameAs(ProjectSystemSyncManager.SyncResult.UNKNOWN)
  }

  fun testAddMultipleDependenciesWithSomeUnavailable() {
    // Note that during setup PLAY_SERVICES is not included in the list of available dependencies.
    val appCompat = GoogleMavenArtifactId.APP_COMPAT_V7.getCoordinate("+")
    val playServices = GoogleMavenArtifactId.PLAY_SERVICES.getCoordinate("+")
    val dependenciesNotAdded = myModule.addDependencies(listOf(appCompat, playServices), false)

    Truth.assertThat(myModule.getModuleSystem().getRegisteredDependency(appCompat)).isEqualTo(appCompat)
    Truth.assertThat(myModule.getModuleSystem().getRegisteredDependency(playServices)).isNull()
    Truth.assertThat(dependenciesNotAdded).containsExactly(playServices)
    Truth.assertThat(syncManager.getLastSyncResult()).isSameAs(ProjectSystemSyncManager.SyncResult.SUCCESS)
  }

  fun testAddDependenciesWithoutTriggeringSync() {
    val constraintLayout = GoogleMavenArtifactId.CONSTRAINT_LAYOUT.getCoordinate("+")
    val dependenciesNotAdded = myModule.addDependencies(Collections.singletonList(constraintLayout), false, false)

    Truth.assertThat(myModule.getModuleSystem().getRegisteredDependency(constraintLayout)).isEqualTo(constraintLayout)
    Truth.assertThat(dependenciesNotAdded.isEmpty()).isTrue()
    Truth.assertThat(syncManager.getLastSyncResult()).isSameAs(ProjectSystemSyncManager.SyncResult.UNKNOWN)
  }

  fun testAddDependenciesWithoutUserApproval() {
    DEPENDENCY_MANAGEMENT_TEST_ASSUME_USER_WILL_ACCEPT_DEPENDENCIES = false
    val constraintLayout = GoogleMavenArtifactId.CONSTRAINT_LAYOUT.getCoordinate("+")
    val dependenciesNotAdded = myModule.addDependencies(Collections.singletonList(constraintLayout), true, false)

    Truth.assertThat(myModule.getModuleSystem().getRegisteredDependency(constraintLayout)).isNull()
    Truth.assertThat(dependenciesNotAdded).containsExactly(constraintLayout)
    Truth.assertThat(syncManager.getLastSyncResult()).isSameAs(ProjectSystemSyncManager.SyncResult.UNKNOWN)
  }

  fun testAddSomeInvalidDependenciesWithoutUserApproval() {
    DEPENDENCY_MANAGEMENT_TEST_ASSUME_USER_WILL_ACCEPT_DEPENDENCIES = false
    // Here CONSTRAINT_LAYOUT is valid but bad:worse:1.2.3 is not. If the user rejects the adding of dependencies then the resulting list
    // should contain both dependencies because none of them were added.
    val constraintLayout = GoogleMavenArtifactId.CONSTRAINT_LAYOUT.getCoordinate("+")
    val badNonExistentDependency = GradleCoordinate("bad", "worse", "1.2.3")
    val dependenciesNotAdded = myModule.addDependencies(listOf(constraintLayout, badNonExistentDependency), true, false)

    Truth.assertThat(myModule.getModuleSystem().getRegisteredDependency(constraintLayout)).isNull()
    Truth.assertThat(dependenciesNotAdded).containsExactly(constraintLayout, badNonExistentDependency)
    Truth.assertThat(syncManager.getLastSyncResult()).isSameAs(ProjectSystemSyncManager.SyncResult.UNKNOWN)
  }
}