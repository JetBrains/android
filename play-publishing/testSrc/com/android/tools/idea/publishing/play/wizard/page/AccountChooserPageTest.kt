/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.publishing.play.wizard.page

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.android.flags.junit.FlagRule
import com.android.testutils.waitForCondition
import com.android.tools.adtui.compose.TestComposeWizard
import com.android.tools.adtui.compose.utils.StudioComposeTestRule
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.flags.overrideForTest
import com.google.common.truth.Truth.assertThat
// TODO: android-merge; com.google.gct.login2 is tools/vendor/google/login, which this repository does not carry.
// import com.google.gct.login2.GoogleLoginService
// import com.google.gct.login2.LoginFeatureRule
// import com.google.gct.login2.LoginUsersRule
// import com.google.gct.login2.fstLoginFeature
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import kotlin.time.Duration.Companion.seconds
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

@RunsInEdt
class AccountChooserPageTest {
  private val edtRule = EdtRule()
  private val applicationRule = ApplicationRule()
  private val flagsRule = FlagRule(StudioFlags.ENABLE_FSTS, true)
  private val disposableRule = DisposableRule()
  private val composeTestRule = StudioComposeTestRule.createStudioComposeTestRule()
  // TODO: android-merge; LoginFeatureRule and LoginUsersRule are in tools/vendor/google/login, which this
  // repository does not carry.
  // private val loginFeatureRule = LoginFeatureRule()
  // private val loginUsersRule = LoginUsersRule()

  @get:Rule
  val ruleChain: RuleChain =
    RuleChain.outerRule(edtRule)
      .around(applicationRule)
      .around(flagsRule)
      .around(disposableRule)
      // .around(loginFeatureRule)
      // .around(loginUsersRule)
      .around(composeTestRule)

  @Test
  fun testLoggedOutPageContent() {
    val wizard = TestComposeWizard { AccountChooserPage() }

    composeTestRule.setContent { wizard.Content() }

    composeTestRule.onNodeWithText("Publish your Android app for testing").assertIsDisplayed()
    composeTestRule.onNodeWithText("Publish your application directly to Google Play Store from Android Studio.").assertIsDisplayed()
    composeTestRule.onNodeWithText("In the following steps, you will be guided to:").assertIsDisplayed()
    composeTestRule.onNodeWithText("Sign in and link your Google Play account to Android Studio, if necessary").assertIsDisplayed()
    composeTestRule.onNodeWithText("Upload your Android App Bundle (.aab)").assertIsDisplayed()
    composeTestRule.onNodeWithText("Configure your release").assertIsDisplayed()

    composeTestRule
      .onNodeWithText(
        "You must have a Google Play developer account to publish apps. If you don't have one yet, you can start the registration at Google Play Console",
        substring = true,
      )
      .assertIsDisplayed()

    composeTestRule
      .onNodeWithText("Signing in to Android Studio is required. You will be redirected to the web to sign in as the next step.")
      .assertIsDisplayed()
  }

  // TODO: android-merge; the test sets up a signed in user through LoginUsersRule and LoginFeatureRule from
  // tools/vendor/google/login, which this repository does not carry, and the banner it asserts on is shown
  // only when that login state can be read.
  // @Test
  // fun testLoggedOutPageContentRequiresAuthorization() {
  //   StudioFlags.ENABLE_FSTS.overrideForTest(false, disposableRule.disposable)
  //   loginUsersRule.setActiveUser("user@google.com", features = listOf(loginFeatureRule.ENFORCED))
  //   StudioFlags.ENABLE_FSTS.overrideForTest(true, disposableRule.disposable)
  //
  //   val wizard = TestComposeWizard { AccountChooserPage() }
  //
  //   composeTestRule.setContent { wizard.Content() }
  //
  //   composeTestRule
  //     .onNodeWithText(
  //       "Using this wizard requires new authorization for Android Studio. You will be redirected to the web to sign in as the next step.",
  //       substring = true,
  //     )
  //     .assertIsDisplayed()
  // }

  // TODO: android-merge; both tests drive the next action, which signs in through
  // com.google.gct.login2.fstLoginFeature from tools/vendor/google/login, which this repository does not carry.
  // @Test
  // fun testNextActionWhenLoggedIn() {
  //   loginUsersRule.setActiveUser("user@google.com")
  //
  //   val wizard = TestComposeWizard { AccountChooserPage() }
  //
  //   composeTestRule.setContent { wizard.Content() }
  //
  //   wizard.performAction(wizard.nextAction)
  //   assertThat(wizard.pageStackSize()).isEqualTo(2)
  // }
  //
  // @Test
  // fun testNextActionWhenLoggedOut() {
  //   val wizard = TestComposeWizard { AccountChooserPage() }
  //
  //   composeTestRule.setContent { wizard.Content() }
  //
  //   wizard.performAction(wizard.nextAction)
  //   waitForCondition(1.seconds) { fstLoginFeature.isLoggedIn() }
  //   assertThat(wizard.pageStackSize()).isEqualTo(2)
  // }

