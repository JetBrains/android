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

import com.google.gct.login2.CredentialedUser
import com.google.gct.login2.GoogleLoginService
import com.google.gct.login2.LoginFeature
import com.google.gct.login2.PreferredUser
import com.intellij.openapi.diagnostic.Logger
import com.intellij.settingsSync.core.SettingsSyncLocalSettings
import com.intellij.settingsSync.core.SettingsSyncSettings
import com.intellij.settingsSync.core.auth.SettingsSyncAuthService
import com.intellij.settingsSync.core.communicator.SettingsSyncUserData
import icons.StudioIllustrations
import java.awt.Component
import javax.swing.Icon
import javax.swing.JComponent

private val log
  get() = Logger.getInstance(GoogleAuthService::class.java)

class GoogleAuthService : SettingsSyncAuthService {
  private val feature = LoginFeature.feature<SettingsSyncFeature>()

  override val icon: Icon
    get() = StudioIllustrations.Common.GOOGLE_LOGO

  override val providerCode: String
    get() = PROVIDER_CODE_GOOGLE

  override val providerName: String
    get() = PROVIDER_NAME_GOOGLE

  /**
   * Returns a list of [SettingsSyncUserData] which contains
   * 1) logged-in users irrespective of their B&S feature authorization status
   * 2) active sync user irrespective of their B&S feature authorization status
   */
  override fun getAvailableUserAccounts(): List<SettingsSyncUserData> {
    val currentUser =
      getActiveSyncUserEmail()
        .takeIf { SettingsSyncLocalSettings.getInstance().providerCode == PROVIDER_CODE_GOOGLE }
        ?.let { getUserData(it) }

    val allLoggedInUsers =
      GoogleLoginService.instance.allUsersFlow.value.values.map { it.createSettingsSyncUserData() }

    return listOfNotNull(currentUser) + allLoggedInUsers.filterNot { it == currentUser }
  }

  /**
   * Return user data based on [userId].
   *
   * Note there's no credential validation here.
   */
  override fun getUserData(userId: String): SettingsSyncUserData {
    return createSettingsSyncUserData(userId)
  }

  override suspend fun login(parentComponent: Component?): SettingsSyncUserData? {
    return login(preferredUser = PreferredUser.None, parentComponent = parentComponent)
  }

  suspend fun login(
    preferredUser: PreferredUser,
    parentComponent: Component?,
  ): SettingsSyncUserData? {
    val loggedInUser =
      feature.logIn(
        preferredUser = preferredUser,
        switchUserIfApplicable = false,
        parentComponent = parentComponent as? JComponent,
      )

    return loggedInUser.email?.let { getUserData(it) }
  }

  override fun crossSyncSupported(): Boolean = false

  override fun getPendingUserAction(userId: String): SettingsSyncAuthService.PendingUserAction? {
    if (isLoggedIn(userId)) return null

    val user = PreferredUser.User(userId)
    return SettingsSyncAuthService.PendingUserAction(
      message = "Authorization Required",
      actionTitle = "Authorize",
      actionDescription = "give access",
    ) { component ->
      try {
        login(user, parentComponent = component)
      } catch (t: Throwable) {
        log.warn("Failed to authorize $user", t)
      }
    }
  }

  private fun isLoggedIn(userEmail: String): Boolean {
    return feature.isLoggedIn(userEmail)
  }
}

internal fun getActiveSyncUserEmail(): String? {
  return SettingsSyncLocalSettings.getInstance().userId.takeIf {
    SettingsSyncSettings.getInstance().syncEnabled
  }
}

// if we want to make sure what we show is consistent (e.g. name is not available if not logged
// in), we just always pass email info around.
private fun CredentialedUser.createSettingsSyncUserData() =
  SettingsSyncUserData(
    id = email,
    providerCode = PROVIDER_CODE_GOOGLE,
    name = null,
    email = email,
    printableName = null,
  )

private fun createSettingsSyncUserData(email: String) =
  SettingsSyncUserData(
    id = email,
    providerCode = PROVIDER_CODE_GOOGLE,
    name = null,
    email = email,
    printableName = null,
  )
