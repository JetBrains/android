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
import com.google.gct.login2.LoginFeature
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class SettingsSyncFeature : LoginFeature {
  override val name: String = NAME

  override val description: String =
    "Sync your settings to Google Drive to keep them across computers and re-installs."

  override val infoUrl: String? = null

  override val infoUrlDisplayText: String? = null

  override val settingsAction: AnAction =
    object : AnAction("Go to Backup and Sync") {
      override fun actionPerformed(e: AnActionEvent) {
        // TODO: uncomment below once we have it.
        // ShowSettingsUtil.getInstance().showSettingsDialog(e.project,
        // SettingsSyncBundle.message("title.settings.sync"))
      }
    }

  override val oAuthScopes: Collection<String> =
    setOf("https://www.googleapis.com/auth/drive.appdata")

  override val isAvailable: Boolean = StudioFlags.SETTINGS_SYNC_ENABLED.get()

  override val onboardingWizardEntry: LoginFeature.OnboardingWizardEntry =
    LoginFeature.OnboardingWizardEntry(
      icon = AllIcons.Providers.GoogleCloudSpanner, // TODO: update this once we have it.
      name = "<b>Google Drive:</b> Enable Synced Settings", // TODO: update wording
      description =
        "Settings Sync backs up your IDE settings to Google Drives and restores them " +
          "across your workstations so that your Android Studio experience is just the way you like it.", // TODO: update wording
    )

  companion object {
    internal const val NAME = "Google Settings Sync"
  }
}
