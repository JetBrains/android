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

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.android.tools.adtui.compose.LocalProject
import com.android.tools.adtui.compose.TestComposeWizard
import com.android.tools.adtui.compose.utils.StudioComposeTestRule
import com.android.tools.idea.publishing.play.client.FakePlayPublishingClient
import com.android.tools.idea.publishing.play.client.type.AppConfig
import com.android.tools.idea.publishing.play.client.type.AppType
import com.android.tools.idea.publishing.play.client.type.Developer
import com.android.tools.idea.publishing.play.wizard.PlayPublishingWizardState
import com.google.common.truth.Truth.assertThat
// TODO: android-merge; com.google.gct.login2 is tools/vendor/google/login, which this repository does not carry.
// import com.google.gct.login2.LoginFeatureRule
// import com.google.gct.login2.LoginUsersRule
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import kotlinx.coroutines.CompletableDeferred
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

@RunsInEdt
class CreateAppRecordPageTest {
  private val edtRule = EdtRule()
  private val applicationRule = ApplicationRule()
  private val disposableRule = DisposableRule()
  private val composeTestRule = StudioComposeTestRule.createStudioComposeTestRule()
  // TODO: android-merge; LoginFeatureRule and LoginUsersRule are in tools/vendor/google/login, which this
  // repository does not carry. The page under test reads its data from the injected PlayPublishingClient,
  // so the tests below run without them.
  // private val loginFeatureRule = LoginFeatureRule()
  // private val loginUsersRule = LoginUsersRule()

  @get:Rule
  val ruleChain: RuleChain =
    RuleChain.outerRule(edtRule)
      .around(applicationRule)
      .around(disposableRule)
      // .around(loginFeatureRule)
      // .around(loginUsersRule)
      .around(composeTestRule)

  private lateinit var fakeClient: FakePlayPublishingClient

  @Before
  fun setUp() {
    // TODO: android-merge; LoginUsersRule is in tools/vendor/google/login, which this repository does not carry.
    // loginUsersRule.setActiveUser("user@example.com")
    fakeClient = FakePlayPublishingClient()
  }

  @Test
  fun testInitialStateLoading() {
    fakeClient.config = FakePlayPublishingClient.Config(listDeveloperCall = { CompletableDeferred<List<Developer>>().await() })
    createWizard()

    composeTestRule.onNodeWithText("Publish your Android app for testing").assertIsDisplayed()
    composeTestRule.onNodeWithText("Create new app").assertIsDisplayed()
    composeTestRule.onNodeWithText("Loading accounts...").assertIsDisplayed()
  }

  @Test
  fun testAccountSelection() {
    val developers = listOf(Developer(1, "Account 1"), Developer(2, "Account 2"))
    fakeClient.config = FakePlayPublishingClient.Config(listDeveloperCall = { developers })
    createWizard()

    composeTestRule.onNodeWithText("Account 1").assertIsDisplayed()

    // Open dropdown
    composeTestRule.onNodeWithText("Account 1").performClick()
    composeTestRule.onNodeWithText("Account 2").performClick()

    composeTestRule.onNodeWithText("Account 2").assertIsDisplayed()
  }

  @Test
  fun testNoAccounts() {
    fakeClient.config = FakePlayPublishingClient.Config(listDeveloperCall = { emptyList() })
    createWizard()

    composeTestRule.onNodeWithText("No developer accounts found.").assertIsDisplayed()
    composeTestRule.onNodeWithText("Next").assertIsNotEnabled()
  }

  @Test
  fun testAppNameField() {
    fakeClient.config = FakePlayPublishingClient.Config(listDeveloperCall = { listOf(Developer(1, "Account 1")) })
    createWizard()

    composeTestRule.onNodeWithText("App name:").assertIsDisplayed()
    composeTestRule.onNodeWithText("30 characters remaining").assertIsDisplayed()

    val textField = composeTestRule.onNodeWithText("")
    textField.performTextInput("My Awesome App")

    composeTestRule.onNodeWithText("16 characters remaining").assertIsDisplayed()
  }

  @Test
  fun testCharLimit() {
    fakeClient.config = FakePlayPublishingClient.Config(listDeveloperCall = { listOf(Developer(1, "Account 1")) })
    createWizard()

    val textField = composeTestRule.onNodeWithText("")
    textField.performTextInput("A".repeat(30))

    composeTestRule.onNodeWithText("0 characters remaining").assertIsDisplayed()
  }

