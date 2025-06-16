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
import com.android.tools.idea.settingssync.SyncEventsMetrics
import com.android.tools.idea.settingssync.getActiveSyncUserEmail
import com.android.tools.idea.settingssync.onboarding.Category.Companion.DESCRIPTORS
import com.google.gct.login2.LoginFeature
import com.google.gct.login2.PreferredUser
import com.google.gct.login2.ui.onboarding.compose.GoogleSignInWizard
import com.google.gct.wizard.HandleFinishedTask
import com.google.gct.wizard.WizardState
import com.google.gct.wizard.WizardStateElement
import com.google.wireless.android.sdk.stats.BackupAndSyncEvent
import com.google.wireless.android.sdk.stats.GoogleLoginPluginEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.Logger
import com.intellij.settingsSync.core.SettingsSyncBundle
import com.intellij.settingsSync.core.SettingsSyncLocalSettings
import com.intellij.settingsSync.core.SettingsSyncSettings
import com.intellij.settingsSync.core.SettingsSyncState
import com.intellij.settingsSync.core.SettingsSyncStateHolder
import com.intellij.settingsSync.core.UpdateResult
import com.intellij.settingsSync.core.communicator.RemoteCommunicatorHolder
import com.intellij.settingsSync.core.config.SettingsSyncEnabler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.IOException

