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
import com.google.common.util.concurrent.MoreExecutors
import com.google.gct.login2.CredentialedUser
import com.google.gct.login2.GoogleLoginService
import com.google.gct.login2.LoginFeature
import com.google.gct.login2.LoginUsersRule
import com.google.gct.login2.UserInfoEnforcedFeature
import com.intellij.openapi.application.ApplicationManager
import com.intellij.settingsSync.core.SettingsSyncEventListener
import com.intellij.settingsSync.core.SettingsSyncEvents
import com.intellij.settingsSync.core.SettingsSyncLocalSettings
import com.intellij.settingsSync.core.SettingsSyncSettings
import com.intellij.settingsSync.core.SettingsSyncStatusTracker
import com.intellij.settingsSync.core.SyncSettingsEvent
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

private const val TEST_EMAIL = "test_user@gmail.com"
private val USER_INFO = UserInfoEnforcedFeature()

class GoogleLoginStateListenerTest {
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

  private val loginStates = mutableListOf<Boolean>()
  private val syncEvents = mutableListOf<SyncSettingsEvent>()
  private val scope = CoroutineScope(MoreExecutors.directExecutor().asCoroutineDispatcher())

  private lateinit var googleLoginStateListener: GoogleLoginStateListener
  private lateinit var testListener: SettingsSyncEventListener

  @Before
  fun setUp() {
    googleLoginStateListener = GoogleLoginStateListener(scope)
    ApplicationManager.getApplication()
      .replaceService(
        GoogleLoginStateListener::class.java,
        googleLoginStateListener,
        disposableRule.disposable,
      )

    ExtensionTestUtil.maskExtensions(
      LoginFeature.Companion.EP_NAME,
      listOf(feature, USER_INFO),
      disposableRule.disposable,
      false,
    )

    testListener =
      object : SettingsSyncEventListener {
        override fun loginStateChanged() {
          loginStates.add(loginUsersRule.isLoggedIn(TEST_EMAIL, feature))
        }

        override fun settingChanged(event: SyncSettingsEvent) {
          syncEvents.add(event)
        }
      }
    SettingsSyncEvents.getInstance().addListener(testListener)

    // ensure initial state
    SettingsSyncSettings.getInstance().syncEnabled = true
    SettingsSyncLocalSettings.getInstance().userId = TEST_EMAIL
    SettingsSyncLocalSettings.getInstance().providerCode = PROVIDER_CODE_GOOGLE
  }

  @After
  fun tearDown() {
    SettingsSyncEvents.getInstance().removeListener(testListener)
  }

  @Test
  fun `login state changes of the sync user are picked up`() = runTest {
    // Prepare
    googleLoginStateListener.startListening()

    // Actions
    loginUsersRule.setActiveUser(TEST_EMAIL, features = listOf(feature))
    loginUsersRule.logOut(TEST_EMAIL)

    // Verify
    assertThat(loginStates).containsExactly(false, true, false)
    assertThat(syncEvents)
      .containsExactly(
        SyncSettingsEvent.SyncRequest,
        SyncSettingsEvent.SyncRequest,
        SyncSettingsEvent.SyncRequest,
      )
  }

  @Test
  fun `login state changes of the sync user are picked up, deduplicated`() = runTest {
    // Prepare
    googleLoginStateListener.startListening()

    // Actions
    loginUsersRule.setActiveUser(TEST_EMAIL, features = listOf(feature))

    loginUsersRule.setActiveUser(TEST_EMAIL, features = listOf(feature, USER_INFO))

    loginUsersRule.logOut(TEST_EMAIL)

    // Verify
    assertThat(loginStates).containsExactly(false, true, false)
    assertThat(syncEvents)
      .containsExactly(
        SyncSettingsEvent.SyncRequest,
        SyncSettingsEvent.SyncRequest,
        SyncSettingsEvent.SyncRequest,
      )
  }

