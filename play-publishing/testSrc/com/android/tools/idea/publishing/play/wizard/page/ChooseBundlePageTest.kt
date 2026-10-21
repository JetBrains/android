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
import com.android.tools.idea.publishing.AdiClient
import com.android.tools.idea.publishing.RegistrationState
import com.android.tools.idea.publishing.play.AppMetadata
import com.android.tools.idea.publishing.play.client.FakePlayPublishingClient
import com.android.tools.idea.publishing.play.client.PlayPublishingClient
import com.android.tools.idea.publishing.play.client.type.App
import com.android.tools.idea.publishing.play.wizard.PlayPublishingWizardState
import com.google.common.truth.Truth.assertThat
// AND-142 JetBrains patch (Google Play Publishing): com.google.gct.login2 is tools/vendor/google/login, which this repository does not carry.
// import com.google.gct.login2.LoginFeatureRule
// import com.google.gct.login2.LoginUsersRule
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import com.intellij.util.application
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.wheneverBlocking

@RunsInEdt
class ChooseBundlePageTest {
  private val edtRule = EdtRule()
  private val projectRule = ProjectRule()
  private val disposableRule = DisposableRule()
  private val composeTestRule = StudioComposeTestRule.createStudioComposeTestRule()
  // AND-142 JetBrains patch (Google Play Publishing): LoginFeatureRule and LoginUsersRule are in tools/vendor/google/login, which this
  // repository does not carry. The page under test reads its data from the injected metadata extractor and
  // from the fake client, so the tests below run without them.
  // private val loginFeatureRule = LoginFeatureRule()
  // private val loginUsersRule = LoginUsersRule()
  private lateinit var fakeClient: FakePlayPublishingClient

  @get:Rule
  val ruleChain: RuleChain =
    RuleChain.outerRule(edtRule)
      .around(projectRule)
      .around(disposableRule)
      // .around(loginFeatureRule)
      // .around(loginUsersRule)
      .around(composeTestRule)

  @Before
  fun setUp() {
    // AND-142 JetBrains patch (Google Play Publishing): LoginUsersRule is in tools/vendor/google/login, which this repository does not carry.
    // loginUsersRule.setActiveUser("user@example.com")
    fakeClient = FakePlayPublishingClient()
    application.replaceService(PlayPublishingClient::class.java, fakeClient, disposableRule.disposable)
  }

  @Test
  fun testDefaultState() {
    createWizard { AppMetadata("Fake App", "com.fake.app", "123", "1.2.3") }

    // Header
    composeTestRule.onNodeWithText("Publish your Android app for testing").assertIsDisplayed()
    composeTestRule.onNodeWithText("Choose App Bundle").assertIsDisplayed()

    // User info
    // AND-142 JetBrains patch (Google Play Publishing): the signed in account row needs tools/vendor/google/login, which this repository
    // does not carry, so the page under test does not render it.
    // composeTestRule.onNodeWithText("Signed in as: ").assertIsDisplayed()
    // composeTestRule.onNodeWithText("user@example.com").assertIsDisplayed()

    // Info banner should be displayed
    composeTestRule.onNodeWithText("Field pre-filled from the 'Generate Signed App Bundle or APK' wizard.").assertIsDisplayed()

    // Text field label
    composeTestRule.onNodeWithText("App bundle:").assertIsDisplayed()

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

    composeTestRule.onNodeWithText("Failed to parse metadata", substring = true).assertIsDisplayed()
    composeTestRule.onNodeWithText("Next").assertIsNotEnabled()
    composeTestRule.onNodeWithText("Previous").assertIsNotEnabled()
  }

