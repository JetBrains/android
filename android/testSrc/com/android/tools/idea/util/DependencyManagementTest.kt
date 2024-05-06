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
import java.util.Collections

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
    projectSystem.addDependency(GoogleMavenArtifactId.APP_COMPAT_V7, module, GradleVersion(1337, 600613))

    Truth.assertThat(module.dependsOnAndroidx()).isFalse()

    projectSystem.addDependency(GoogleMavenArtifactId.ANDROIDX_APP_COMPAT_V7, module, GradleVersion(1337, 600613))

    Truth.assertThat(module.dependsOnAndroidx()).isFalse()
  }

  fun testDependsOnAndroidXWithUseAndroidX() {
    Truth.assertThat(module.dependsOnAndroidx()).isFalse()
    projectSystem.useAndroidX = true
    Truth.assertThat(module.dependsOnAndroidx()).isTrue()
  }

  fun testDependsOnOldSupportLib() {
    projectSystem.addDependency(GoogleMavenArtifactId.APP_COMPAT_V7, module, GradleVersion(1337, 600613))
    projectSystem.addDependency(GoogleMavenArtifactId.ANDROIDX_APP_COMPAT_V7, module, GradleVersion(1337, 600613))

    Truth.assertThat(module.dependsOnOldSupportLib()).isTrue()
  }

  fun testDoesNotDependOnOldSupportLib() {
    projectSystem.addDependency(GoogleMavenArtifactId.ANDROIDX_APP_COMPAT_V7, module, GradleVersion(1337, 600613))

    Truth.assertThat(module.dependsOnOldSupportLib()).isFalse()
  }

  fun testUserConfirmationMultipleArtifactsMessage() {
    val artifacts = listOf(
      GoogleMavenArtifactId.DESIGN.getCoordinate("+"),
      GoogleMavenArtifactId.APP_COMPAT_V7.getCoordinate("+")
    )
    val correctMessage = "This operation requires the libraries com.android.support:design:+, " +
                         "com.android.support:appcompat-v7:+.\n\nWould you like to add these now?"

    Truth.assertThat(createAddDependencyMessage(artifacts)).isEqualTo(correctMessage)
  }

  fun testUserConfirmationSingleArtifactsMessage() {
    val artifacts = listOf(GoogleMavenArtifactId.DESIGN.getCoordinate("+"))
    val correctMessage = "This operation requires the library com.android.support:design:+.\n\n" +
                         "Would you like to add this now?"

    Truth.assertThat(createAddDependencyMessage(artifacts)).isEqualTo(correctMessage)
  }

  fun testUserConfirmationMultipleArtifactMessageWithWarning() {
    val artifacts = listOf(
      GoogleMavenArtifactId.DESIGN.getCoordinate("25.2.1"),
      GoogleMavenArtifactId.APP_COMPAT_V7.getCoordinate("26.0.1")
    )
    val warning = "Version incompatibility between: com.android.support:design:25.2.1 and: com.android.support::appcompat-v7:26.0.1"

    val correctMessage = """
      This operation requires the libraries com.android.support:design:25.2.1, com.android.support:appcompat-v7:26.0.1.

      Problem: Version incompatibility between: com.android.support:design:25.2.1 and: com.android.support::appcompat-v7:26.0.1

      The project may not compile after adding these libraries.
      Would you like to add them anyway?""".trimIndent()

    Truth.assertThat(createAddDependencyMessage(artifacts, warning)).isEqualTo(correctMessage)
  }

  fun testUserConfirmationSingleArtifactMessageWithWarning() {
    val artifacts = listOf(GoogleMavenArtifactId.DESIGN.getCoordinate("25.2.1"))
    val warning = "Inconsistencies in the existing project dependencies found.\n" +
                  "Version incompatibility between: com.android.support:design:25.2.1 and: com.android.support::appcompat-v7:26.0.1"

    val correctMessage = """
      This operation requires the library com.android.support:design:25.2.1.

      Problem: Inconsistencies in the existing project dependencies found.
      Version incompatibility between: com.android.support:design:25.2.1 and: com.android.support::appcompat-v7:26.0.1

      The project may not compile after adding this library.
      Would you like to add it anyway?""".trimIndent()

    Truth.assertThat(createAddDependencyMessage(artifacts, warning)).isEqualTo(correctMessage)
  }

  fun testDependsOnWhenDependencyExists() {
    projectSystem.addDependency(GoogleMavenArtifactId.APP_COMPAT_V7, module, GradleVersion(1337, 600613))

    Truth.assertThat(module.dependsOn(GoogleMavenArtifactId.APP_COMPAT_V7)).isTrue()
  }

  fun testDependsOnWhenDependencyDoesNotExist() {
    projectSystem.addDependency(GoogleMavenArtifactId.APP_COMPAT_V7, module, GradleVersion(1337, 600613))

    Truth.assertThat(module.dependsOn(GoogleMavenArtifactId.DESIGN)).isFalse()
  }

  fun testAddEmptyListOfDependencies() {
    projectSystem.addDependency(GoogleMavenArtifactId.APP_COMPAT_V7, module, GradleVersion(1337, 600613))

    val dependenciesNotAdded = module.addDependenciesWithUiConfirmation(Collections.emptyList(), false)

    Truth.assertThat(dependenciesNotAdded.isEmpty()).isTrue()
    Truth.assertThat(syncManager.getLastSyncResult()).isSameAs(ProjectSystemSyncManager.SyncResult.UNKNOWN)
    Truth.assertThat(dialogMessages).isEmpty()
  }

  fun testAddDependency() {
    val constraintLayout = GoogleMavenArtifactId.CONSTRAINT_LAYOUT.getCoordinate("+")
    val dependenciesNotAdded = module.addDependenciesWithUiConfirmation(Collections.singletonList(constraintLayout), false)

    Truth.assertThat(module.getModuleSystem().getRegisteredDependency(constraintLayout)).isEqualTo(constraintLayout)
    Truth.assertThat(dependenciesNotAdded.isEmpty()).isTrue()
    Truth.assertThat(syncManager.getLastSyncResult()).isSameAs(ProjectSystemSyncManager.SyncResult.SUCCESS)
    Truth.assertThat(dialogMessages).isEmpty()
  }

  fun testAddMultipleDependencies() {
    val appCompat = GoogleMavenArtifactId.APP_COMPAT_V7.getCoordinate("+")
    val constraintLayout = GoogleMavenArtifactId.CONSTRAINT_LAYOUT.getCoordinate("+")
    val dependenciesNotAdded = module.addDependenciesWithUiConfirmation(listOf(constraintLayout, appCompat), false)

    Truth.assertThat(module.getModuleSystem().getRegisteredDependency(appCompat)).isEqualTo(appCompat)
    Truth.assertThat(module.getModuleSystem().getRegisteredDependency(constraintLayout)).isEqualTo(constraintLayout)
    Truth.assertThat(dependenciesNotAdded.isEmpty()).isTrue()
    Truth.assertThat(syncManager.getLastSyncResult()).isSameAs(ProjectSystemSyncManager.SyncResult.SUCCESS)
    Truth.assertThat(dialogMessages).isEmpty()
  }

  fun testAddSingleUnavailableDependencies() {
    // Note that during setup PLAY_SERVICES is not included in the list of available dependencies.
    val playServices = GoogleMavenArtifactId.PLAY_SERVICES.getCoordinate("+")
    val dependenciesNotAdded = module.addDependenciesWithUiConfirmation(listOf(playServices), false)

    Truth.assertThat(module.getModuleSystem().getRegisteredDependency(playServices)).isNull()
    Truth.assertThat(dependenciesNotAdded).containsExactly(playServices)
    Truth.assertThat(syncManager.getLastSyncResult()).isSameAs(ProjectSystemSyncManager.SyncResult.UNKNOWN)
    Truth.assertThat(dialogMessages).containsExactly("Can't find com.google.android.gms:play-services:+")
  }

  fun testAddMultipleUnavailableDependencies() {
    // Note that during setup PLAY_SERVICES and PLAY_SERVICES_MAPS are not included in the list of available dependencies.
    val playServicesMaps = GoogleMavenArtifactId.PLAY_SERVICES_MAPS.getCoordinate("+")
    val playServices = GoogleMavenArtifactId.PLAY_SERVICES.getCoordinate("+")
    val dependenciesNotAdded = module.addDependenciesWithUiConfirmation(listOf(playServicesMaps, playServices), false)

    Truth.assertThat(module.getModuleSystem().getRegisteredDependency(playServicesMaps)).isNull()
    Truth.assertThat(module.getModuleSystem().getRegisteredDependency(playServices)).isNull()
    Truth.assertThat(dependenciesNotAdded).containsExactly(playServices, playServicesMaps)
    Truth.assertThat(syncManager.getLastSyncResult()).isSameAs(ProjectSystemSyncManager.SyncResult.UNKNOWN)
    Truth.assertThat(dialogMessages).containsExactly("""
      Can't find com.google.android.gms:play-services-maps:+
      Can't find com.google.android.gms:play-services:+
      """.trimIndent())
  }

  fun testAddMultipleDependenciesWithSomeUnavailable() {
    // Note that during setup PLAY_SERVICES is not included in the list of available dependencies.
    val appCompat = GoogleMavenArtifactId.APP_COMPAT_V7.getCoordinate("+")
    val playServices = GoogleMavenArtifactId.PLAY_SERVICES.getCoordinate("+")
    val dependenciesNotAdded = module.addDependenciesWithUiConfirmation(listOf(appCompat, playServices), false)

    Truth.assertThat(module.getModuleSystem().getRegisteredDependency(appCompat)).isEqualTo(appCompat)
    Truth.assertThat(module.getModuleSystem().getRegisteredDependency(playServices)).isNull()
    Truth.assertThat(dependenciesNotAdded).containsExactly(playServices)
    Truth.assertThat(syncManager.getLastSyncResult()).isSameAs(ProjectSystemSyncManager.SyncResult.SUCCESS)
    Truth.assertThat(dialogMessages).containsExactly("Can't find com.google.android.gms:play-services:+")
  }

  fun testAddDependenciesWithoutTriggeringSync() {
    val constraintLayout = GoogleMavenArtifactId.CONSTRAINT_LAYOUT.getCoordinate("+")
    val dependenciesNotAdded = module.addDependenciesWithUiConfirmation(Collections.singletonList(constraintLayout), false, false)

    Truth.assertThat(module.getModuleSystem().getRegisteredDependency(constraintLayout)).isEqualTo(constraintLayout)
    Truth.assertThat(dependenciesNotAdded.isEmpty()).isTrue()
    Truth.assertThat(syncManager.getLastSyncResult()).isSameAs(ProjectSystemSyncManager.SyncResult.UNKNOWN)
    Truth.assertThat(dialogMessages).isEmpty()
  }

  fun testAddDependenciesWithoutUserApproval() {
    dialogAnswer = Messages.CANCEL

    val constraintLayout = GoogleMavenArtifactId.CONSTRAINT_LAYOUT.getCoordinate("+")
    val dependenciesNotAdded = module.addDependenciesWithUiConfirmation(Collections.singletonList(constraintLayout), true, false)

    Truth.assertThat(module.getModuleSystem().getRegisteredDependency(constraintLayout)).isNull()
    Truth.assertThat(dependenciesNotAdded).containsExactly(constraintLayout)
    Truth.assertThat(syncManager.getLastSyncResult()).isSameAs(ProjectSystemSyncManager.SyncResult.UNKNOWN)
    Truth.assertThat(dialogMessages).containsExactly("""
      This operation requires the library com.android.support.constraint:constraint-layout:+.

      Would you like to add this now?
      """.trimIndent())
  }

  fun testAddSomeNonExistentDependenciesWithoutUserApproval() {
    dialogAnswer = Messages.CANCEL

    // Here CONSTRAINT_LAYOUT is valid but bad:worse:1.2.3 is not. If the user rejects the adding of dependencies then the resulting list
    // should contain both dependencies because none of them were added.
    val constraintLayout = GoogleMavenArtifactId.CONSTRAINT_LAYOUT.getCoordinate("+")
    val badNonExistentDependency = GradleCoordinate("bad", "worse", "1.2.3")
    val dependenciesNotAdded = module.addDependenciesWithUiConfirmation(listOf(constraintLayout, badNonExistentDependency), true, false)

    Truth.assertThat(module.getModuleSystem().getRegisteredDependency(constraintLayout)).isNull()
    Truth.assertThat(dependenciesNotAdded).containsExactly(constraintLayout, badNonExistentDependency)
    Truth.assertThat(syncManager.getLastSyncResult()).isSameAs(ProjectSystemSyncManager.SyncResult.UNKNOWN)
    Truth.assertThat(dialogMessages).containsExactly("""
      This operation requires the libraries com.android.support.constraint:constraint-layout:+, bad:worse:1.2.3.

      Problem: Can't find bad:worse:1.2.3


      The project may not compile after adding these libraries.
      Would you like to add them anyway?""".trimIndent())
  }

  fun testAddDependenciesWithSomeErrorDuringRegistration() {
    val appCompat = GoogleMavenArtifactId.APP_COMPAT_V7.getCoordinate("+")
    projectSystem.addFakeErrorForRegisteringDependency(appCompat, "Can't add appcompat because reasons.")
    val constraintLayout = GoogleMavenArtifactId.CONSTRAINT_LAYOUT.getCoordinate("+")
    val dependenciesNotAdded = module.addDependenciesWithUiConfirmation(listOf(constraintLayout, appCompat), false)

    Truth.assertThat(module.getModuleSystem().getRegisteredDependency(appCompat)).isNull()
    Truth.assertThat(module.getModuleSystem().getRegisteredDependency(constraintLayout)).isEqualTo(constraintLayout)
    Truth.assertThat(dependenciesNotAdded).containsExactly(appCompat)
    Truth.assertThat(syncManager.getLastSyncResult()).isSameAs(ProjectSystemSyncManager.SyncResult.SUCCESS)
    Truth.assertThat(dialogMessages).containsExactly("""
      The following dependencies could not be added:
      com.android.support:appcompat-v7:+ Reason: Can't add appcompat because reasons.

      A sync will be still be performed to resolve the dependencies that were added successfully.""".trimIndent())
  }

  fun testAddDependenciesAllWillErrorDuringRegistration() {
    val appCompat = GoogleMavenArtifactId.APP_COMPAT_V7.getCoordinate("+")
    projectSystem.addFakeErrorForRegisteringDependency(appCompat, "Can't add appcompat because reasons.")
    val constraintLayout = GoogleMavenArtifactId.CONSTRAINT_LAYOUT.getCoordinate("+")
    projectSystem.addFakeErrorForRegisteringDependency(constraintLayout, "Can't add constraintLayout because reasons.")
    val dependenciesNotAdded = module.addDependenciesWithUiConfirmation(listOf(constraintLayout, appCompat), false)

    Truth.assertThat(module.getModuleSystem().getRegisteredDependency(appCompat)).isNull()
    Truth.assertThat(module.getModuleSystem().getRegisteredDependency(constraintLayout)).isNull()
    Truth.assertThat(dependenciesNotAdded).containsExactly(appCompat, constraintLayout)
    Truth.assertThat(syncManager.getLastSyncResult()).isSameAs(ProjectSystemSyncManager.SyncResult.UNKNOWN)
    Truth.assertThat(dialogMessages).containsExactly("""
      The following dependencies could not be added:
      com.android.support.constraint:constraint-layout:+ Reason: Can't add constraintLayout because reasons.
      com.android.support:appcompat-v7:+ Reason: Can't add appcompat because reasons.
      """.trimIndent())
  }

  fun testAddDependenciesWithUnavailableDependenciesAndSomeErrorDuringRegistration() {
    val appCompat = GoogleMavenArtifactId.APP_COMPAT_V7.getCoordinate("+")
    projectSystem.addFakeErrorForRegisteringDependency(appCompat, "Can't add appcompat because reasons.")
    val constraintLayout = GoogleMavenArtifactId.CONSTRAINT_LAYOUT.getCoordinate("+")
    // Note that during setup PLAY_SERVICES is not included in the list of available dependencies.
    val playServices = GoogleMavenArtifactId.PLAY_SERVICES.getCoordinate("+")
    val dependenciesNotAdded = module.addDependenciesWithUiConfirmation(listOf(constraintLayout, appCompat, playServices), false)

    Truth.assertThat(module.getModuleSystem().getRegisteredDependency(appCompat)).isNull()
    Truth.assertThat(module.getModuleSystem().getRegisteredDependency(playServices)).isNull()
    Truth.assertThat(module.getModuleSystem().getRegisteredDependency(constraintLayout)).isEqualTo(constraintLayout)
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
    val appCompat = GoogleMavenArtifactId.APP_COMPAT_V7.getCoordinate("+")
    val constraintLayout = GoogleMavenArtifactId.CONSTRAINT_LAYOUT.getCoordinate("+")
    projectSystem.addIncompatibleDependencyPair(appCompat, constraintLayout)
    val dependenciesNotAdded = module.addDependenciesWithUiConfirmation(listOf(constraintLayout, appCompat), true)

    Truth.assertThat(module.getModuleSystem().getRegisteredDependency(appCompat)).isEqualTo(appCompat)
    Truth.assertThat(module.getModuleSystem().getRegisteredDependency(constraintLayout)).isEqualTo(constraintLayout)
    Truth.assertThat(dependenciesNotAdded.isEmpty()).isTrue()
    Truth.assertThat(syncManager.getLastSyncResult()).isSameAs(ProjectSystemSyncManager.SyncResult.SUCCESS)
    Truth.assertThat(dialogMessages).containsExactly("""
      This operation requires the libraries com.android.support.constraint:constraint-layout:+, com.android.support:appcompat-v7:+.

      Problem: com.android.support:appcompat-v7:+ is not compatible with com.android.support.constraint:constraint-layout:+


      The project may not compile after adding these libraries.
      Would you like to add them anyway?""".trimIndent())
  }
}