internal val feature
  get() = LoginFeature.feature<SettingsSyncFeature>()

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

  // "Update from server" only covers getting remote data, for the following git operation and
  // applying settings
  // to the IDE - they are done in async way and the lifecycle is controlled outside the dialog.
  private val updateFromServerCompleter = CompletableDeferred<UpdateResult?>(null)

  val activeSyncUser: String? = getActiveSyncUserEmail()

  /**
   * In-memory cache holding the results of the latest cloud status fetch operations.
   *
   * The map uses the user's email address as the key. The value is the `UpdateResult`, representing
   * the state of the remote copy associated with that email address.
   */
  val cloudStatusCache: MutableMap<String, UpdateResult> = mutableMapOf()

  init {
    syncEnabler.addListener(this)
  }

  // mutable states
  var pushOrPull: PushOrPull by mutableStateOf(PushOrPull.NOT_SPECIFIED)
  var configurationOption: SyncConfigurationOption by
    mutableStateOf(SyncConfigurationOption.CONFIGURE_NEW_ACCOUNT)
  var syncCategoryStates = SettingsSyncStateHolder().mapToUIStates()

  // Returns the [PreferredUser] for the following configuration based on the selected
  // [configurationOption].
  fun WizardState.getOnboardingUser(): PreferredUser =
    when (configurationOption) {
      SyncConfigurationOption.CONFIGURE_NEW_ACCOUNT -> {
        getOrCreateState { GoogleSignInWizard.SignInState() }.signedInUser
      }
      SyncConfigurationOption.USE_EXISTING_SETTINGS -> {
        PreferredUser.User(email = activeSyncUser ?: error("Should not reach this state."))
      }
    }

  fun SettingsSyncState.mapToUIStates() = DESCRIPTORS.map { it.toCheckboxNode(this) }

  fun WizardState.canSkipFeatureConfiguration(): Boolean {
    val signInState = getOrCreateState { GoogleSignInWizard.SignInState() }

    if (!signInState.requiredIntegrations.contains(feature)) return true
    if (activeSyncUser != null && activeSyncUser == getOnboardingUser().email) return true

    return false
  }

  /**
   * Gets a user's cloud sync status, prioritizing a valid result from the local cache.
   *
   * On a cache miss (or if the cached result is an error), this function triggers a network request
   * to fetch the latest status, but only if [allowFetchIfCacheMiss] is `true`.
   */
  suspend fun getCloudStatus(userEmail: String, allowFetchIfCacheMiss: Boolean): UpdateResult? {
    val cached = cloudStatusCache[userEmail]
    if (cached != null && cached !is UpdateResult.Error) return cached

    if (allowFetchIfCacheMiss) {
      cloudStatusCache[userEmail] = checkCloudUpdates(userEmail, PROVIDER_CODE_GOOGLE)
    }

    return cloudStatusCache[userEmail]
  }

  override val handleFinishedTask: HandleFinishedTask =
    HandleFinishedTask(description = feature.title) {
      // Exit early if no action is needed
      if (
        canSkipFeatureConfiguration() ||
          configurationOption == SyncConfigurationOption.USE_EXISTING_SETTINGS
      ) {
        LOG.info("Skipping feature configuration as per user choice.")
        return@HandleFinishedTask
      }

      // Setup Phase: Configure local settings before sync
      RemoteCommunicatorHolder.invalidateCommunicator()
      SettingsSyncLocalSettings.getInstance().apply {
        userId = getOnboardingUser().email
        providerCode = PROVIDER_CODE_GOOGLE
      }

      // Sync Phase: Push local settings or pull from cloud
      when (pushOrPull) {
        PushOrPull.PUSH,
        PushOrPull.NOT_SPECIFIED -> handlePush()
        PushOrPull.PULL -> handlePull()
      }

      collectMetrics()
    }

  /** Handles the "push" flow. */
  private suspend fun handlePush() {
    LOG.info("Pushing local settings to cloud...")

    SettingsSyncSettings.getInstance().apply {
      syncEnabled = true
      applyFromState(syncCategoryStates.toSettingsSyncState(syncEnabled = true))
    }

    return withContext(Dispatchers.IO) {
      // Under the hood is currently a fire-and-forget operation (it has its own lifecycle
      // management) - It won't block the wizard from finishing, so we just always return
      // success. Even it fails eventually, the current behavior is that there's next
      // "auto-sync" for recovering.
      syncEnabler.pushSettingsToServer()
    }
  }

  /** Handles the "pull" flow. */
  private suspend fun handlePull() {
    LOG.info("Pulling cloud settings and applying to local...")

    // Under the hood, it triggers the dialog and that the dialog's logic
    // will complete the 'updateFromServerCompleter' deferred. Note that
    // the whole application work is not fully done when the dialog is closed,
    // the remaining work is launched ("fired and forget") and the life cycle is
    // not controlled by us.
    withContext(Dispatchers.EDT) {
      syncEnabler.getSettingsFromServer(syncCategoryStates.toSettingsSyncState(syncEnabled = true))
    }

    updateFromServerCompleter.await()
  }

  private fun WizardState.collectMetrics() {
    SyncEventsMetrics.getInstance()
      .trackEvent(
        BackupAndSyncEvent.newBuilder().apply {
          enablementFlow =
            when (getOrCreateState { GoogleSignInWizard.SignInState() }.loginType) {
              GoogleLoginPluginEvent.LoginType.COMBINED_LOGIN -> {
                BackupAndSyncEvent.EnablementFlow.UNIFIED_SIGN_IN_FLOW
              }
              GoogleLoginPluginEvent.LoginType.FEATURE_LOGIN -> {
                BackupAndSyncEvent.EnablementFlow.ACCOUNT_SETTINGS_PAGE
              }
              else -> {
                BackupAndSyncEvent.EnablementFlow.UNKNOWN_FLOW
              }
            }
        }
      )
  }

  override fun updateFromServerFinished(result: UpdateResult) {
    when (result) {
      is UpdateResult.Success -> {
        SettingsSyncSettings.getInstance().syncEnabled = true
        updateFromServerCompleter.complete(result)
      }
      UpdateResult.NoFileOnServer,
      UpdateResult.FileDeletedFromServer -> {
        updateFromServerCompleter.completeExceptionally(
          IOException(
            SettingsSyncBundle.message("notification.title.update.error") +
              SettingsSyncBundle.message("notification.title.update.no.such.file")
          )
        )
      }
      is UpdateResult.Error -> {
        updateFromServerCompleter.completeExceptionally(
          IOException(
            SettingsSyncBundle.message("notification.title.update.error") + result.message
          )
        )
      }
    }
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
