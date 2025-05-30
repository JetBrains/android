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
import com.android.ide.common.repository.GoogleMavenArtifactId
import com.android.tools.idea.projectsystem.NON_PLATFORM_SUPPORT_LAYOUT_LIBS
import com.android.tools.idea.projectsystem.PLATFORM_SUPPORT_LIBS
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.projectsystem.TestProjectSystem
import com.android.tools.idea.projectsystem.getModuleSystem
import com.google.common.truth.Truth
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.testFramework.LightPlatformTestCase

/**
 * Tests for [DependencyManagement].
 */
class DependencyManagementTest : LightPlatformTestCase() {

  private lateinit var projectSystem: TestProjectSystem
  private lateinit var syncManager: ProjectSystemSyncManager
  private val dialogMessages = mutableListOf<String>()
  private var dialogAnswer = Messages.OK

  override fun setUp() {
    super.setUp()
    projectSystem = TestProjectSystem(project, availableDependencies = PLATFORM_SUPPORT_LIBS + NON_PLATFORM_SUPPORT_LAYOUT_LIBS,
                                       lastSyncResult = ProjectSystemSyncManager.SyncResult.UNKNOWN)
    projectSystem.useInTests()
    syncManager = projectSystem.getSyncManager()

    val testDialog = TestDialog { message: String ->
      dialogMessages.add(message.trim()) // Remove line break in the end of the message.
      dialogAnswer
    }
    TestDialogManager.setTestDialog(testDialog)
  }

  override fun tearDown() {
    super.tearDown()
    TestDialogManager.setTestDialog(TestDialog.DEFAULT)
  }

  fun testDoesNotDependOnAndroidX() {
    projectSystem.addDependency(GoogleMavenArtifactId.SUPPORT_APPCOMPAT_V7, module, GradleVersion(1337, 600613))

    Truth.assertThat(module.dependsOnAndroidx()).isFalse()

    projectSystem.addDependency(GoogleMavenArtifactId.ANDROIDX_APPCOMPAT, module, GradleVersion(1337, 600613))

    Truth.assertThat(module.dependsOnAndroidx()).isFalse()
  }

  fun testDependsOnAndroidXWithUseAndroidX() {
    Truth.assertThat(module.dependsOnAndroidx()).isFalse()
    projectSystem.useAndroidX = true
    Truth.assertThat(module.dependsOnAndroidx()).isTrue()
  }

  fun testUserConfirmationMultipleArtifactsMessage() {
    val artifacts = setOf(
      GoogleMavenArtifactId.SUPPORT_DESIGN,
      GoogleMavenArtifactId.SUPPORT_APPCOMPAT_V7
    )
    val correctMessage = "This operation requires the libraries com.android.support:design, " +
                         "com.android.support:appcompat-v7.\n\nWould you like to add these now?"

    Truth.assertThat(createAddDependencyMessage(artifacts)).isEqualTo(correctMessage)
  }

  fun testUserConfirmationSingleArtifactsMessage() {
    val artifacts = setOf(GoogleMavenArtifactId.SUPPORT_DESIGN)
    val correctMessage = "This operation requires the library com.android.support:design.\n\n" +
                         "Would you like to add this now?"

    Truth.assertThat(createAddDependencyMessage(artifacts)).isEqualTo(correctMessage)
  }

  fun testUserConfirmationMultipleArtifactMessageWithWarning() {
    val artifacts = setOf(
      GoogleMavenArtifactId.SUPPORT_DESIGN,
      GoogleMavenArtifactId.SUPPORT_APPCOMPAT_V7
    )
    val warning = "Version incompatibility between: com.android.support:design:25.2.1 and: com.android.support::appcompat-v7:26.0.1"

    val correctMessage = """
      This operation requires the libraries com.android.support:design, com.android.support:appcompat-v7.

      Problem: Version incompatibility between: com.android.support:design:25.2.1 and: com.android.support::appcompat-v7:26.0.1

      The project may not compile after adding these libraries.
      Would you like to add them anyway?""".trimIndent()

    Truth.assertThat(createAddDependencyMessage(artifacts, warning)).isEqualTo(correctMessage)
  }