  @Test
  fun `no login state changes are picked up because it's not the sync user`() = runTest {
    // Prepare
    googleLoginStateListener.startListening()

    // Actions
    loginUsersRule.setActiveUser("foo1@bar.com", features = listOf(feature))

    loginUsersRule.logOut("foo1@bar.com")

    // Verify
    assertThat(loginStates).containsExactly(false)
    assertThat(syncEvents).containsExactly(SyncSettingsEvent.SyncRequest)
  }

  @Test
  fun `only matching login state changes are picked up`() = runTest {
    // Prepare
    googleLoginStateListener.startListening()

    // Actions
    loginUsersRule.setActiveUser("foo1@bar.com", features = listOf(feature))

    loginUsersRule.setActiveUser(TEST_EMAIL, features = listOf(USER_INFO))

    loginUsersRule.setActiveUser(TEST_EMAIL, features = listOf(USER_INFO, feature))

    loginUsersRule.logOut("foo1@bar.com")

    // Verify
    assertThat(loginStates).containsExactly(false, true)
    assertThat(syncEvents)
      .containsExactly(SyncSettingsEvent.SyncRequest, SyncSettingsEvent.SyncRequest)
  }

  @Test
  fun `do not pick up if no active sync user`() = runTest {
    // Prepare
    SettingsSyncSettings.getInstance().syncEnabled = false
    SettingsSyncLocalSettings.getInstance().userId = TEST_EMAIL
    SettingsSyncLocalSettings.getInstance().providerCode = PROVIDER_CODE_GOOGLE
    googleLoginStateListener.startListening()

    // Actions
    loginUsersRule.setActiveUser(TEST_EMAIL, features = listOf(USER_INFO))
    loginUsersRule.setActiveUser(TEST_EMAIL, features = listOf(USER_INFO, feature))

    // Verify
    assertThat(loginStates).isEmpty()
    assertThat(syncEvents).isEmpty()
  }

  @Test
  fun `do not pick up if no google active sync user`() = runTest {
    // Prepare
    SettingsSyncSettings.getInstance().syncEnabled = true
    SettingsSyncLocalSettings.getInstance().userId = TEST_EMAIL
    SettingsSyncLocalSettings.getInstance().providerCode = "jba"
    googleLoginStateListener.startListening()

    // Actions
    loginUsersRule.setActiveUser(TEST_EMAIL, features = listOf(USER_INFO))
    loginUsersRule.setActiveUser(TEST_EMAIL, features = listOf(USER_INFO, feature))

    // Verify
    assertThat(loginStates).isEmpty()
    assertThat(syncEvents).isEmpty()
  }

  @Test
  fun `do not pick up if disabling sync`() = runTest {
    // Prepare
    googleLoginStateListener.startListening()

    // Actions
    loginUsersRule.setActiveUser(TEST_EMAIL, features = listOf(USER_INFO))
    loginUsersRule.setActiveUser(TEST_EMAIL, features = listOf(USER_INFO, feature))

    // Verify
    assertThat(loginStates).containsExactly(false, true)
    assertThat(syncEvents)
      .containsExactly(SyncSettingsEvent.SyncRequest, SyncSettingsEvent.SyncRequest)

    // Action
    SettingsSyncSettings.getInstance().syncEnabled = false
    SettingsSyncLocalSettings.getInstance().userId = TEST_EMAIL
    SettingsSyncLocalSettings.getInstance().providerCode = PROVIDER_CODE_GOOGLE

    loginUsersRule.logOut(TEST_EMAIL)

    // Verify
    assertThat(loginStates).containsExactly(false, true)
    assertThat(syncEvents)
      .containsExactly(SyncSettingsEvent.SyncRequest, SyncSettingsEvent.SyncRequest)
  }

