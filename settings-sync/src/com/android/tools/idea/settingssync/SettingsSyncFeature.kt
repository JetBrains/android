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

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.settingssync.onboarding.ChooseCategoriesStepPage
import com.android.tools.idea.settingssync.onboarding.EnableOrSkipStepPage
import com.android.tools.idea.settingssync.onboarding.PushOrPullStepPage
import com.google.gct.login2.LoginFeature
import com.google.gct.login2.OAuthScope
import com.google.gct.wizard.WizardPage
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ex.Settings
import icons.StudioIllustrations
import icons.StudioIllustrationsCompose
import javax.swing.Icon
import org.jetbrains.jewel.ui.icon.IconKey

class SettingsSyncFeature : LoginFeature {
  override val key: String = "Backup and Sync"
  override val title: String = "Backup and Sync"

  override val description: String =
    "Sync your settings to your Google Account to keep them across computers and re-installs."

  override val infoUrl: String? = null

  override val infoUrlDisplayText: String? = null

  override val settingsAction: AnAction =
    object : AnAction("Configure Backup and Sync") {
      override fun actionPerformed(e: AnActionEvent) {
        val settings = e.getData(Settings.KEY)
        settings?.select(settings.find("settings.sync"))
      }
    }

  override val oAuthScopes: Collection<OAuthScope> = setOf(OAuthScope.DriveAppData)

  override val isAvailable: Boolean = StudioFlags.SETTINGS_SYNC_ENABLED.get()

  override val onboardingWizardEntry: LoginFeature.OnboardingWizardEntry =
    object : LoginFeature.OnboardingWizardEntry {
      override val icon: Icon = StudioIllustrations.Common.GOOGLE_LOGO
      override val composeIconKey: IconKey = StudioIllustrationsCompose.Common.GoogleLogo
      override val title: String = "<b>Google Account Storage:</b> Enable Backup and Sync"
      override val annotatedTitle: AnnotatedString = buildAnnotatedString {
        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
          append("Google Account Storage:")
        }
        append(" Enable Backup and Sync")
      }
      override val description: String =
        "Backup and Sync backs up your IDE settings to your Google Account and restores them " +
          "to other workstations signed into the same account so that your Android Studio " +
          "experience is just the way you like it."

      override fun getPages(): List<WizardPage> {
        return listOf(EnableOrSkipStepPage(), PushOrPullStepPage(), ChooseCategoriesStepPage())
      }
    }
}