  fun testUserConfirmationSingleArtifactMessageWithWarning() {
    val artifacts = setOf(GoogleMavenArtifactId.SUPPORT_DESIGN)
    val warning = "Inconsistencies in the existing project dependencies found.\n" +
                  "Version incompatibility between: com.android.support:design:25.2.1 and: com.android.support::appcompat-v7:26.0.1"

    val correctMessage = """
      This operation requires the library com.android.support:design.

      Problem: Inconsistencies in the existing project dependencies found.
      Version incompatibility between: com.android.support:design:25.2.1 and: com.android.support::appcompat-v7:26.0.1

      The project may not compile after adding this library.
      Would you like to add it anyway?""".trimIndent()

    Truth.assertThat(createAddDependencyMessage(artifacts, warning)).isEqualTo(correctMessage)
  }

  fun testDependsOnWhenDependencyExists() {
    projectSystem.addDependency(GoogleMavenArtifactId.SUPPORT_APPCOMPAT_V7, module, GradleVersion(1337, 600613))

    Truth.assertThat(module.dependsOn(GoogleMavenArtifactId.SUPPORT_APPCOMPAT_V7)).isTrue()
  }

  fun testDependsOnWhenDependencyDoesNotExist() {
    projectSystem.addDependency(GoogleMavenArtifactId.SUPPORT_APPCOMPAT_V7, module, GradleVersion(1337, 600613))

    Truth.assertThat(module.dependsOn(GoogleMavenArtifactId.SUPPORT_DESIGN)).isFalse()
  }

  fun testAddEmptyListOfDependencies() {
    projectSystem.addDependency(GoogleMavenArtifactId.SUPPORT_APPCOMPAT_V7, module, GradleVersion(1337, 600613))

    val dependenciesNotAdded = module.addDependenciesWithUiConfirmation(setOf(), false)

    Truth.assertThat(dependenciesNotAdded).isEmpty()
    Truth.assertThat(syncManager.getLastSyncResult()).isSameAs(ProjectSystemSyncManager.SyncResult.UNKNOWN)
    Truth.assertThat(dialogMessages).isEmpty()
  }

  fun testAddDependency() {
    val constraintLayout = GoogleMavenArtifactId.CONSTRAINT_LAYOUT
    val dependenciesNotAdded = module.addDependenciesWithUiConfirmation(setOf(constraintLayout), false)

    Truth.assertThat(module.getModuleSystem().getRegisteredDependency(constraintLayout.getCoordinate("+"))).isEqualTo(constraintLayout.getCoordinate("+"))
    Truth.assertThat(projectSystem.getModuleSystem(module).getRegisteredDependency(constraintLayout)?.coordinate).isEqualTo(constraintLayout.getCoordinate("+"))
    Truth.assertThat(projectSystem.getModuleSystem(module).hasRegisteredDependency(constraintLayout)).isTrue()
    Truth.assertThat(dependenciesNotAdded).isEmpty()
    Truth.assertThat(syncManager.getLastSyncResult()).isSameAs(ProjectSystemSyncManager.SyncResult.SUCCESS)
    Truth.assertThat(dialogMessages).isEmpty()
  }

  fun testAddMultipleDependencies() {
    val appCompat = GoogleMavenArtifactId.SUPPORT_APPCOMPAT_V7
    val constraintLayout = GoogleMavenArtifactId.CONSTRAINT_LAYOUT
    val dependenciesNotAdded = module.addDependenciesWithUiConfirmation(setOf(constraintLayout, appCompat), false)

    Truth.assertThat(module.getModuleSystem().getRegisteredDependency(appCompat.getCoordinate("+"))).isEqualTo(appCompat.getCoordinate("+"))
    Truth.assertThat(projectSystem.getModuleSystem(module).getRegisteredDependency(appCompat)?.coordinate).isEqualTo(appCompat.getCoordinate("+"))
    Truth.assertThat(projectSystem.getModuleSystem(module).hasRegisteredDependency(appCompat)).isTrue()
    Truth.assertThat(module.getModuleSystem().getRegisteredDependency(constraintLayout.getCoordinate("+"))).isEqualTo(constraintLayout.getCoordinate("+"))
    Truth.assertThat(projectSystem.getModuleSystem(module).getRegisteredDependency(constraintLayout)?.coordinate).isEqualTo(constraintLayout.getCoordinate("+"))
    Truth.assertThat(projectSystem.getModuleSystem(module).hasRegisteredDependency(constraintLayout)).isTrue()
    Truth.assertThat(dependenciesNotAdded).isEmpty()
    Truth.assertThat(syncManager.getLastSyncResult()).isSameAs(ProjectSystemSyncManager.SyncResult.SUCCESS)
    Truth.assertThat(dialogMessages).isEmpty()
  }

