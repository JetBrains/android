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
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.application.ApplicationManager
import com.intellij.settingsSync.core.SettingsSyncSettings
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.replaceService
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.Mockito.never
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class StudioSettingsSyncInitializerTest {
  private val applicationRule = ApplicationRule()
  private val disposableRule = DisposableRule()
  private val flagRule = FlagRule(StudioFlags.SETTINGS_SYNC_ENABLED, true)
  @get:Rule val rules: RuleChain = RuleChain.outerRule(applicationRule).around(flagRule).around(disposableRule)

  private lateinit var provider: StudioSettingsSyncInitializer
  private lateinit var mockActionManager: ActionManager
  private lateinit var mockOriginalOpenAction: AnAction
  private lateinit var mockOriginalStatusAction: AnAction

  @Before
  fun setUp() {
    provider = StudioSettingsSyncInitializer()
    mockActionManager = mock()
    ApplicationManager.getApplication().replaceService(ActionManager::class.java, mockActionManager, disposableRule.disposable)

    mockOriginalOpenAction = mock()
    mockOriginalStatusAction = mock()
    whenever(mockActionManager.getAction("SettingsSyncOpenSettingsAction")).thenReturn(mockOriginalOpenAction)
    whenever(mockActionManager.getAction("SettingsSyncStatusAction")).thenReturn(mockOriginalStatusAction)

    val mockPresentation = Presentation()
    whenever(mockOriginalOpenAction.templatePresentation).thenReturn(mockPresentation)
    whenever(mockOriginalStatusAction.templatePresentation).thenReturn(mockPresentation)
  }

  @Test
  fun `appFrameCreated replaces actions with StudioSettingsSyncAction when settings sync enabled`() {
    provider.appFrameCreated(emptyList())

    val openActionCaptor = argumentCaptor<AnAction>()
    verify(mockActionManager).replaceAction(eq("SettingsSyncOpenSettingsAction"), openActionCaptor.capture())
    assertTrue(openActionCaptor.firstValue is StudioSettingsSyncAction)

    val statusActionCaptor = argumentCaptor<AnAction>()
    verify(mockActionManager).replaceAction(eq("SettingsSyncStatusAction"), statusActionCaptor.capture())
    assertTrue(statusActionCaptor.firstValue is StudioSettingsSyncAction)
  }

  @Test
  fun `appFrameCreated unregisters actions when settings sync disabled`() {
    StudioFlags.SETTINGS_SYNC_ENABLED.override(false)

    provider.appFrameCreated(emptyList())

    verify(mockActionManager).unregisterAction("SettingsSyncOpenSettingsAction")
    verify(mockActionManager).unregisterAction("SettingsSyncStatusAction")
    verify(mockActionManager, never()).replaceAction(any(), any())
  }

  @Test
  fun `StudioSettingsSyncAction delegates fallbackToSettings to delegate actionPerformed`() {
    val mockDelegate = mock<AnAction>()
    val mockPresentation = Presentation()
    whenever(mockDelegate.templatePresentation).thenReturn(mockPresentation)

    val action = StudioSettingsSyncAction(mockDelegate)
    val mockEvent = mock<AnActionEvent>()

    // Trigger action when SettingsSync is enabled (forces fallbackToSettings)
    val mockSettings = mock<SettingsSyncSettings>()
    whenever(mockSettings.syncEnabled).thenReturn(true)
    ApplicationManager.getApplication().replaceService(SettingsSyncSettings::class.java, mockSettings, disposableRule.disposable)

    action.actionPerformed(mockEvent)

    verify(mockDelegate).actionPerformed(mockEvent)
  }
}
