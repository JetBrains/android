/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.settingssync.onboarding.BackupAndSyncWizardProvider
import com.android.tools.idea.settingssync.onboarding.feature
import com.google.gct.login2.GoogleLoginService
import com.google.gct.login2.PreferredUser
import com.intellij.ide.actions.SettingsEntryPointAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.settingsSync.core.SettingsSyncSettings

/**
 * An action that wraps the native IntelliJ Settings Sync action to inject the custom Android Studio onboarding wizard.
 *
 * If the onboarding wizard is disabled, settings sync is already set up, or the user is logged out, it falls back to the default IntelliJ
 * behavior.
 */
@Suppress("UnstableApiUsage")
class StudioSettingsSyncAction(private val delegate: AnAction) : DumbAwareAction(), SettingsEntryPointAction.NoDots {

  init {
    // Copy over the presentation (text, icon, description, etc.) from the upstream action
    // we're wrapping, so it matches the platform's expectations (e.g. "Backup and Sync").
    templatePresentation.copyFrom(delegate.templatePresentation)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val service = GoogleLoginService.instance
    val activeUserEmail = service.getEmail()
    if (
      activeUserEmail == null || !StudioFlags.ENABLE_SETTINGS_SYNC_ONBOARDING_WIZARD.get() || SettingsSyncSettings.getInstance().syncEnabled
    ) {
      fallback(e)
      return
    }

    if (feature.isLoggedIn()) {
      showWizard(activeUserEmail)
      return
    }

    // No user is authorized for this feature. Trigger a blocking login flow.
    // The onboarding wizard will be shown automatically as a post-login action.
    feature.logInBlocking()
  }

  override fun update(e: AnActionEvent) {
    delegate.update(e)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return delegate.actionUpdateThread
  }

  private fun showWizard(email: String) {
    runInEdt { BackupAndSyncWizardProvider.create().createDialog(PreferredUser.User(email)).show() }
  }

  private fun fallback(e: AnActionEvent) {
    delegate.actionPerformed(e)
  }
}