  fun testAddSingleUnavailableDependencies() {
    // Note that during setup PLAY_SERVICES is not included in the list of available dependencies.
    val playServices = GoogleMavenArtifactId.PLAY_SERVICES
    val dependenciesNotAdded = module.addDependenciesWithUiConfirmation(setOf(playServices), false)

    Truth.assertThat(module.getModuleSystem().getRegisteredDependency(playServices.getCoordinate("+"))).isNull()
    Truth.assertThat(projectSystem.getModuleSystem(module).getRegisteredDependency(playServices)?.coordinate).isNull()
    Truth.assertThat(projectSystem.getModuleSystem(module).hasRegisteredDependency(playServices)).isFalse()
    Truth.assertThat(dependenciesNotAdded).containsExactly(playServices)
    Truth.assertThat(syncManager.getLastSyncResult()).isSameAs(ProjectSystemSyncManager.SyncResult.UNKNOWN)
    Truth.assertThat(dialogMessages).containsExactly("Can't find com.google.android.gms:play-services:+")
  }

  fun testAddMultipleUnavailableDependencies() {
    // Note that during setup PLAY_SERVICES and PLAY_SERVICES_MAPS are not included in the list of available dependencies.
    val playServicesMaps = GoogleMavenArtifactId.PLAY_SERVICES_MAPS
    val playServices = GoogleMavenArtifactId.PLAY_SERVICES
    val dependenciesNotAdded = module.addDependenciesWithUiConfirmation(setOf(playServicesMaps, playServices), false)

    Truth.assertThat(module.getModuleSystem().getRegisteredDependency(playServicesMaps.getCoordinate("+"))).isNull()
    Truth.assertThat(projectSystem.getModuleSystem(module).getRegisteredDependency(playServicesMaps)?.coordinate).isNull()
    Truth.assertThat(projectSystem.getModuleSystem(module).hasRegisteredDependency(playServicesMaps)).isFalse()
    Truth.assertThat(module.getModuleSystem().getRegisteredDependency(playServices.getCoordinate("+"))).isNull()
    Truth.assertThat(projectSystem.getModuleSystem(module).getRegisteredDependency(playServices)?.coordinate).isNull()
    Truth.assertThat(projectSystem.getModuleSystem(module).hasRegisteredDependency(playServices)).isFalse()
    Truth.assertThat(dependenciesNotAdded).containsExactly(playServices, playServicesMaps)
    Truth.assertThat(syncManager.getLastSyncResult()).isSameAs(ProjectSystemSyncManager.SyncResult.UNKNOWN)
    Truth.assertThat(dialogMessages).containsExactly("""
      Can't find com.google.android.gms:play-services-maps:+
      Can't find com.google.android.gms:play-services:+
      """.trimIndent())
  }

