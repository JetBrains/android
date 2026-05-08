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
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.android.tools.adtui.compose.LocalProject
import com.android.tools.adtui.compose.TestComposeWizard
import com.android.tools.adtui.compose.utils.StudioComposeTestRule
import com.android.tools.idea.publishing.play.AppMetadata
import com.android.tools.idea.publishing.play.wizard.PlayPublishingWizardState
// TODO: android-merge; com.google.gct.login2 is tools/vendor/google/login, which this repository does not carry.
// import com.google.gct.login2.LoginFeatureRule
// import com.google.gct.login2.LoginUsersRule
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

@RunsInEdt
class ChooseArtifactPageTest {
  private val edtRule = EdtRule()
  private val applicationRule = ApplicationRule()
  private val disposableRule = DisposableRule()
  private val composeTestRule = StudioComposeTestRule.createStudioComposeTestRule()
  // TODO: android-merge; LoginFeatureRule and LoginUsersRule are in tools/vendor/google/login, which this
  // repository does not carry. The page under test reads its data from the injected metadata extractor, so
  // the tests below run without them.
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

  @Before
  fun setUp() {
    // TODO: android-merge; LoginUsersRule is in tools/vendor/google/login, which this repository does not carry.
    // loginUsersRule.setActiveUser("user@example.com")
  }

  @Test
  fun testDefaultState() {
    createWizard { AppMetadata("Fake App", "com.fake.app", "123", "1.2.3") }

    // Header
    composeTestRule.onNodeWithText("Upload to Play").assertIsDisplayed()
    composeTestRule.onNodeWithText("Choose App Bundle or APK").assertIsDisplayed()

    // User info
    // TODO: android-merge; the signed in account row needs tools/vendor/google/login, which this repository
    // does not carry, so the page under test does not render it.
    // composeTestRule.onNodeWithText("Signed in as: ").assertIsDisplayed()
    // composeTestRule.onNodeWithText("user@example.com").assertIsDisplayed()

    // Info banner should be displayed
    composeTestRule.onNodeWithText("Path pre-filled from the 'Generate Signed App Bundle or APK' wizard.").assertIsDisplayed()

    // Text field label
    composeTestRule.onNodeWithText("App bundle or APK:").assertIsDisplayed()

    // Test package name
    composeTestRule.onNodeWithTag("PackageNameRow").assert(hasAnyChild(hasText("Package name")) and hasAnyChild(hasText("com.fake.app")))

    // Test version name
    composeTestRule.onNodeWithTag("VersionNameRow").assert(hasAnyChild(hasText("Version name")) and hasAnyChild(hasText("1.2.3")))

    // Test version code
    composeTestRule.onNodeWithTag("VersionCodeRow").assert(hasAnyChild(hasText("Version code")) and hasAnyChild(hasText("123")))

    // Buttons
    composeTestRule.onNodeWithText("Next").assertIsEnabled()
    composeTestRule.onNodeWithText("Previous").assertIsNotEnabled()
  }

  @Test
  fun testPackageNameNull() {
    createWizard { AppMetadata("Fake App", null, "123", "1.2.3") }

    // Test package name
    composeTestRule.onNodeWithTag("PackageNameRow").assert(hasAnyChild(hasText("Package name")) and hasAnyChild(hasText("—")))

    composeTestRule.onNodeWithText("Next").assertIsNotEnabled()
    composeTestRule.onNodeWithText("Previous").assertIsNotEnabled()
  }

  @Test
  fun testPackageNameEmpty() {
    createWizard { AppMetadata("Fake App", "", "123", "1.2.3") }

    // Test package name
    composeTestRule.onNodeWithTag("PackageNameRow").assert(hasAnyChild(hasText("Package name")) and hasAnyChild(hasText("—")))

    composeTestRule.onNodeWithText("Next").assertIsNotEnabled()
    composeTestRule.onNodeWithText("Previous").assertIsNotEnabled()
  }

  @Test
  fun testAppNameNull() {
    createWizard { AppMetadata(null, "com.fake.app", "123", "1.2.3") }

    composeTestRule.onNodeWithText("Next").assertIsNotEnabled()
    composeTestRule.onNodeWithText("Previous").assertIsNotEnabled()
  }

  @Test
  fun testAppNameEmpty() {
    createWizard { AppMetadata("", "com.fake.app", "123", "1.2.3") }

    composeTestRule.onNodeWithText("Next").assertIsNotEnabled()
    composeTestRule.onNodeWithText("Previous").assertIsNotEnabled()
  }

  @Test
  fun testVersionCodeNull() {
    createWizard { AppMetadata("Fake App", "com.fake.app", null, "1.2.3") }

    // Test version code
    composeTestRule.onNodeWithTag("VersionCodeRow").assert(hasAnyChild(hasText("Version code")) and hasAnyChild(hasText("—")))

    composeTestRule.onNodeWithText("Next").assertIsEnabled()
    composeTestRule.onNodeWithText("Previous").assertIsNotEnabled()
  }

  @Test
  fun testVersionCodeEmpty() {
    createWizard { AppMetadata("Fake App", "com.fake.app", "", "1.2.3") }

    // Test version code
    composeTestRule.onNodeWithTag("VersionCodeRow").assert(hasAnyChild(hasText("Version code")) and hasAnyChild(hasText("—")))

    composeTestRule.onNodeWithText("Next").assertIsEnabled()
    composeTestRule.onNodeWithText("Previous").assertIsNotEnabled()
  }

  @Test
  fun testVersionNameNull() {
    createWizard { AppMetadata("Fake App", "com.fake.app", "123", null) }

    // Test version name
    composeTestRule.onNodeWithTag("VersionNameRow").assert(hasAnyChild(hasText("Version name")) and hasAnyChild(hasText("—")))

    composeTestRule.onNodeWithText("Next").assertIsEnabled()
    composeTestRule.onNodeWithText("Previous").assertIsNotEnabled()
  }

  @Test
  fun testVersionNameEmpty() {
    createWizard { AppMetadata("Fake App", "com.fake.app", "123", "") }

    // Test version name
    composeTestRule.onNodeWithTag("VersionNameRow").assert(hasAnyChild(hasText("Version name")) and hasAnyChild(hasText("—")))

    composeTestRule.onNodeWithText("Next").assertIsEnabled()
    composeTestRule.onNodeWithText("Previous").assertIsNotEnabled()
  }

  @Test
  fun testExtractMetadataThrows() {
    createWizard { throw Exception("Failed to extract metadata") }

    // Test package name
    composeTestRule.onNodeWithTag("PackageNameRow").assert(hasAnyChild(hasText("Package name")) and hasAnyChild(hasText("—")))

    // Test version name
    composeTestRule.onNodeWithTag("VersionNameRow").assert(hasAnyChild(hasText("Version name")) and hasAnyChild(hasText("—")))

    // Test version code
    composeTestRule.onNodeWithTag("VersionCodeRow").assert(hasAnyChild(hasText("Version code")) and hasAnyChild(hasText("—")))

    composeTestRule.onNodeWithText("Next").assertIsNotEnabled()
    composeTestRule.onNodeWithText("Previous").assertIsNotEnabled()
  }

  private fun createWizard(appMetadata: () -> AppMetadata): TestComposeWizard {
    val wizard = TestComposeWizard {
      getOrCreateState { PlayPublishingWizardState(artifactPath = "/some/fake/path") }
      ChooseArtifactPage { appMetadata() }
    }
    composeTestRule.setContent { CompositionLocalProvider(LocalProject provides null) { wizard.Content() } }
    return wizard
  }
}
