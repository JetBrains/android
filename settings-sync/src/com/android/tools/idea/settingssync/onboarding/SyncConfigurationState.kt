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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.state.ToggleableState
import com.android.tools.idea.settingssync.PROVIDER_CODE_GOOGLE
import com.android.tools.idea.settingssync.SettingsSyncFeature
import com.android.tools.idea.settingssync.onboarding.Category.Companion.DESCRIPTORS
import com.google.gct.login2.LoginFeature
import com.google.gct.login2.PreferredUser
import com.google.gct.login2.ui.onboarding.compose.GoogleSignInWizard
import com.google.gct.wizard.WizardState
import com.google.gct.wizard.WizardStateElement
import com.intellij.openapi.diagnostic.Logger
import com.intellij.settingsSync.core.SettingsSyncLocalSettings
import com.intellij.settingsSync.core.SettingsSyncSettings
import com.intellij.settingsSync.core.SettingsSyncState
import com.intellij.settingsSync.core.SettingsSyncStateHolder
import com.intellij.settingsSync.core.UpdateResult
import com.intellij.settingsSync.core.communicator.RemoteCommunicatorHolder
import com.intellij.settingsSync.core.config.SettingsSyncEnabler

internal val feature = LoginFeature.feature<SettingsSyncFeature>()

internal enum class PushOrPull {
  /** push local settings to remote */
  PUSH,
  /** pull remote settings to local */
  PULL,
  NOT_SPECIFIED,
}

internal enum class SyncConfigurationOption {
  CONFIGURE_NEW_ACCOUNT,
  USE_EXISTING_SETTINGS,
}

private val LOG = Logger.getInstance(SyncConfigurationState::class.java)

internal class SyncConfigurationState : WizardStateElement, SettingsSyncEnabler.Listener {
  private val syncEnabler = SettingsSyncEnabler()
  val activeSyncUser =
    SettingsSyncLocalSettings.getInstance().userId.takeIf {
      SettingsSyncSettings.getInstance().syncEnabled
    }

  init {
    syncEnabler.addListener(this)
  }

  // mutable states
  var pushOrPull: PushOrPull by mutableStateOf(PushOrPull.NOT_SPECIFIED)
  var configurationOption: SyncConfigurationOption by
    mutableStateOf(SyncConfigurationOption.CONFIGURE_NEW_ACCOUNT)
  val syncCategoryStates = DESCRIPTORS.map { it.toCheckboxNode(SettingsSyncStateHolder()) }

  // Returns the [PreferredUser] for the following configuration based on the selected
  // [configurationOption].
  private fun getOnboardingUser(stateContext: WizardState): PreferredUser =
    when (configurationOption) {
      SyncConfigurationOption.CONFIGURE_NEW_ACCOUNT -> {
        stateContext.getOrCreateState { GoogleSignInWizard.SignInState() }.signedInUser
      }
      SyncConfigurationOption.USE_EXISTING_SETTINGS -> {
        PreferredUser.User(email = activeSyncUser ?: error("Should not reach this state."))
      }
    }

  fun WizardState.canSkipFeatureConfiguration(): Boolean {
    val signInState = getOrCreateState { GoogleSignInWizard.SignInState() }

    if (!signInState.requiredIntegrations.contains(feature)) return true
    if (activeSyncUser != null && activeSyncUser == getOnboardingUser(this).email) return true

    return false
  }

  override fun WizardState.handleFinished() {
    if (canSkipFeatureConfiguration()) return
    if (configurationOption == SyncConfigurationOption.USE_EXISTING_SETTINGS) return

    // Update the following to ensure that we have a right communicator set up for the following
    // sync via [syncEnabler]. TODO: confirm with JB the right flow to invalidate communicator.
    RemoteCommunicatorHolder.invalidateCommunicator()
    SettingsSyncLocalSettings.getInstance().userId = getOnboardingUser(stateContext = this).email
    SettingsSyncLocalSettings.getInstance().providerCode = PROVIDER_CODE_GOOGLE

    // Sync...
    when (pushOrPull) {
      PushOrPull.PUSH,
      PushOrPull.NOT_SPECIFIED -> {
        LOG.info("Push local settings to cloud...")

        SettingsSyncSettings.getInstance()
          .applyFromState(syncCategoryStates.toSettingsSyncState(syncEnabled = true))

        // Note it's OK if anything gets wrong as we will push again when there's a chance.
        syncEnabler.pushSettingsToServer()
      }
      PushOrPull.PULL -> {
        LOG.info("Pull cloud settings per request from cloud and apply to local...")
        // TODO:
      }
    }
  }

  override fun updateFromServerFinished(result: UpdateResult) {
    // TODO
  }
}

internal fun List<CheckboxNode>.toSettingsSyncState(syncEnabled: Boolean): SettingsSyncState {
  val settingsSyncState = SettingsSyncStateHolder()

  forEach { nodeState ->
    val parent = DESCRIPTORS.single { it.name == nodeState.id }

    // set parent category
    settingsSyncState.setCategoryEnabled(
      parent.category,
      nodeState.isCheckedState != ToggleableState.Off,
    )

    // set children categories
    nodeState.children.mapNotNull { nodeState ->
      val descriptor =
        parent.secondaryGroup?.getDescriptors()?.single { it.id == nodeState.id }
          ?: return@mapNotNull null
      settingsSyncState.setSubcategoryEnabled(
        parent.category,
        descriptor.id,
        nodeState.isCheckedState == ToggleableState.On,
      )
    }
  }

  return settingsSyncState.apply { this.syncEnabled = syncEnabled }
}