  fun testAddMultipleDependenciesWithSomeUnavailable() {
    // Note that during setup PLAY_SERVICES is not included in the list of available dependencies.
    val appCompat = GoogleMavenArtifactId.SUPPORT_APPCOMPAT_V7
    val playServices = GoogleMavenArtifactId.PLAY_SERVICES
    val dependenciesNotAdded = module.addDependenciesWithUiConfirmation(setOf(appCompat, playServices), false)

    Truth.assertThat(module.getModuleSystem().getRegisteredDependency(appCompat.getCoordinate("+"))).isEqualTo(appCompat.getCoordinate("+"))
    Truth.assertThat(projectSystem.getModuleSystem(module).getRegisteredDependency(appCompat)?.coordinate).isEqualTo(appCompat.getCoordinate("+"))
    Truth.assertThat(projectSystem.getModuleSystem(module).hasRegisteredDependency(appCompat)).isTrue()
    Truth.assertThat(module.getModuleSystem().getRegisteredDependency(playServices.getCoordinate("+"))).isNull()
    Truth.assertThat(projectSystem.getModuleSystem(module).getRegisteredDependency(playServices)?.coordinate).isNull()
    Truth.assertThat(projectSystem.getModuleSystem(module).hasRegisteredDependency(playServices)).isFalse()
    Truth.assertThat(dependenciesNotAdded).containsExactly(playServices)
    Truth.assertThat(syncManager.getLastSyncResult()).isSameAs(ProjectSystemSyncManager.SyncResult.SUCCESS)
    Truth.assertThat(dialogMessages).containsExactly("Can't find com.google.android.gms:play-services:+")
  }

  fun testAddDependenciesWithoutTriggeringSync() {
    val constraintLayout = GoogleMavenArtifactId.CONSTRAINT_LAYOUT
    val dependenciesNotAdded = module.addDependenciesWithUiConfirmation(setOf(constraintLayout), false, false)

    Truth.assertThat(module.getModuleSystem().getRegisteredDependency(constraintLayout.getCoordinate("+"))).isEqualTo(constraintLayout.getCoordinate("+"))
    Truth.assertThat(projectSystem.getModuleSystem(module).getRegisteredDependency(constraintLayout)?.coordinate).isEqualTo(constraintLayout.getCoordinate("+"))
    Truth.assertThat(projectSystem.getModuleSystem(module).hasRegisteredDependency(constraintLayout)).isTrue()
    Truth.assertThat(dependenciesNotAdded).isEmpty()
    Truth.assertThat(syncManager.getLastSyncResult()).isSameAs(ProjectSystemSyncManager.SyncResult.UNKNOWN)
    Truth.assertThat(dialogMessages).isEmpty()
  }

  fun testAddDependenciesWithoutUserApproval() {
    dialogAnswer = Messages.CANCEL

    val constraintLayout = GoogleMavenArtifactId.CONSTRAINT_LAYOUT
    val dependenciesNotAdded = module.addDependenciesWithUiConfirmation(setOf(constraintLayout), true, false)

    Truth.assertThat(module.getModuleSystem().getRegisteredDependency(constraintLayout.getCoordinate("+"))).isNull()
    Truth.assertThat(projectSystem.getModuleSystem(module).getRegisteredDependency(constraintLayout)?.coordinate).isNull()
    Truth.assertThat(projectSystem.getModuleSystem(module).hasRegisteredDependency(constraintLayout)).isFalse()
    Truth.assertThat(dependenciesNotAdded).containsExactly(constraintLayout)
    Truth.assertThat(syncManager.getLastSyncResult()).isSameAs(ProjectSystemSyncManager.SyncResult.UNKNOWN)
    Truth.assertThat(dialogMessages).containsExactly("""
      This operation requires the library com.android.support.constraint:constraint-layout.

      Would you like to add this now?
      """.trimIndent())
  }