  @Test
  fun testAppInConsoleShowsNextActionAsCreateRelease() {
    fakeClient.config =
      FakePlayPublishingClient.Config(listAppsCall = { listOf(App(packageName = "com.fake.app", displayName = "Fake App")) })
    val state = PlayPublishingWizardState(bundlePath = "/some/fake/path", isRegistered = true)
    val wizard = createWizard(state) { AppMetadata("Fake App", "com.fake.app", "123", "1.2.3") }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithText("Matches existing app").assertIsDisplayed()
    composeTestRule.onNodeWithText("Next").assertIsEnabled()

    wizard.performAction(wizard.nextAction)
    // Page stack size goes from 1 to 2
    assertThat(wizard.pageStackSize()).isEqualTo(2)
    composeTestRule.onNodeWithText("Create release").assertIsDisplayed()
  }

  @Test
  fun testAppNotInConsoleShowsNextActionAsCreateAppRecord() {
    fakeClient.config = FakePlayPublishingClient.Config(listAppsCall = { emptyList() })
    // isRegistered is false (meaning they are in the console but don't have this app, or haven't registered)
    val state = PlayPublishingWizardState(bundlePath = "/some/fake/path", isRegistered = false)
    val wizard = createWizard(state) { AppMetadata("Fake App", "com.fake.app", "123", "1.2.3") }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithText("Package name available").assertIsDisplayed()
    composeTestRule.onNodeWithText("Next").assertIsEnabled()

    wizard.performAction(wizard.nextAction)
    assertThat(wizard.pageStackSize()).isEqualTo(2)
    composeTestRule.onNodeWithText("Create new app").assertIsDisplayed()
  }

  @Test
  fun testPackageNameNotAvailableShowsErrorBanner() {
    fakeClient.config = FakePlayPublishingClient.Config(listAppsCall = { emptyList() })
    // isRegistered is true, but app not in console -> error banner
    val state = PlayPublishingWizardState(bundlePath = "/some/fake/path", isRegistered = true)
    createWizard(state) { AppMetadata("Fake App", "com.fake.app", "123", "1.2.3") }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithText("The package name (com.fake.app) is not available", substring = true).assertIsDisplayed()
    composeTestRule.onNodeWithText("Next").assertIsNotEnabled()
  }

  @Test
  fun testFailedToLoadAppsShowsErrorBanner() {
    fakeClient.config = FakePlayPublishingClient.Config(listAppsCall = { throw Exception("Network failure") })
    val state = PlayPublishingWizardState(bundlePath = "/some/fake/path", isRegistered = true)
    createWizard(state) { AppMetadata("Fake App", "com.fake.app", "123", "1.2.3") }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithText("Failed to check package availability: Network failure", substring = true).assertIsDisplayed()
    composeTestRule.onNodeWithText("Next").assertIsNotEnabled()
  }

  @Test
  fun testAppUnsignedShowsErrorBanner() {
    createWizard { AppMetadata("Fake App", "com.fake.app", "123", "1.2.3", isSigned = false) }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithText("App is unsigned.", substring = true).assertIsDisplayed()
    composeTestRule.onNodeWithText("Next").assertIsNotEnabled()
  }

  @Test
  fun testAppDebugBuildShowsErrorBanner() {
    createWizard { AppMetadata("Fake App", "com.fake.app", "123", "1.2.3", isDebug = true, isSigned = true) }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithText("Build type is incorrect.", substring = true).assertIsDisplayed()
    composeTestRule.onNodeWithText("Next").assertIsNotEnabled()
  }

  @Test
  fun testShouldExtractMetadataReturnsFalse() {
    createWizard(shouldExtractMetadata = { false }) { AppMetadata("Fake App", "com.fake.app", "123", "1.2.3") }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("PackageNameRow").assert(hasAnyChild(hasText("Package name")) and hasAnyChild(hasText("—")))
    composeTestRule.onNodeWithText("Next").assertIsNotEnabled()
  }

  @Test
  fun testInvalidPathThrowsException() {
    val state = PlayPublishingWizardState(bundlePath = "\u0000")
    createWizard(state) { AppMetadata("Fake App", "com.fake.app", "123", "1.2.3") }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("PackageNameRow").assert(hasAnyChild(hasText("Package name")) and hasAnyChild(hasText("—")))
    composeTestRule.onNodeWithText("Next").assertIsNotEnabled()
  }

