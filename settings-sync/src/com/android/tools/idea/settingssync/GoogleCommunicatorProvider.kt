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
import com.android.tools.idea.settingssync.onboarding.feature
import com.google.api.client.auth.oauth2.Credential
import com.google.gct.login2.GoogleLoginService
import com.google.gct.login2.LoginFeature
import com.google.gct.login2.PreferredUser
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.settingsSync.core.AbstractServerCommunicator
import com.intellij.settingsSync.core.SettingsSyncEvents
import com.intellij.settingsSync.core.SettingsSyncLocalSettings
import com.intellij.settingsSync.core.SettingsSyncRemoteCommunicator
import com.intellij.settingsSync.core.SettingsSyncStatusTracker
import com.intellij.settingsSync.core.SyncSettingsEvent
import com.intellij.settingsSync.core.communicator.SettingsSyncCommunicatorProvider
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

internal const val PROVIDER_CODE_GOOGLE = "google"
internal const val PROVIDER_NAME_GOOGLE = "Google"
internal const val LOGIN_ACTION_DESCRIPTION = "Log in with Google account"

/**
 * Listens to Google login state changes for the active sync user and reacts accordingly.
 *
 * This class observes the login status of the user currently designated for settings sync. When a
 * change in login state is detected (e.g., user logs in or logs out), it performs several actions:
 * - Fires a `LoginStateChanged` event via [SettingsSyncEvents].
 * - If the user has logged in and there was a pending "login required" action, it clears that
 *   action.
 * - Triggers a new settings sync to ensure the sync status is up-to-date.
 *
 * TODO: once we have disposable available (IJ already made the change in the master branch, we have
 *   to wait for the next merge), we can simplify this.
 */
@Service
class GoogleLoginStateListener(private val coroutineScope: CoroutineScope) {
  private val syncUser: String?
    get() =
      getActiveSyncUserEmail().takeIf {
        SettingsSyncLocalSettings.getInstance().providerCode == PROVIDER_CODE_GOOGLE
      }

  fun startListening() {
    coroutineScope.launch {
      GoogleLoginService.instance.allUsersFlow
        .filterNot { syncUser == null || GoogleLoginService.instance.isInitialized == false }
        .map { it[syncUser]?.isLoggedIn(feature) == true }
        .distinctUntilChanged()
        .collect { isLoggedIn ->
          thisLogger()
            .info("Login status gets changed for $syncUser (login state = $isLoggedIn)...")
          SettingsSyncEvents.getInstance().fireLoginStateChanged()

          // Ideally, we should just fire login state change and the platform will handle the rest
          // naturally. But this is not the case now.
          //
          // Since we should not rely on periodically (1h) sync or "on focus" sync for refreshing
          // sync status, we explicitly clean up some state or trigger sync event here.

          // 1. Clear up the Google login action required previously if applicable.
          if (isLoggedIn) {
            val status = SettingsSyncStatusTracker.getInstance().currentStatus
            if (
              status is SettingsSyncStatusTracker.SyncStatus.ActionRequired &&
                status.actionTitle == LOGIN_ACTION_DESCRIPTION
            ) {
              SettingsSyncStatusTracker.getInstance().clearActionRequired()
            }
          }

          // 2. Trigger sync to make sure the sync status is updated after login state change. Note
          // there could be unfortunately duplicate requests on login settings changes.
          SettingsSyncEvents.getInstance().fireSettingsChanged(SyncSettingsEvent.SyncRequest)
        }
    }
  }
}

private const val BACKUP_AND_SYNC_HELP_URL = "https://d.android.com/r/studio-ui/settings-sync/help"
private val helpLinkPair: Pair<String, String> = Pair("Learn more", BACKUP_AND_SYNC_HELP_URL)

class GoogleCommunicatorProvider : SettingsSyncCommunicatorProvider {
  init {
    service<GoogleLoginStateListener>().startListening()
  }

  override val authService: GoogleAuthService = GoogleAuthService()

  override val providerCode: String = PROVIDER_CODE_GOOGLE

  override fun createCommunicator(userId: String): SettingsSyncRemoteCommunicator {
    return GoogleCloudServerCommunicator(userId, authService)
  }

  override fun isAvailable(): Boolean {
    return StudioFlags.SETTINGS_SYNC_ENABLED.get()
  }

  override val learnMoreLinkPair: Pair<String, String> = helpLinkPair

  override val learnMoreLinkPair2: Pair<String, String> = helpLinkPair
}

private class UnauthorizedException(message: String) : IOException(message)

private val log
  get() = Logger.getInstance(GoogleCloudServerCommunicator::class.java)

class GoogleCloudServerCommunicator(
  private val email: String,
  private val authService: GoogleAuthService,
) : AbstractServerCommunicator() {
  private val lastRemoteErrorRef = AtomicReference<Throwable>()
  private val googleDriveClient = GoogleDriveClient { getCredential(email) }

  private fun getCredential(email: String): Credential {
    // TODO: how the system reacts to logout or token issue needs revisit once JB has new updates.
    return LoginFeature.feature<SettingsSyncFeature>().credential(email)
      ?: throw UnauthorizedException(
        "Missing feature authorization for $email, please log in first."
      )
  }

  override val userId: String = email

  override fun writeFileInternal(
    filePath: String,
    versionId: String?,
    content: InputStream,
  ): String {
    // 1. delete the oldest files on cloud if the total count > 10
    googleDriveClient.deleteOldestFilesOverLimit(filePath)

    // 2. write a new file to cloud
    return googleDriveClient.write(filePath, content).versionId
  }

  override fun readFileInternal(filePath: String): Pair<InputStream?, String?> {
    val (content, driveFileMetadata) = googleDriveClient.read(filePath) ?: return null to null

    return content.inputStream() to driveFileMetadata.versionId
  }

  override fun deleteFileInternal(filePath: String) {
    googleDriveClient.delete(filePath)
  }

  override fun getLatestVersion(filePath: String): String? {
    return googleDriveClient.getLatestUpdatedFileMetadata(filePath)?.versionId
  }

  override fun requestSuccessful() {
    if (lastRemoteErrorRef.get() != null) {
      log.info("Connection to setting sync server is restored")
    }
    lastRemoteErrorRef.set(null)
  }

  override fun handleRemoteError(e: Throwable): String {
    // Logic is mostly copied from IJ implementation.
    val defaultMessage = "Error during communication with server"
    if (e is UnauthorizedException) {
      setAuthActionRequired()
    } else if (e is IOException) {
      if (lastRemoteErrorRef.get()?.message != e.message) {
        lastRemoteErrorRef.set(e)
        log.warn("$defaultMessage: ${e.message}")
      }
    } else {
      log.error(e)
    }
    return e.message ?: defaultMessage
  }

  private fun setAuthActionRequired() {
    val user = PreferredUser.User(email)

    SettingsSyncStatusTracker.getInstance().setActionRequired(
      "Authorization Required",
      LOGIN_ACTION_DESCRIPTION,
    ) { component ->
      try {
        authService.login(user, parentComponent = component)
      } catch (t: Throwable) {
        log.warn("Failed to authorize $user", t)
      }
    }
  }
}
