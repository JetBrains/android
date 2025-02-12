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
import com.google.api.client.auth.oauth2.Credential
import com.google.gct.login2.LoginFeature
import com.intellij.openapi.diagnostic.Logger
import com.intellij.settingsSync.core.AbstractServerCommunicator
import com.intellij.settingsSync.core.SettingsSyncRemoteCommunicator
import com.intellij.settingsSync.core.SettingsSyncSettings
import com.intellij.settingsSync.core.auth.SettingsSyncAuthService
import com.intellij.settingsSync.core.communicator.SettingsSyncCommunicatorProvider
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicReference

internal const val PROVIDER_CODE_GOOGLE = "google"
internal const val PROVIDER_NAME_GOOGLE = "Google"

class GoogleCommunicatorProvider : SettingsSyncCommunicatorProvider {
  override val authService: SettingsSyncAuthService = GoogleAuthService()

  override val providerCode: String = PROVIDER_CODE_GOOGLE

  override fun createCommunicator(userId: String): SettingsSyncRemoteCommunicator {
    return GoogleCloudServerCommunicator(userId)
  }

  override fun isAvailable(): Boolean {
    return StudioFlags.SETTINGS_SYNC_ENABLED.get()
  }
}

private class UnauthorizedException(message: String) : RuntimeException(message)

private val log
  get() = Logger.getInstance(GoogleCloudServerCommunicator::class.java)

class GoogleCloudServerCommunicator(private val email: String) : AbstractServerCommunicator() {
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

  override fun handleRemoteError(e: Throwable): String {
    // Logic is mostly copied from IJ implementation.
    val defaultMessage = "Error during communication with server"
    if (e is IOException) {
      if (lastRemoteErrorRef.get()?.message != e.message) {
        lastRemoteErrorRef.set(e)
        log.warn("$defaultMessage: ${e.message}")
      }
    } else if (e is UnauthorizedException) {
      // TODO: we might want to call authService.invalidate(email) or so, but this depends on the
      // overall flow.
      SettingsSyncSettings.getInstance().syncEnabled = false
      log.warn(
        "Got \"Unauthorized\" from Google Drive service. Settings Sync will be disabled. Please log in again."
      )
    } else {
      log.error(e)
    }
    return e.message ?: defaultMessage
  }
}