  fun testAddDependenciesWithSomeErrorDuringRegistration() {
    val appCompat = GoogleMavenArtifactId.SUPPORT_APPCOMPAT_V7
    addFakeErrorForRegisteringMavenArtifact(appCompat, "Can't add appcompat because reasons.")
    val constraintLayout = GoogleMavenArtifactId.CONSTRAINT_LAYOUT
    val dependenciesNotAdded = module.addDependenciesWithUiConfirmation(setOf(constraintLayout, appCompat), false)

    Truth.assertThat(module.getModuleSystem().getRegisteredDependency(appCompat.getCoordinate("+"))).isNull()
    Truth.assertThat(projectSystem.getModuleSystem(module).getRegisteredDependency(appCompat)?.coordinate).isNull()
    Truth.assertThat(projectSystem.getModuleSystem(module).hasRegisteredDependency(appCompat)).isFalse()
    Truth.assertThat(module.getModuleSystem().getRegisteredDependency(constraintLayout.getCoordinate("+"))).isEqualTo(constraintLayout.getCoordinate("+"))
    Truth.assertThat(projectSystem.getModuleSystem(module).getRegisteredDependency(constraintLayout)?.coordinate).isEqualTo(constraintLayout.getCoordinate("+"))
    Truth.assertThat(projectSystem.getModuleSystem(module).hasRegisteredDependency(constraintLayout)).isTrue()
    Truth.assertThat(dependenciesNotAdded).containsExactly(appCompat)
    Truth.assertThat(syncManager.getLastSyncResult()).isSameAs(ProjectSystemSyncManager.SyncResult.SUCCESS)
    Truth.assertThat(dialogMessages).containsExactly("""
      The following dependencies could not be added:
      com.android.support:appcompat-v7:+ Reason: Can't add appcompat because reasons.

      A sync will be still be performed to resolve the dependencies that were added successfully.""".trimIndent())
  }

  fun testAddDependenciesAllWillErrorDuringRegistration() {
    val appCompat = GoogleMavenArtifactId.SUPPORT_APPCOMPAT_V7
    addFakeErrorForRegisteringMavenArtifact(appCompat, "Can't add appcompat because reasons.")
    val constraintLayout = GoogleMavenArtifactId.CONSTRAINT_LAYOUT
    addFakeErrorForRegisteringMavenArtifact(constraintLayout, "Can't add constraintLayout because reasons.")
    val dependenciesNotAdded = module.addDependenciesWithUiConfirmation(setOf(constraintLayout, appCompat), false)

    Truth.assertThat(module.getModuleSystem().getRegisteredDependency(appCompat.getCoordinate("+"))).isNull()
    Truth.assertThat(projectSystem.getModuleSystem(module).getRegisteredDependency(appCompat)?.coordinate).isNull()
    Truth.assertThat(projectSystem.getModuleSystem(module).hasRegisteredDependency(appCompat)).isFalse()
    Truth.assertThat(module.getModuleSystem().getRegisteredDependency(constraintLayout.getCoordinate("+"))).isNull()
    Truth.assertThat(projectSystem.getModuleSystem(module).getRegisteredDependency(constraintLayout)?.coordinate).isNull()
    Truth.assertThat(projectSystem.getModuleSystem(module).hasRegisteredDependency(constraintLayout)).isFalse()
    Truth.assertThat(dependenciesNotAdded).containsExactly(appCompat, constraintLayout)
    Truth.assertThat(syncManager.getLastSyncResult()).isSameAs(ProjectSystemSyncManager.SyncResult.UNKNOWN)
    Truth.assertThat(dialogMessages).containsExactly("""
      The following dependencies could not be added:
      com.android.support.constraint:constraint-layout:+ Reason: Can't add constraintLayout because reasons.
      com.android.support:appcompat-v7:+ Reason: Can't add appcompat because reasons.
      """.trimIndent())
  }