  @Test
  fun testLanguageSelection() {
    fakeClient.config = FakePlayPublishingClient.Config(listDeveloperCall = { listOf(Developer(1, "Account 1")) })
    createWizard()

    composeTestRule.onNodeWithText("English (United States) - en-US").assertIsDisplayed()

    // Open dropdown
    composeTestRule.onNodeWithText("English (United States) - en-US").performClick()
    composeTestRule.onNodeWithText("French (France) - fr-FR").performClick()

    composeTestRule.onNodeWithText("French (France) - fr-FR").assertIsDisplayed()
  }

  @Test
  fun testNextButtonEnabled() {
    fakeClient.config = FakePlayPublishingClient.Config(listDeveloperCall = { listOf(Developer(1, "Account 1")) })
    val state = PlayPublishingWizardState(packageName = "com.example.app", client = fakeClient)
    createWizard(state)

    // Next should be enabled as we have an account selected by default (first one)
    composeTestRule.onNodeWithText("Next").assertIsEnabled()
  }

  @Test
  fun testNextButtonAction() {
    val dev = Developer(1, "Account 1")
    val createAppRecordCalls = mutableListOf<Pair<Long, AppConfig>>()
    fakeClient.config =
      FakePlayPublishingClient.Config(
        listDeveloperCall = { listOf(dev) },
        createAppRecordCall = { id, cfg ->
          createAppRecordCalls.add(id to cfg)
          AppConfig(
            packageName = "com.example.app",
            title = "App",
            defaultLanguageCode = "en-US",
            appType = AppType.APP_TYPE_APP,
            paid = false,
          )
        },
      )
    val state = PlayPublishingWizardState(packageName = "com.example.app", client = fakeClient)
    createWizard(state)

    composeTestRule.onNodeWithText("Next").performClick()

    composeTestRule.waitForIdle()

    // Wait for the mock to be called (since it's in a background Task)
    composeTestRule.waitUntil(5000) { createAppRecordCalls.isNotEmpty() }

    val calledWith = createAppRecordCalls.first()
    assertThat(calledWith.first).isEqualTo(1L)
    assertThat(calledWith.second.packageName).isEqualTo("com.example.app")
    assertThat(state.isAppCreated).isTrue()
    assertThat(state.releaseName).isEqualTo("First Release")
  }

  @Test
  fun testErrorLoadingDevelopers() {
    fakeClient.config = FakePlayPublishingClient.Config(listDeveloperCall = { throw Exception("Network error") })
    createWizard()

    composeTestRule.onNodeWithText("Failed to load developers: Network error").assertIsDisplayed()
  }

  @OptIn(ExperimentalTestApi::class)
  @Test
  fun testCreateAppError() {
    val dev = Developer(1, "Account 1")
    fakeClient.config =
      FakePlayPublishingClient.Config(
        listDeveloperCall = { listOf(dev) },
        createAppRecordCall = { _, _ -> throw Exception("Creation failed") },
      )

    val state = PlayPublishingWizardState(packageName = "com.example.app", client = fakeClient)
    createWizard(state)

    composeTestRule.onNodeWithText("Next").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.waitUntilExactlyOneExists(hasText("Creation failed"), 2000)
  }

  @Test
  fun testPackageNameUnavailable() {
    val dev = Developer(1, "Account 1")
    fakeClient.config =
      FakePlayPublishingClient.Config(
        listDeveloperCall = { listOf(dev) },
        createAppRecordCall = { _, _ -> throw Exception("The package name com.example.app is not available on Play") },
      )

    val state = PlayPublishingWizardState(packageName = "com.example.app", client = fakeClient)
    createWizard(state)

    composeTestRule.onNodeWithText("Next").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.waitUntil(5000) {
      composeTestRule
        .onAllNodes(hasText("The package name com.example.app is not available on Play", substring = true))
        .fetchSemanticsNodes()
        .isNotEmpty()
    }

    composeTestRule.onNodeWithText("Next").assertIsNotEnabled()
  }

  private fun createWizard(state: PlayPublishingWizardState = PlayPublishingWizardState(client = fakeClient)): TestComposeWizard {
    val wizard = TestComposeWizard {
      getOrCreateState { state }
      CreateAppRecordPage()
    }
    composeTestRule.setContent { CompositionLocalProvider(LocalProject provides null) { wizard.Content() } }
    return wizard
  }
}
