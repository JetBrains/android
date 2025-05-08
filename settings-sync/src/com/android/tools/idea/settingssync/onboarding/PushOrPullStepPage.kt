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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.google.gct.login2.ui.onboarding.compose.InnerWizardContentPage
import com.google.gct.wizard.WizardDialogController
import com.google.gct.wizard.WizardPage
import com.google.gct.wizard.WizardPageControl
import com.google.gct.wizard.WizardState
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.settingsSync.core.SettingsSyncBundle
import com.intellij.settingsSync.core.SettingsSyncStateHolder
import com.intellij.settingsSync.core.UpdateResult
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import org.jetbrains.jewel.ui.component.ExternalLink
import org.jetbrains.jewel.ui.component.Text

private const val BACKUP_AND_SYNC_LOCATION_URL =
  "https://d.android.com/r/studio-ui/settings-sync/location"

internal class PushOrPullStepPage : WizardPage() {
  override val description: String =
    "Backup & Sync step2: choose to push or pull settings to/from Google Drive."
  override val composableContent: @Composable WizardState.() -> Unit = {
    PushOrPullComposableContent()
  }
  override val controlProvider: (WizardState) -> WizardPageControl = { state ->
    object : WizardPageControl() {
      val configurationState = state.getOrCreateState { SyncConfigurationState() }

      override fun canProceed(): Boolean {
        return configurationState.pushOrPull != PushOrPull.NOT_SPECIFIED
      }

      override fun onProceed(): WizardDialogController.() -> Boolean = {
        val settingsSyncState =
          when (configurationState.pushOrPull) {
            PushOrPull.PULL -> {
              val result: UpdateResult? =
                configurationState.getCloudStatusWithModalProgressBlocking(
                  userEmail =
                    checkNotNull(with(configurationState) { state.getOnboardingUser().email }),
                  // No network call at this point as the result is supposedly already cached.
                  allowFetchIfCacheMiss = false,
                  parentComponent = null,
                )

              (result as? UpdateResult.Success)?.settingsSnapshot?.getState()
                ?: error("Should have valid remote settings sync data available. (current: $result")
            }
            PushOrPull.PUSH,
            PushOrPull.NOT_SPECIFIED -> SettingsSyncStateHolder()
          }

        with(configurationState) { syncCategoryStates = settingsSyncState.mapToUIStates() }

        true
      }

      override fun shouldShow(): Boolean {
        with(configurationState) {
          if (state.canSkipFeatureConfiguration()) return false

          // If the onboarding user email is not available when asked, we just assume it should be
          // shown for the case where no login yet, and we want to hint available feature
          // configurations.
          val onboardingUserEmail = state.getOnboardingUser().email ?: return true

          val cloudStatus: UpdateResult =
            getCloudStatusWithModalProgressBlocking(
              userEmail = onboardingUserEmail,
              allowFetchIfCacheMiss = true,
              parentComponent = null,
            ) ?: error("Should have remote settings sync data available.")

          return when (cloudStatus) {
            UpdateResult.NoFileOnServer,
            UpdateResult.FileDeletedFromServer -> false
            is UpdateResult.Success -> true
            is UpdateResult.Error -> {
              thisLogger()
                .warn(
                  SettingsSyncBundle.message("notification.title.update.error") +
                    "(${cloudStatus.message})"
                )
              false
            }
          }
        }
      }
    }
  }
}

@Composable
internal fun WizardState.PushOrPullComposableContent() {
  val configurationState = getOrCreateState { SyncConfigurationState() }

  InnerWizardContentPage(header = { SyncConfigurationPageTitle() }) {
    Column(Modifier.padding(vertical = 16.dp, horizontal = 32.dp)) {
      Text(
        "There are existing Android Studio settings synced to this Google account." +
          " Please choose which settings you would like to use."
      )

      Spacer(modifier = Modifier.height(16.dp))

      Column(Modifier.padding(start = 16.dp)) {
        // pull
        RadioButtonWithComment(
          annotatedText =
            AnnotatedString.Builder()
              .apply { append("Use the settings from your Google account storage\n") }
              .toAnnotatedString(),
          annotatedComment =
            AnnotatedString.Builder()
              .apply {
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                  append("Last updated: ${extractDateFromCloudRecord()}\n")
                  append("Android Studio version: ${extractAppInfoFromCloudRecord()}\n")
                }
              }
              .toAnnotatedString(),
          selected = configurationState.pushOrPull == PushOrPull.PULL,
          onSelect = { configurationState.pushOrPull = PushOrPull.PULL },
        )

        // push
        RadioButtonWithComment(
          annotatedText =
            AnnotatedString.Builder()
              .apply {
                append("Use the local settings and upload them to your Google account storage\n")
              }
              .toAnnotatedString(),
          annotatedComment =
            AnnotatedString.Builder()
              .apply {
                // TODO: grab date info from the settings folder?
                // withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                //   append("Last updated: ??\n")
                //   append(
                //     "Android Studio version:
                // ${ApplicationInfoEx.getInstanceEx().fullApplicationName}"
                //   )
                // }
              }
              .toAnnotatedString(),
          selected = configurationState.pushOrPull == PushOrPull.PUSH,
          onSelect = { configurationState.pushOrPull = PushOrPull.PUSH },
        )
      }

      Row {
        Text("Your previous settings will be backed up and can be retrieved. ")
        ExternalLink(
          text = "Learn more",
          onClick = { BrowserUtil.browse(BACKUP_AND_SYNC_LOCATION_URL) },
        )
      }
    }
  }
}

private fun WizardState.getCachedServerData(): UpdateResult.Success {
  val configurationState = getOrCreateState { SyncConfigurationState() }
  val onboardingUserEmail: String =
    with(configurationState) {
      getOnboardingUser().email ?: error("Should have non-null email available.")
    }

  return configurationState.cloudStatusCache[onboardingUserEmail] as UpdateResult.Success
}

private fun WizardState.extractDateFromCloudRecord(): String {
  val formatter = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC)
  val instant = getCachedServerData().settingsSnapshot.metaInfo.dateCreated

  return formatter.format(instant)
}

private fun WizardState.extractAppInfoFromCloudRecord(): String? {
  return getCachedServerData().settingsSnapshot.metaInfo.appInfo?.fullApplicationName
}