  @Test
  fun testDefaultBundlePathFromProject() {
    val projectDir = projectRule.project.guessProjectDir()?.toNioPathOrNull()
    if (projectDir != null) {
      Files.createDirectories(projectDir.resolve("app"))
    }
    val state = PlayPublishingWizardState(bundlePath = null)
    createWizard(state) { AppMetadata("Fake App", "com.fake.app", "123", "1.2.3") }
    composeTestRule.waitForIdle()

    val expectedPath =
      projectRule.project.guessProjectDir()?.toNioPathOrNull()?.resolve("app")?.toAbsolutePath()?.toString()
        ?: projectRule.project.guessProjectDir()?.path
        ?: ""
    composeTestRule.onNodeWithText(expectedPath).assertIsDisplayed()
    assertThat(state.bundlePath).isEqualTo(expectedPath)
  }

  @Test
  fun testDefaultBundlePathFromProject_noAppDirectory() {
    projectRule.project.guessProjectDir()?.toNioPathOrNull()?.resolve("app")?.toFile()?.deleteRecursively()
    val state = PlayPublishingWizardState(bundlePath = null)
    createWizard(state) { AppMetadata("Fake App", "com.fake.app", "123", "1.2.3") }
    composeTestRule.waitForIdle()

    val expectedPath =
      projectRule.project.guessProjectDir()?.toNioPathOrNull()?.toAbsolutePath()?.toString()
        ?: projectRule.project.guessProjectDir()?.path
        ?: ""
    composeTestRule.onNodeWithText(expectedPath).assertIsDisplayed()
    assertThat(state.bundlePath).isEqualTo(expectedPath)
  }

  @Test
  fun testShouldExtractMetadataConditions() = runBlocking {
    val tempDir = Files.createTempDirectory("test_bundle")
    try {
      val nonExistentFile = tempDir.resolve("non_existent.aab")
      assertThat(shouldExtractMetadata(nonExistentFile)).isFalse()

      val directory = tempDir.resolve("some_dir.aab")
      Files.createDirectory(directory)
      assertThat(shouldExtractMetadata(directory)).isFalse()

      val wrongExtension = tempDir.resolve("app.apk")
      Files.createFile(wrongExtension)
      assertThat(shouldExtractMetadata(wrongExtension)).isFalse()

      val validBundle = tempDir.resolve("app.aab")
      Files.createFile(validBundle)
      assertThat(shouldExtractMetadata(validBundle)).isTrue()

      val validBundleUppercase = tempDir.resolve("app2.AAB")
      Files.createFile(validBundleUppercase)
      assertThat(shouldExtractMetadata(validBundleUppercase)).isTrue()
    } finally {
      tempDir.toFile().deleteRecursively()
    }
  }

  @Test
  fun testPackageNameChangeTriggersAdiClient() {
    val client: AdiClient = mock()
    wheneverBlocking { client.checkPackageRegistrationStatus(any(), anyOrNull()) }
      .thenReturn(mapOf("com.new.package" to RegistrationState.REGISTERED) to null)

    val state = PlayPublishingWizardState(bundlePath = null)
    createWizard(state, adiClient = client) { AppMetadata("New App", "com.new.package", "123", "1.2.3") }
    composeTestRule.waitForIdle()

    assertThat(state.packageName).isEqualTo("com.new.package")
    assertThat(state.isRegistered).isTrue()
  }

  @Test
  fun testPackageNameChangeDoesNotTriggerAdiClientWhenPathIsLocked() {
    val client: AdiClient = mock()

    val state = PlayPublishingWizardState(bundlePath = "/some/fake/path", isRegistered = true)
    createWizard(state, adiClient = client) { AppMetadata("New App", "com.new.package", "123", "1.2.3") }
    composeTestRule.waitForIdle()

    assertThat(state.packageName).isEqualTo("com.new.package")
    assertThat(state.isRegistered).isTrue()

    org.mockito.Mockito.verifyNoInteractions(client)
  }