  // TODO: android-merge; the account dropdown these three tests drive is shown only when
  // com.google.gct.login2.GoogleLoginService from tools/vendor/google/login reports logged in users, and this
  // repository does not carry it.
  // @Test
  // fun testLoggedInShowsAccountDropdown() {
  //   loginUsersRule.setActiveUser("user1@google.com")
  //   loginUsersRule.setActiveUser("user2@google.com")
  //
  //   val wizard = TestComposeWizard { AccountChooserPage() }
  //
  //   composeTestRule.setContent { wizard.Content() }
  //
  //   // Dropdown and active user are displayed
  //   composeTestRule.onNodeWithText("Google account:").assertIsDisplayed()
  //   composeTestRule.onNodeWithText("user2@google.com").assertIsDisplayed()
  //
  //   // Open dropdown
  //   composeTestRule.onNodeWithText("user2@google.com").performClick()
  //
  //   // Other logged in users and new account option are visible in dropdown menu
  //   composeTestRule.onNodeWithText("user1@google.com").assertIsDisplayed()
  //   composeTestRule.onNodeWithText("Sign in with a new account").assertIsDisplayed()
  // }
  //
  // @Test
  // fun testSwitchAccountUpdatesActiveUser() {
  //   loginUsersRule.setActiveUser("user1@google.com")
  //   loginUsersRule.setActiveUser("user2@google.com")
  //
  //   val wizard = TestComposeWizard { AccountChooserPage() }
  //
  //   composeTestRule.setContent { wizard.Content() }
  //
  //   // Open dropdown and switch to user1@google.com
  //   composeTestRule.onNodeWithText("user2@google.com").performClick()
  //   composeTestRule.onNodeWithText("user1@google.com").performClick()
  //
  //   // Clicking next should set active user to user1@google.com and proceed
  //   wizard.performAction(wizard.nextAction)
  //   assertThat(GoogleLoginService.instance.getEmail()).isEqualTo("user1@google.com")
  //   assertThat(wizard.pageStackSize()).isEqualTo(2)
  // }
  //
  // @Test
  // fun testSelectSignInWithNewAccount() {
  //   loginUsersRule.setActiveUser("user@google.com")
  //
  //   val wizard = TestComposeWizard { AccountChooserPage() }
  //
  //   composeTestRule.setContent { wizard.Content() }
  //
  //   // Open dropdown and select "Sign in with a new account"
  //   composeTestRule.onNodeWithText("user@google.com").performClick()
  //   composeTestRule.onNodeWithText("Sign in with a new account").performClick()
  //
  //   // Banner message should be displayed since we opted to sign in with a new account
  //   composeTestRule
  //     .onNodeWithText("Signing in to Android Studio is required. You will be redirected to the web to sign in as the next step.")
  //     .assertIsDisplayed()
  //
  //   // Trigger next action which should open sign in flow (simulated by logInBlocking)
  //   loginUsersRule.setNextLoginUser("new_user@google.com")
  //   wizard.performAction(wizard.nextAction)
  //   waitForCondition(1.seconds) { GoogleLoginService.instance.getEmail() == "new_user@google.com" }
  //   assertThat(wizard.pageStackSize()).isEqualTo(2)
  // }

  // TODO: android-merge; the banner text depends on the login state read from
  // com.google.gct.login2.GoogleLoginService and fstLoginFeature in tools/vendor/google/login, which this
  // repository does not carry, so only the logged out banner can be shown here.
  // @Test
  // fun testBannerVisibility() {
  //   // 1. User without FST login feature -> banner shown
  //   StudioFlags.ENABLE_FSTS.overrideForTest(false, disposableRule.disposable)
  //   loginUsersRule.setActiveUser("user@google.com", features = listOf(loginFeatureRule.ENFORCED))
  //   StudioFlags.ENABLE_FSTS.overrideForTest(true, disposableRule.disposable)
  //
  //   var wizard = TestComposeWizard { AccountChooserPage() }
  //   composeTestRule.setContent { wizard.Content() }
  //
  //   composeTestRule
  //     .onNodeWithText(
  //       "Using this wizard requires new authorization for Android Studio. You will be redirected to the web to sign in as the next step."
  //     )
  //     .assertIsDisplayed()
  //
  //   // 2. User with FST login feature -> banner hidden
  //   loginUsersRule.setActiveUser("user_with_fst@google.com", features = listOf(loginFeatureRule.ENFORCED, loginFeatureRule.FST))
  //
  //   wizard = TestComposeWizard { AccountChooserPage() }
  //   composeTestRule.setContent { wizard.Content() }
  //
  //   composeTestRule
  //     .onNodeWithText(
  //       "Using this wizard requires new authorization for Android Studio. You will be redirected to the web to sign in as the next step."
  //     )
  //     .assertDoesNotExist()
  //
  //   // 3. Selection of "Sign in with a new account" -> banner shown
  //   composeTestRule.onNodeWithText("user_with_fst@google.com").performClick()
  //   composeTestRule.onNodeWithText("Sign in with a new account").performClick()
  //
  //   composeTestRule
  //     .onNodeWithText("Signing in to Android Studio is required. You will be redirected to the web to sign in as the next step.")
  //     .assertIsDisplayed()
  // }
}
