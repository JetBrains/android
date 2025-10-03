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

import com.google.gct.login2.PreferredUser
import com.google.gct.login2.ui.onboarding.compose.GoogleSignInWizard.SignInState
import com.google.gct.wizard.StructuredFlowWizard
import com.google.gct.wizard.WizardState
import com.google.wireless.android.sdk.stats.GoogleLoginPluginEvent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service

/**
 * Standalone Backup and Sync feature's setup wizard.
 *
 * This class is responsible for assembling the necessary wizard pages and initial state for the
 * feature onboarding flow. It is specifically designed to be invoked when a user authorizes an
 * account from the Google accounts settings page, providing a streamlined experience where the user
 * is already known.
 */
interface BackupAndSyncWizard {
  fun createDialog(user: PreferredUser): StructuredFlowWizard
}

@Service
class BackupAndSyncWizardProvider {
  fun create(): BackupAndSyncWizard = BackupAndSyncWizardImpl()

  companion object {
    fun create(): BackupAndSyncWizard = service<BackupAndSyncWizardProvider>().create()
  }
}

private class BackupAndSyncWizardImpl : BackupAndSyncWizard {
  override fun createDialog(user: PreferredUser): StructuredFlowWizard {
    val state =
      WizardState().apply {
        getOrCreateState { SignInState() }
          .apply {
            requiredIntegrations.clear()
            requiredIntegrations.add(feature)
            signedInUser = user
            loginType = GoogleLoginPluginEvent.LoginType.FEATURE_LOGIN
          }
      }
    return StructuredFlowWizard(
      project = null,
      title = "Backup and Sync Wizard",
      wizardState = state,
      wizardPages = feature.allOnboardingPages(),
    )
  }
}