  @Test
  fun testPackageNameChangeTriggersAdiClientWhenPathIsLockedButIsRegisteredIsNull() {
    val client: AdiClient = mock()
    wheneverBlocking { client.checkPackageRegistrationStatus(any(), anyOrNull()) }
      .thenReturn(mapOf("com.new.package" to RegistrationState.REGISTERED) to null)

    val state = PlayPublishingWizardState(bundlePath = "/some/fake/path", isRegistered = null)
    createWizard(state, adiClient = client) { AppMetadata("New App", "com.new.package", "123", "1.2.3") }
    composeTestRule.waitForIdle()

    assertThat(state.packageName).isEqualTo("com.new.package")
    assertThat(state.isRegistered).isTrue()
  }

  @Test
  fun testNextButtonDisabledWhileCheckingRegistration() {
    val client: AdiClient = mock()
    val deferred = CompletableDeferred<Pair<Map<String, RegistrationState>, Any?>>()
    wheneverBlocking { client.checkPackageRegistrationStatus(any(), anyOrNull()) }.thenAnswer { runBlocking { deferred.await() } }

    val state = PlayPublishingWizardState(bundlePath = null)
    createWizard(state, adiClient = client) { AppMetadata("New App", "com.new.package", "123", "1.2.3") }

    // During registration check, isCheckingRegistration is true, so "Next" button should be disabled
    composeTestRule.onNodeWithText("Next").assertIsNotEnabled()

    // Complete the registration check to NOT_REGISTERED (available)
    deferred.complete(mapOf("com.new.package" to RegistrationState.NOT_REGISTERED) to null)
    composeTestRule.waitUntil(5000) {
      runCatching {
          composeTestRule.onNodeWithText("Next").assertIsEnabled()
          true
        }
        .getOrDefault(false)
    }
  }

  @Test
  fun testNextButtonEnabledWhenRegistrationCheckFails() {
    val client: AdiClient = mock()
    wheneverBlocking { client.checkPackageRegistrationStatus(any(), anyOrNull()) }.thenThrow(RuntimeException("Network error"))

    val state = PlayPublishingWizardState(bundlePath = null)
    createWizard(state, adiClient = client) { AppMetadata("New App", "com.new.package", "123", "1.2.3") }
    composeTestRule.waitForIdle()

    // Check failed, isCheckingRegistration is false, we should fall back to BundleState.Valid, enabling Next button
    composeTestRule.onNodeWithText("Next").assertIsEnabled()
  }

  private fun createWizard(
    state: PlayPublishingWizardState = PlayPublishingWizardState(bundlePath = "/some/fake/path"),
    shouldExtractMetadata: suspend (Path) -> Boolean = { true },
    adiClient: AdiClient? = null,
    appMetadata: () -> AppMetadata,
  ): TestComposeWizard {
    val initialIsRegistered = state.isRegistered
    val client =
      adiClient
        ?: mock<AdiClient>().apply {
          wheneverBlocking { checkPackageRegistrationStatus(any(), anyOrNull()) }
            .thenAnswer { invocation ->
              @Suppress("UNCHECKED_CAST") val packageNames = invocation.arguments[0] as Collection<String>
              val regState =
                when (initialIsRegistered) {
                  true -> RegistrationState.REGISTERED
                  false -> RegistrationState.NOT_REGISTERED
                  else -> RegistrationState.UNKNOWN
                }
              packageNames.associateWith { regState } to null
            }
        }
    val wizard = TestComposeWizard {
      getOrCreateState { state }
      ChooseBundlePage(createAdiClient = { client }, shouldExtractMetadata = shouldExtractMetadata) { appMetadata() }
    }
    composeTestRule.setContent { CompositionLocalProvider(LocalProject provides projectRule.project) { wizard.Content() } }
    return wizard
  }
}
