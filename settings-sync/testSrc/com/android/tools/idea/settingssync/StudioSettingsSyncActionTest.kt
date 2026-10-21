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
package com.android.tools.idea.settingssync

import com.android.flags.junit.FlagRule
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.settingssync.onboarding.BackupAndSyncWizard
import com.android.tools.idea.settingssync.onboarding.BackupAndSyncWizardProvider
import com.google.gct.login2.LoginUsersRule
import com.google.gct.login2.PreferredUser
import com.google.gct.wizard.StructuredFlowWizard
import com.google.wireless.android.sdk.stats.GoogleLoginPluginEvent
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.settingsSync.core.SettingsSyncSettings
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.executeSomeCoroutineTasksAndDispatchAllInvocationEvents
import com.intellij.testFramework.replaceService
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor

@RunWith(Parameterized::class)
@RunsInEdt
class StudioSettingsSyncActionTest(private val enableFsts: Boolean) {
  companion object {
    @JvmStatic @Parameterized.Parameters(name = "enableFsts = {0}") fun flagVals() = listOf(true, false)
  }

  private val applicationRule = ApplicationRule()
  private val disposableRule = DisposableRule()
  private val onboardingSettingsSyncRule = FlagRule(StudioFlags.ENABLE_SETTINGS_SYNC_ONBOARDING_WIZARD, true)
  private val fstsRule = FlagRule(StudioFlags.ENABLE_FSTS, enableFsts)
  private val loginUsersRule = LoginUsersRule()
  private val edtRule = EdtRule()

  @get:Rule
  val rules: RuleChain =
    RuleChain.outerRule(applicationRule)
      .around(onboardingSettingsSyncRule)
      .around(fstsRule)
      .around(disposableRule)
      .around(loginUsersRule)
      .around(edtRule)

  private lateinit var action: StudioSettingsSyncAction
  private lateinit var mockWizardProvider: BackupAndSyncWizardProvider
  private lateinit var mockWizard: BackupAndSyncWizard
  private lateinit var mockShowSettingsUtil: ShowSettingsUtil
  private lateinit var mockEvent: AnActionEvent
  private lateinit var mockProject: Project

  private lateinit var mockDialog: StructuredFlowWizard

  private lateinit var mockFallbackAction: AnAction

  @Before
  fun setUp() {
    mockFallbackAction = mock(AnAction::class.java)
    val mockPresentation = Presentation()
    `when`(mockFallbackAction.templatePresentation).thenReturn(mockPresentation)

    action = StudioSettingsSyncAction(mockFallbackAction)

    mockWizardProvider = mock(BackupAndSyncWizardProvider::class.java)
    mockWizard = mock(BackupAndSyncWizard::class.java)
    mockDialog = mock(StructuredFlowWizard::class.java)
    `when`(mockWizardProvider.create()).thenReturn(mockWizard)
    `when`(mockWizard.createDialog(any())).thenReturn(mockDialog)
    ApplicationManager.getApplication()
      .replaceService(BackupAndSyncWizardProvider::class.java, mockWizardProvider, disposableRule.disposable)

    mockShowSettingsUtil = mock(ShowSettingsUtil::class.java)
    ApplicationManager.getApplication().replaceService(ShowSettingsUtil::class.java, mockShowSettingsUtil, disposableRule.disposable)

    val mockSettings = mock(SettingsSyncSettings::class.java)
    `when`(mockSettings.syncEnabled).thenReturn(false)
    ApplicationManager.getApplication().replaceService(SettingsSyncSettings::class.java, mockSettings, disposableRule.disposable)

    mockEvent = mock(AnActionEvent::class.java)
    mockProject = mock(Project::class.java)
    `when`(mockEvent.project).thenReturn(mockProject)
  }

  @Test
  fun `actionPerformed shows wizard when onboarding wizard enabled and user logged in`() {
    // Simulate user already logged in to Google and authorized for Settings Sync (or FST covering it)
    loginUsersRule.setActiveUser("test@gmail.com")

    action.actionPerformed(mockEvent)

    executeSomeCoroutineTasksAndDispatchAllInvocationEvents()

    val userCaptor = argumentCaptor<PreferredUser>()
    verify(mockWizard).createDialog(userCaptor.capture())
    assertEquals("test@gmail.com", userCaptor.firstValue.email)
    verify(mockDialog).show()
    verify(mockShowSettingsUtil, never()).showSettingsDialog(mockProject, "settings.sync")
    verify(mockFallbackAction, never()).actionPerformed(mockEvent)
  }

  @Test
  fun `actionPerformed shows wizard when onboarding wizard enabled after login`() {
    // Simulate user logged in to Google but NOT yet authorized for Settings Sync.
    // This will force the action to trigger the login flow.
    loginUsersRule.setActiveUser("test@gmail.com", features = emptyList(), loginType = GoogleLoginPluginEvent.LoginType.FEATURE_LOGIN)
    action.actionPerformed(mockEvent)

    executeSomeCoroutineTasksAndDispatchAllInvocationEvents()

    val userCaptor = argumentCaptor<PreferredUser>()
    verify(mockWizard).createDialog(userCaptor.capture())
    assertEquals("test@gmail.com", userCaptor.firstValue.email)
    verify(mockDialog).show()
    verify(mockShowSettingsUtil, never()).showSettingsDialog(mockProject, "settings.sync")
    verify(mockFallbackAction, never()).actionPerformed(mockEvent)
  }

  @Test
  fun `actionPerformed shows settings when onboarding wizard disabled`() {
    StudioFlags.ENABLE_SETTINGS_SYNC_ONBOARDING_WIZARD.override(overrideValue = false)
    loginUsersRule.setActiveUser("test@gmail.com")

    action.actionPerformed(mockEvent)

    verify(mockWizard, never()).createDialog(any())
    verify(mockShowSettingsUtil, never()).showSettingsDialog(mockProject, "settings.sync")
    verify(mockFallbackAction).actionPerformed(mockEvent)
  }

  @Test
  fun `actionPerformed shows settings when no user logged in`() {
    action.actionPerformed(mockEvent)

    verify(mockWizard, never()).createDialog(any())
    verify(mockShowSettingsUtil, never()).showSettingsDialog(mockProject, "settings.sync")
    verify(mockFallbackAction).actionPerformed(mockEvent)
  }

  @Test
  fun `actionPerformed shows settings when sync enabled`() {
    val mockSettings = SettingsSyncSettings.getInstance()
    `when`(mockSettings.syncEnabled).thenReturn(true)
    loginUsersRule.setActiveUser("test@gmail.com")

    action.actionPerformed(mockEvent)

    verify(mockWizard, never()).createDialog(any())
    verify(mockShowSettingsUtil, never()).showSettingsDialog(mockProject, "settings.sync")
    verify(mockFallbackAction).actionPerformed(mockEvent)
  }
}