  fun testAddDependenciesWithUnavailableDependenciesAndSomeErrorDuringRegistration() {
    val appCompat = GoogleMavenArtifactId.SUPPORT_APPCOMPAT_V7
    addFakeErrorForRegisteringMavenArtifact(appCompat, "Can't add appcompat because reasons.")
    val constraintLayout = GoogleMavenArtifactId.CONSTRAINT_LAYOUT
    // Note that during setup PLAY_SERVICES is not included in the list of available dependencies.
    val playServices = GoogleMavenArtifactId.PLAY_SERVICES
    val dependenciesNotAdded = module.addDependenciesWithUiConfirmation(setOf(constraintLayout, appCompat, playServices), false)

    Truth.assertThat(module.getModuleSystem().getRegisteredDependency(appCompat.getCoordinate("+"))).isNull()
    Truth.assertThat(projectSystem.getModuleSystem(module).getRegisteredDependency(appCompat)?.coordinate).isNull()
    Truth.assertThat(projectSystem.getModuleSystem(module).hasRegisteredDependency(appCompat)).isFalse()
    Truth.assertThat(module.getModuleSystem().getRegisteredDependency(playServices.getCoordinate("+"))).isNull()
    Truth.assertThat(projectSystem.getModuleSystem(module).getRegisteredDependency(playServices)?.coordinate).isNull()
    Truth.assertThat(projectSystem.getModuleSystem(module).hasRegisteredDependency(playServices)).isFalse()
    Truth.assertThat(module.getModuleSystem().getRegisteredDependency(constraintLayout.getCoordinate("+"))).isEqualTo(constraintLayout.getCoordinate("+"))
    Truth.assertThat(projectSystem.getModuleSystem(module).getRegisteredDependency(constraintLayout)?.coordinate).isEqualTo(constraintLayout.getCoordinate("+"))
    Truth.assertThat(projectSystem.getModuleSystem(module).hasRegisteredDependency(constraintLayout)).isTrue()
    Truth.assertThat(dependenciesNotAdded).containsExactly(appCompat, playServices)
    Truth.assertThat(syncManager.getLastSyncResult()).isSameAs(ProjectSystemSyncManager.SyncResult.SUCCESS)
    Truth.assertThat(dialogMessages).containsExactly(
      "Can't find com.google.android.gms:play-services:+",
      """
      The following dependencies could not be added:
      com.android.support:appcompat-v7:+ Reason: Can't add appcompat because reasons.

      A sync will be still be performed to resolve the dependencies that were added successfully.""".trimIndent())
  }

  fun testAddMultipleDependenciesWithCompatibilityError() {
    val appCompat = GoogleMavenArtifactId.SUPPORT_APPCOMPAT_V7
    val constraintLayout = GoogleMavenArtifactId.CONSTRAINT_LAYOUT
    addIncompatibleArtifactPair(appCompat, constraintLayout)
    val dependenciesNotAdded = module.addDependenciesWithUiConfirmation(setOf(constraintLayout, appCompat), true)

    Truth.assertThat(module.getModuleSystem().getRegisteredDependency(appCompat.getCoordinate("+"))).isEqualTo(appCompat.getCoordinate("+"))
    Truth.assertThat(projectSystem.getModuleSystem(module).getRegisteredDependency(appCompat)?.coordinate).isEqualTo(appCompat.getCoordinate("+"))
    Truth.assertThat(projectSystem.getModuleSystem(module).hasRegisteredDependency(appCompat)).isTrue()
    Truth.assertThat(module.getModuleSystem().getRegisteredDependency(constraintLayout.getCoordinate("+"))).isEqualTo(constraintLayout.getCoordinate("+"))
    Truth.assertThat(projectSystem.getModuleSystem(module).getRegisteredDependency(constraintLayout)?.coordinate).isEqualTo(constraintLayout.getCoordinate("+"))
    Truth.assertThat(projectSystem.getModuleSystem(module).hasRegisteredDependency(constraintLayout)).isTrue()
    Truth.assertThat(dependenciesNotAdded).isEmpty()
    Truth.assertThat(syncManager.getLastSyncResult()).isSameAs(ProjectSystemSyncManager.SyncResult.SUCCESS)
    Truth.assertThat(dialogMessages).containsExactly("""
      This operation requires the libraries com.android.support.constraint:constraint-layout, com.android.support:appcompat-v7.

      Problem: com.android.support:appcompat-v7:+ is not compatible with com.android.support.constraint:constraint-layout:+


      The project may not compile after adding these libraries.
      Would you like to add them anyway?""".trimIndent())
  }

  private fun addFakeErrorForRegisteringMavenArtifact(id: GoogleMavenArtifactId, error: String) =
    projectSystem.addFakeErrorForRegisteringDependency(id.getCoordinate("+"), error)
  private fun addIncompatibleArtifactPair(id1: GoogleMavenArtifactId, id2: GoogleMavenArtifactId) =
    projectSystem.addIncompatibleDependencyPair(id1.getCoordinate("+"), id2.getCoordinate("+"))
}