  @Test
  fun `can clear required auth action by logging-in`() = runTest {
    // Prepare
    googleLoginStateListener.startListening()

    SettingsSyncStatusTracker.getInstance().setActionRequired(
      "Authorization Required",
      LOGIN_ACTION_DESCRIPTION,
    ) {}

    assertThat(SettingsSyncStatusTracker.getInstance().currentStatus)
      .isInstanceOf(SettingsSyncStatusTracker.SyncStatus.ActionRequired::class.java)

    // Actions
    loginUsersRule.setActiveUser(TEST_EMAIL, features = listOf(feature))

    // Verify
    assertThat(loginStates).containsExactly(false, true)
    assertThat(syncEvents)
      .containsExactly(SyncSettingsEvent.SyncRequest, SyncSettingsEvent.SyncRequest)
    assertThat(SettingsSyncStatusTracker.getInstance().currentStatus)
      .isInstanceOf(SettingsSyncStatusTracker.SyncStatus.Success::class.java)
  }
}

// Special test setup for testing out specific code. For general testing purpose, please expand
// GoogleLoginStateListenerTest above.
class GoogleLoginStateListenerSimpleTest() {
  private val applicationRule = ApplicationRule()
  private val flagRule = FlagRule(StudioFlags.SETTINGS_SYNC_ENABLED, true)
  private val disposableRule = DisposableRule()

  @get:Rule
  val rules: RuleChain =
    RuleChain.outerRule(applicationRule).around(flagRule).around(disposableRule)

  private val loginStates = mutableListOf<Boolean>()
  private val syncEvents = mutableListOf<SyncSettingsEvent>()
  private val scope = CoroutineScope(MoreExecutors.directExecutor().asCoroutineDispatcher())

  private var _isInitialized = false
  private val _allUsersFlow = MutableStateFlow<Map<String, CredentialedUser>>(emptyMap())

  private lateinit var googleLoginStateListener: GoogleLoginStateListener
  private lateinit var testListener: SettingsSyncEventListener

  @Before
  fun setUp() {
    val loginService =
      object : GoogleLoginService by mock(GoogleLoginService::class.java) {
        override val isInitialized: Boolean
          get() = _isInitialized

        override val allUsersFlow: StateFlow<Map<String, CredentialedUser>>
          get() = _allUsersFlow
      }

    ApplicationManager.getApplication()
      .replaceService(GoogleLoginService::class.java, loginService, disposableRule.disposable)

    googleLoginStateListener = GoogleLoginStateListener(scope)
    ApplicationManager.getApplication()
      .replaceService(
        GoogleLoginStateListener::class.java,
        googleLoginStateListener,
        disposableRule.disposable,
      )

    ExtensionTestUtil.maskExtensions(
      LoginFeature.Companion.EP_NAME,
      listOf(feature, USER_INFO),
      disposableRule.disposable,
      false,
    )

    testListener =
      object : SettingsSyncEventListener {
        override fun loginStateChanged() {
          loginStates.add(
            loginService.allUsersFlow.value
              .filterKeys { it == TEST_EMAIL }
              .any { it.value.isLoggedIn(feature) }
          )
        }

        override fun settingChanged(event: SyncSettingsEvent) {
          syncEvents.add(event)
        }
      }
    SettingsSyncEvents.getInstance().addListener(testListener)

    // ensure initial state
    SettingsSyncSettings.getInstance().syncEnabled = true
    SettingsSyncLocalSettings.getInstance().userId = TEST_EMAIL
    SettingsSyncLocalSettings.getInstance().providerCode = PROVIDER_CODE_GOOGLE
  }

  @After
  fun tearDown() {
    SettingsSyncEvents.getInstance().removeListener(testListener)
  }

  @Test
  fun `pick up login event on startup`() = runTest {
    googleLoginStateListener.startListening()
    _allUsersFlow.emit(
      mapOf<String, CredentialedUser>(
        TEST_EMAIL to
          mock<CredentialedUser>().apply { `when`(isLoggedIn(feature)).thenReturn(true) }
      )
    )

    _isInitialized = true

    // Verify
    assertThat(loginStates).containsExactly(false, true)
  }
}
