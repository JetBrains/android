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
import com.android.flags.junit.FlagRule
import com.android.testutils.waitForCondition
import com.android.tools.adtui.compose.TestComposeWizard
import com.android.tools.adtui.compose.utils.StudioComposeTestRule
import com.android.tools.idea.flags.StudioFlags
import com.google.common.truth.Truth.assertThat
// TODO: android-merge; com.google.gct.login2 is tools/vendor/google/login, which this repository does not carry.
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
class LoggedOutPageTest {
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
    val wizard = TestComposeWizard { LoggedOutPage() }

    composeTestRule.setContent { wizard.Content() }

    composeTestRule.onNodeWithText("Publish your Android app for testing").assertIsDisplayed()
    composeTestRule.onNodeWithText("Publish your application directly to Google Play Store from Android Studio.").assertIsDisplayed()
    composeTestRule.onNodeWithText("In the following steps, you will be guided to:").assertIsDisplayed()
    composeTestRule.onNodeWithText("Sign in and link your Google Play account to Android Studio, if necessary").assertIsDisplayed()
    composeTestRule.onNodeWithText("Upload your Android App Bundle (.aab) or APK").assertIsDisplayed()
    composeTestRule.onNodeWithText("Configure your release").assertIsDisplayed()

    composeTestRule
      .onNodeWithText(
        "You must have a Google Play developer account to publish apps. If you don't have one yet, you can start the registration at Google Play Console",
        substring = true,
      )
      .assertIsDisplayed()

    composeTestRule
      .onNodeWithText(
        "Using this wizard requires signing into Android Studio. You will be redirected to the web to sign in at the next step."
      )
      .assertIsDisplayed()
  }

  // TODO: android-merge; both tests drive the next action, which signs in through
  // com.google.gct.login2.fstLoginFeature from tools/vendor/google/login, which this repository does not carry.
  // @Test
  // fun testNextActionWhenLoggedIn() {
  //   loginUsersRule.setActiveUser("user@example.com")
  //
  //   val wizard = TestComposeWizard { LoggedOutPage() }
  //
  //   composeTestRule.setContent { wizard.Content() }
  //
  //   wizard.performAction(wizard.nextAction)
  //   assertThat(wizard.pageStackSize()).isEqualTo(2)
  // }
  //
  // @Test
  // fun testNextActionWhenLoggedOut() {
  //   val wizard = TestComposeWizard { LoggedOutPage() }
  //
  //   composeTestRule.setContent { wizard.Content() }
  //
  //   wizard.performAction(wizard.nextAction)
  //   waitForCondition(1.seconds) { fstLoginFeature.isLoggedIn() }
  //   assertThat(wizard.pageStackSize()).isEqualTo(2)
  // }
}
