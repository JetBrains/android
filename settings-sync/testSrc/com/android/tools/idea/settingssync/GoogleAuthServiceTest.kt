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

import com.android.flags.junit.FlagRule
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.settingssync.onboarding.feature
import com.google.common.truth.Truth.assertThat
import com.google.gct.login2.LoginFeature
import com.google.gct.login2.LoginUsersRule
import com.intellij.settingsSync.core.SettingsSyncLocalSettings
import com.intellij.settingsSync.core.SettingsSyncSettings
import com.intellij.settingsSync.core.communicator.SettingsSyncUserData
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ExtensionTestUtil
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

private const val TEST_EMAIL = "test_user@gmail.com"

class GoogleAuthServiceTest {
  private val applicationRule = ApplicationRule()
  private val flagRule = FlagRule(StudioFlags.SETTINGS_SYNC_ENABLED, true)
  private val disposableRule = DisposableRule()
  private val loginUsersRule = LoginUsersRule()

  @get:Rule
  val rules: RuleChain =
    RuleChain.outerRule(applicationRule)
      .around(flagRule)
      .around(disposableRule)
      .around(loginUsersRule)

  @Before
  fun setUp() {
    ExtensionTestUtil.maskExtensions(
      LoginFeature.Companion.EP_NAME,
      listOf(feature),
      disposableRule.disposable,
      false,
    )
  }

  @Test
  fun `get available user accounts, list single active sync user who is logged in`() {
    // Prepare
    SettingsSyncSettings.getInstance().syncEnabled = true
    SettingsSyncLocalSettings.getInstance().userId = TEST_EMAIL
    SettingsSyncLocalSettings.getInstance().providerCode = PROVIDER_CODE_GOOGLE
    loginUsersRule.setActiveUser(TEST_EMAIL, features = listOf(feature))

    // Action
    val users = GoogleAuthService().getAvailableUserAccounts()

    // Verify
    assertThat(users)
      .containsExactly(
        SettingsSyncUserData(
          id = TEST_EMAIL,
          providerCode = PROVIDER_CODE_GOOGLE,
          name = null,
          email = TEST_EMAIL,
          printableName = null,
        )
      )
  }

  @Test
  fun `get available user accounts, list single inactive sync user who is logged in`() {
    // Prepare
    SettingsSyncSettings.getInstance().syncEnabled = false
    SettingsSyncLocalSettings.getInstance().userId = TEST_EMAIL
    SettingsSyncLocalSettings.getInstance().providerCode = PROVIDER_CODE_GOOGLE
    loginUsersRule.setActiveUser(TEST_EMAIL, features = listOf(feature))

    // Action
    val users = GoogleAuthService().getAvailableUserAccounts()

    // Verify
    assertThat(users)
      .containsExactly(
        SettingsSyncUserData(
          id = TEST_EMAIL,
          providerCode = PROVIDER_CODE_GOOGLE,
          name = null,
          email = TEST_EMAIL,
          printableName = null,
        )
      )
  }

  @Test
  fun `get available user accounts, list single active sync user who is logged out`() {
    // Prepare
    SettingsSyncSettings.getInstance().syncEnabled = true
    SettingsSyncLocalSettings.getInstance().userId = TEST_EMAIL
    SettingsSyncLocalSettings.getInstance().providerCode = PROVIDER_CODE_GOOGLE

    // Action
    val users = GoogleAuthService().getAvailableUserAccounts()

    // Verify
    assertThat(users)
      .containsExactly(
        SettingsSyncUserData(
          id = TEST_EMAIL,
          providerCode = PROVIDER_CODE_GOOGLE,
          name = null,
          email = TEST_EMAIL,
          printableName = null,
        )
      )
  }

  @Test
  fun `get available user accounts, list all feature logged-in users`() {
    // Prepare
    SettingsSyncSettings.getInstance().syncEnabled = false
    SettingsSyncLocalSettings.getInstance().userId = null
    SettingsSyncLocalSettings.getInstance().providerCode = null
    loginUsersRule.setActiveUser(TEST_EMAIL, features = listOf(feature))

    // Action
    val users = GoogleAuthService().getAvailableUserAccounts()

    // Verify
    assertThat(users)
      .containsExactly(
        SettingsSyncUserData(
          id = TEST_EMAIL,
          providerCode = PROVIDER_CODE_GOOGLE,
          name = null,
          email = TEST_EMAIL,
          printableName = null,
        )
      )
  }

  @Test
  fun `get available user accounts, list all feature logged-in users and sync user`() {
    // Prepare
    SettingsSyncSettings.getInstance().syncEnabled = true
    SettingsSyncLocalSettings.getInstance().userId = "foo@bar.com"
    SettingsSyncLocalSettings.getInstance().providerCode = PROVIDER_CODE_GOOGLE
    loginUsersRule.setActiveUser(TEST_EMAIL, features = listOf(feature))

    // Action
    val users = GoogleAuthService().getAvailableUserAccounts()

    // Verify
    assertThat(users)
      .containsExactly(
        SettingsSyncUserData(
          id = "foo@bar.com",
          providerCode = PROVIDER_CODE_GOOGLE,
          name = null,
          email = "foo@bar.com",
          printableName = null,
        ),
        SettingsSyncUserData(
          id = TEST_EMAIL,
          providerCode = PROVIDER_CODE_GOOGLE,
          name = null,
          email = TEST_EMAIL,
          printableName = null,
        ),
      )
  }

  @Test
  fun `get available user accounts, empty users`() {
    // Prepare
    SettingsSyncSettings.getInstance().syncEnabled = false
    SettingsSyncLocalSettings.getInstance().userId = null
    SettingsSyncLocalSettings.getInstance().providerCode = null

    // Action
    val users = GoogleAuthService().getAvailableUserAccounts()

    // Verify
    assertThat(users).isEmpty()
  }

  @Test
  fun `get available user accounts, do not show saved sync user from other non-google provider`() {
    // Prepare
    SettingsSyncSettings.getInstance().syncEnabled = true
    SettingsSyncLocalSettings.getInstance().userId = "jba"
    SettingsSyncLocalSettings.getInstance().providerCode = "jba"

    // Action
    val users = GoogleAuthService().getAvailableUserAccounts()

    // Verify
    assertThat(users).isEmpty()
  }
}
