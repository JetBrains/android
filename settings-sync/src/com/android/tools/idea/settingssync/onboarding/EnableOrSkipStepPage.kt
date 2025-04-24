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
package com.android.tools.idea.settingssync.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.google.gct.login2.PreferredUser
import com.google.gct.login2.ui.onboarding.compose.GoogleSignInWizard.SignInState
import com.google.gct.login2.ui.onboarding.compose.InnerWizardContentPage
import com.google.gct.wizard.WizardPage
import com.google.gct.wizard.WizardPageControl
import com.google.gct.wizard.WizardState
import org.jetbrains.jewel.ui.component.Text

internal class EnableOrSkipStepPage : WizardPage() {
  override val description: String =
    "Backup & Sync step1: enable or skip if the just logged-in user has already configured the feature."
  override val composableContent: @Composable WizardState.() -> Unit = {
    EnableOrSkipComposableContent()
  }
  override val controlProvider: (WizardState) -> WizardPageControl = { state ->
    object : WizardPageControl() {
      override fun shouldShow(): Boolean {
        val configurationState = state.getOrCreateState { SyncConfigurationState() }

        with(configurationState) {
          if (state.canSkipFeatureConfiguration()) return false
          return configurationState.activeSyncUser != null
        }
      }
    }
  }
}

@Composable
internal fun WizardState.EnableOrSkipComposableContent() {
  val configurationState = getOrCreateState { SyncConfigurationState() }
  val signInState = getOrCreateState { SignInState() }

  // Fetching and caching data as soon as the user makes a selection is more time-efficient than
  // waiting to fetch only when "Next" is clicked (if the data isn't already cached).
  LaunchedEffect(configurationState.configurationOption) {
    if (configurationState.configurationOption != SyncConfigurationOption.CONFIGURE_NEW_ACCOUNT) {
      return@LaunchedEffect
    }

    with(configurationState) {
      val userEmail = checkNotNull(getOnboardingUser().email)
      getCloudStatus(userEmail, allowFetchIfCacheMiss = true)
    }
  }

  InnerWizardContentPage(syncConfigurationPageTitle) {
    Column(Modifier.padding(vertical = 16.dp, horizontal = 32.dp)) {
      Text(
        "Switch Backup & Sync to ${PreferredUser.ActiveUser.email}?",
        fontWeight = FontWeight.Bold,
      )
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        "There is another account that is currently syncing settings on this installation of Android Studio." +
          " Android Studio can only sync settings with one signed in account at a time."
      )

      Spacer(modifier = Modifier.height(8.dp))

      Column(Modifier.padding(start = 16.dp)) {
        RadioButtonWithComment(
          annotatedText =
            AnnotatedString.Builder()
              .apply {
                append("Sync my settings to this account")
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                  append(" (${signInState.signedInUser.email})")
                }
                append(" and stop syncing settings to the previously signed-in account")
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                  append(" (${configurationState.activeSyncUser})")
                }
                append(" .")
              }
              .toAnnotatedString(),
          comment =
            "Android Studio will start to sync your settings to this account." +
              " The previously signed in account that has Settings Sync enabled will have the sync disabled.",
          selected =
            configurationState.configurationOption == SyncConfigurationOption.CONFIGURE_NEW_ACCOUNT,
          onSelect = {
            configurationState.configurationOption = SyncConfigurationOption.CONFIGURE_NEW_ACCOUNT
          },
        )

        Spacer(modifier = Modifier.height(8.dp))

        RadioButtonWithComment(
          annotatedText =
            AnnotatedString.Builder()
              .apply { append("Do not enable Backup & Sync for this account.") }
              .toAnnotatedString(),
          comment =
            "Android Studio will not sync your settings to this account and leave the current state unchanged.",
          selected =
            configurationState.configurationOption == SyncConfigurationOption.USE_EXISTING_SETTINGS,
          onSelect = {
            configurationState.configurationOption = SyncConfigurationOption.USE_EXISTING_SETTINGS
          },
        )
      }
    }
  }
}
