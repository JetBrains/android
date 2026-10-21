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
package com.android.tools.idea.publishing.play

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.android.mockito.kotlin.whenever as staticWhenever
import com.android.tools.adtui.compose.LocalProject
import com.android.tools.adtui.compose.TestComposeWizard
import com.android.tools.adtui.compose.utils.StudioComposeTestRule
import com.android.tools.analytics.UsageTrackerRule
import com.android.tools.idea.gservices.DevServicesDeprecationData
import com.android.tools.idea.gservices.DevServicesDeprecationDataProvider
import com.android.tools.idea.gservices.DevServicesDeprecationStatus
import com.android.tools.idea.publishing.AdiClient
import com.android.tools.idea.publishing.AppPublishingService
import com.android.tools.idea.publishing.AppPublishingSource
import com.android.tools.idea.publishing.RegistrationState
import com.android.tools.idea.publishing.play.client.FakePlayPublishingClient
import com.android.tools.idea.publishing.play.client.NO_APP_LISTING_CORRECTION_MESSAGE
import com.android.tools.idea.publishing.play.client.PlayPublishingClient
import com.android.tools.idea.publishing.play.client.PlayPublishingException
import com.android.tools.idea.publishing.play.client.type.App
import com.android.tools.idea.publishing.play.client.type.AppEdit
import com.android.tools.idea.publishing.play.client.type.Bundle
import com.android.tools.idea.publishing.play.client.type.Track
import com.android.tools.idea.publishing.play.wizard.PlayPublishingWizardState
import com.android.tools.idea.publishing.play.wizard.page.AccountChooserPage
import com.android.tools.idea.publishing.play.wizard.page.ChooseBundlePage
import com.android.tools.idea.publishing.play.wizard.page.CreateReleasePage
import com.android.tools.idea.testing.NotificationRule
import com.google.common.truth.Truth.assertThat
// AND-142 JetBrains patch (Google Play Publishing): com.google.gct.login2 is tools/vendor/google/login, which this repository does not carry.
// import com.google.gct.login2.LoginFeatureRule
// import com.google.gct.login2.LoginUsersRule
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.DevServiceDeprecationInfo
import com.google.wireless.android.sdk.stats.PlayPublishingEvent.CreateAppDetails.CreateAppResult
import com.google.wireless.android.sdk.stats.PlayPublishingEvent.CreateReleaseDetails.CreateReleaseResult
import com.google.wireless.android.sdk.stats.PlayPublishingEvent.CreateReleaseDetails.TrackType
import com.google.wireless.android.sdk.stats.PlayPublishingEvent.PlayPublishingEventType
import com.google.wireless.android.sdk.stats.PlayPublishingEvent.WizardShownDetails.WizardInvocationSource
import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.impl.NotificationGroupEP
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import com.intellij.util.application
import com.intellij.util.xmlb.XmlSerializer
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

private const val NOTIFICATION_GROUP_ID = "Play Publishing"

@RunsInEdt
class PlayPublishingUsageTrackerTest {
  private val edtRule = EdtRule()
  private val projectRule = ProjectRule()
  private val disposableRule = DisposableRule()
  private val notificationRule = NotificationRule(projectRule)
  private val composeTestRule = StudioComposeTestRule.createStudioComposeTestRule()
  // AND-142 JetBrains patch (Google Play Publishing): LoginFeatureRule and LoginUsersRule are in tools/vendor/google/login, which this
  // repository does not carry. The metrics under test are logged by PlayPublishingUsageTracker, which does
  // not read the signed-in account, so the tests below run without them.
  // private val loginFeatureRule = LoginFeatureRule()
  // private val loginUsersRule = LoginUsersRule()
  private val usageTrackerRule = UsageTrackerRule()

  @get:Rule
  val ruleChain: RuleChain =
    RuleChain.outerRule(edtRule)
      .around(projectRule)
      .around(disposableRule)
      .around(notificationRule)
      // .around(loginFeatureRule)
      // .around(loginUsersRule)
      .around(usageTrackerRule)
      .around(composeTestRule)

  private lateinit var fakeClient: FakePlayPublishingClient

  @Before
  fun setUp() {
    // AND-142 JetBrains patch (Google Play Publishing): LoginUsersRule is in tools/vendor/google/login, which this repository does not carry.
    // loginUsersRule.setActiveUser("user@example.com")
    fakeClient = FakePlayPublishingClient()
    application.replaceService(PlayPublishingClient::class.java, fakeClient, disposableRule.disposable)

    // JetBrains patch: play-publishing.xml is not loaded in this test JVM, so the "Play Publishing" notification
    // group is registered here, the same way BranchContextTrackerTest does. Drop this once the module's descriptor
    // is loaded in its tests.
    if (!NotificationGroupManager.getInstance().isGroupRegistered(NOTIFICATION_GROUP_ID)) {
      val group =
        XmlSerializer.deserialize(
          JDOMUtil.load("""<notificationGroup id="$NOTIFICATION_GROUP_ID" displayType="BALLOON"/>"""),
          NotificationGroupEP::class.java,
        )
      ExtensionPointName<NotificationGroupEP>("com.intellij.notificationGroup")
        .point
        .registerExtension(group, disposableRule.disposable)
    }
  }

  @After
  fun tearDown() {
    // Cancel background task if it is running at the end of the test.
    AppPublishingService.getInstance(projectRule.project).coroutineScope.coroutineContext.cancelChildren()
  }

  @Test
  fun testTrackWizardShown() {
    PlayPublishingUsageTracker.trackWizardShown(AppPublishingSource.EXPORT_SIGNED_PACKAGE_WIZARD)

    val loggedEvents = getLoggedEvents()
    assertThat(loggedEvents).hasSize(1)

    val studioEvent = loggedEvents[0].studioEvent
    assertThat(studioEvent.kind).isEqualTo(AndroidStudioEvent.EventKind.PLAY_PUBLISHING_EVENT)
    assertThat(studioEvent.hasPlayPublishingEvent()).isTrue()

    val playPublishingEvent = studioEvent.playPublishingEvent
    assertThat(playPublishingEvent.eventType).isEqualTo(PlayPublishingEventType.WIZARD_SHOWN)
    assertThat(playPublishingEvent.wizardShownDetails.invocationSource).isEqualTo(WizardInvocationSource.EXPORT_SIGNED_PACKAGE_WIZARD)
  }

  @Test
  fun testTrackChooseBundle() {
    PlayPublishingUsageTracker.trackChooseBundle(
      isPackageRegistered = true,
      isAppNameRead = true,
      isPackageNameRead = true,
      isVersionCodeRead = false,
      isVersionNameRead = true,
    )

    val loggedEvents = getLoggedEvents()
    assertThat(loggedEvents).hasSize(1)

    val studioEvent = loggedEvents[0].studioEvent
    assertThat(studioEvent.kind).isEqualTo(AndroidStudioEvent.EventKind.PLAY_PUBLISHING_EVENT)

    val playPublishingEvent = studioEvent.playPublishingEvent
    assertThat(playPublishingEvent.eventType).isEqualTo(PlayPublishingEventType.CHOOSE_BUNDLE)
    val details = playPublishingEvent.chooseBundleDetails
    assertThat(details.packageRegistered).isTrue()
    assertThat(details.appNameRead).isTrue()
    assertThat(details.packageNameRead).isTrue()
    assertThat(details.versionCodeRead).isFalse()
    assertThat(details.versionNameRead).isTrue()
  }

  @Test
  fun testTrackCreateApp() {
    PlayPublishingUsageTracker.trackCreateApp(CreateAppResult.SUCCESS)

    val loggedEvents = getLoggedEvents()
    assertThat(loggedEvents).hasSize(1)

    val studioEvent = loggedEvents[0].studioEvent
    assertThat(studioEvent.kind).isEqualTo(AndroidStudioEvent.EventKind.PLAY_PUBLISHING_EVENT)

    val playPublishingEvent = studioEvent.playPublishingEvent
    assertThat(playPublishingEvent.eventType).isEqualTo(PlayPublishingEventType.CREATE_APP)
    assertThat(playPublishingEvent.createAppDetails.createAppResult).isEqualTo(CreateAppResult.SUCCESS)
  }

  @Test
  fun testTrackCreateRelease() {
    PlayPublishingUsageTracker.trackCreateRelease(CreateReleaseResult.SUCCESS, TrackType.INTERNAL_TESTING, 12500)

    val loggedEvents = getLoggedEvents()
    assertThat(loggedEvents).hasSize(1)

    val studioEvent = loggedEvents[0].studioEvent
    assertThat(studioEvent.kind).isEqualTo(AndroidStudioEvent.EventKind.PLAY_PUBLISHING_EVENT)

    val playPublishingEvent = studioEvent.playPublishingEvent
    assertThat(playPublishingEvent.eventType).isEqualTo(PlayPublishingEventType.CREATE_RELEASE)
    val details = playPublishingEvent.createReleaseDetails
    assertThat(details.createReleaseResult).isEqualTo(CreateReleaseResult.SUCCESS)
    assertThat(details.trackType).isEqualTo(TrackType.INTERNAL_TESTING)
    assertThat(details.timeToUploadBundleMs).isEqualTo(12500)
  }

  @Test
  fun testTrackCreateAppFailures() {
    val failures =
      listOf(
        CreateAppResult.FAILED_PACKAGE_NAME_NOT_AVAILABLE,
        CreateAppResult.FAILED_NO_DEVELOPER_ACCOUNTS,
        CreateAppResult.FAILED_LIST_DEVELOPER_ACCOUNTS_FAILED,
        CreateAppResult.UNKNOWN_CREATE_APP_RESULT,
      )

    failures.forEachIndexed { index, failure ->
      PlayPublishingUsageTracker.trackCreateApp(failure)

      val loggedEvents = getLoggedEvents()
      assertThat(loggedEvents).hasSize(index + 1)

      val playPublishingEvent = loggedEvents[index].studioEvent.playPublishingEvent
      assertThat(playPublishingEvent.eventType).isEqualTo(PlayPublishingEventType.CREATE_APP)
      assertThat(playPublishingEvent.createAppDetails.createAppResult).isEqualTo(failure)
    }
  }

  @Test
  fun testTrackCreateReleaseFailures() {
    val failures =
      listOf(
        CreateReleaseResult.FAILED_TO_LIST_TRACKS,
        CreateReleaseResult.FAILED_VERSION_CODE_ALREADY_EXISTS,
        CreateReleaseResult.FAILED_TO_UPLOAD_BUNDLE,
        CreateReleaseResult.FAILED_RELEASE_NOT_ALLOWED_ON_TRACK,
        CreateReleaseResult.FAILED_BUNDLE_SIGNED_WITH_WRONG_KEY,
        CreateReleaseResult.FAILED_USER_CANCELLED,
        CreateReleaseResult.FAILED_TO_CREATE_RELEASE,
        CreateReleaseResult.FAILED_TO_COMMIT_RELEASE,
      )

    failures.forEachIndexed { index, failure ->
      PlayPublishingUsageTracker.trackCreateRelease(result = failure, releaseTrackType = TrackType.ALPHA, uploadTimeMs = 4500)

      val loggedEvents = getLoggedEvents()
      assertThat(loggedEvents).hasSize(index + 1)

      val playPublishingEvent = loggedEvents[index].studioEvent.playPublishingEvent
      assertThat(playPublishingEvent.eventType).isEqualTo(PlayPublishingEventType.CREATE_RELEASE)
      val details = playPublishingEvent.createReleaseDetails
      assertThat(details.createReleaseResult).isEqualTo(failure)
      assertThat(details.trackType).isEqualTo(TrackType.ALPHA)
      assertThat(details.timeToUploadBundleMs).isEqualTo(4500)
    }
  }

  @Test
  fun testChooseBundleTracking() {
    fakeClient.config =
      FakePlayPublishingClient.Config(listAppsCall = { listOf(App(packageName = "com.fake.app", displayName = "Fake App")) })
    val state = PlayPublishingWizardState(bundlePath = "/some/fake/path", isRegistered = true)
    val wizard = createChooseBundleWizard(state) { AppMetadata("Fake App", "com.fake.app", "123", "1.2.3") }
    composeTestRule.waitForIdle()

    wizard.performAction(wizard.nextAction)
    composeTestRule.waitForIdle()

    val loggedEvents = getLoggedEvents()
    assertThat(loggedEvents).hasSize(1)
    val studioEvent = loggedEvents[0].studioEvent
    val playEvent = studioEvent.playPublishingEvent
    assertThat(playEvent.eventType).isEqualTo(PlayPublishingEventType.CHOOSE_BUNDLE)
    assertThat(playEvent.chooseBundleDetails.packageRegistered).isTrue()
    assertThat(playEvent.chooseBundleDetails.appNameRead).isTrue()
    assertThat(playEvent.chooseBundleDetails.packageNameRead).isTrue()
    assertThat(playEvent.chooseBundleDetails.versionCodeRead).isTrue()
    assertThat(playEvent.chooseBundleDetails.versionNameRead).isTrue()
  }

  @Test
  fun testPublishActionVersionCodeAlreadyUsedFailure() {
    fakeClient.config =
      FakePlayPublishingClient.Config(
        insertEditCall = { AppEdit("editId123", "expiry") },
        listEditTracksCall = { _, _ -> listOf(Track("internal")) },
        uploadArtifactCall = { _, _, _ -> throw PlayPublishingException("Version code 29 has already been used") },
      )

    val state = PlayPublishingWizardState(packageName = "com.example.app", bundlePath = "/some/path/app.aab")
    state.releaseName = "My Release"
    state.releaseNotes = "<en-US>These are some notes</en-US>"
    createCreateReleaseWizard(state)

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Publish app").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.waitUntil(5000) { notificationRule.notifications.isNotEmpty() }

    val loggedEvents = getLoggedEvents()
    assertThat(loggedEvents).hasSize(1)
    val playPublishingEvent = loggedEvents[0].studioEvent.playPublishingEvent
    assertThat(playPublishingEvent.createReleaseDetails.createReleaseResult)
      .isEqualTo(CreateReleaseResult.FAILED_VERSION_CODE_ALREADY_EXISTS)
  }

  @Test
  fun testPublishActionWrongKeyFailure() {
    fakeClient.config =
      FakePlayPublishingClient.Config(
        insertEditCall = { AppEdit("editId123", "expiry") },
        listEditTracksCall = { _, _ -> listOf(Track("internal")) },
        uploadArtifactCall = { _, _, _ -> throw PlayPublishingException("The Android App Bundle was signed with the wrong key") },
      )

    val state = PlayPublishingWizardState(packageName = "com.example.app", bundlePath = "/some/path/app.aab")
    state.releaseName = "My Release"
    state.releaseNotes = "<en-US>These are some notes</en-US>"
    createCreateReleaseWizard(state)

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Publish app").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.waitUntil(5000) { notificationRule.notifications.isNotEmpty() }

    val loggedEvents = getLoggedEvents()
    assertThat(loggedEvents).hasSize(1)
    val playPublishingEvent = loggedEvents[0].studioEvent.playPublishingEvent
    assertThat(playPublishingEvent.createReleaseDetails.createReleaseResult)
      .isEqualTo(CreateReleaseResult.FAILED_BUNDLE_SIGNED_WITH_WRONG_KEY)
  }

  @Test
  fun testPublishActionNoAppListingCorrectionMessageFailure() {
    fakeClient.config =
      FakePlayPublishingClient.Config(
        insertEditCall = { AppEdit("editId123", "expiry") },
        listEditTracksCall = { _, _ -> listOf(Track("internal")) },
        uploadArtifactCall = { _, _, _ -> Bundle(1234, "", "") },
        createReleaseCall = { _, _, _, _, _, _ -> },
        commitEditCall = { _, _ -> throw PlayPublishingException(NO_APP_LISTING_CORRECTION_MESSAGE) },
      )

    val state = PlayPublishingWizardState(packageName = "com.example.app", bundlePath = "/some/path/app.aab")
    state.releaseName = "My Release"
    state.releaseNotes = "<en-US>These are some notes</en-US>"
    createCreateReleaseWizard(state)

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Publish app").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.waitUntil(5000) { notificationRule.notifications.isNotEmpty() }

    val loggedEvents = getLoggedEvents()
    assertThat(loggedEvents).hasSize(1)
    val playPublishingEvent = loggedEvents[0].studioEvent.playPublishingEvent
    assertThat(playPublishingEvent.createReleaseDetails.createReleaseResult)
      .isEqualTo(CreateReleaseResult.FAILED_RELEASE_NOT_ALLOWED_ON_TRACK)
  }

  @Test
  fun testTrackDeprecationOnDisplay() {
    replaceDeprecationService()

    // AND-142 JetBrains patch (Google Play Publishing): LoginUsersRule is in tools/vendor/google/login, which this repository does not carry.
    // The deprecation banner this test drives does not read the signed-in account, so the test runs without it.
    // loginUsersRule.setActiveUser("user@google.com")

    val wizard = TestComposeWizard { AccountChooserPage() }
    composeTestRule.setContent { CompositionLocalProvider(LocalProject provides projectRule.project) { wizard.Content() } }

    composeTestRule.waitForIdle()

    // Verify deprecation display event is logged
    val loggedEvents =
      usageTrackerRule.usages.filter { it.studioEvent.kind == AndroidStudioEvent.EventKind.STUDIO_DEPRECATION_NOTIFICATION_EVENT }
    assertThat(loggedEvents).hasSize(1)
    val info = loggedEvents[0].studioEvent.studioDeprecationNotificationEvent.devServiceDeprecationInfo
    assertThat(info.deprecationStatus).isEqualTo(DevServiceDeprecationInfo.DeprecationStatus.DEPRECATED)
    assertThat(info.deliveryType).isEqualTo(DevServiceDeprecationInfo.DeliveryType.BANNER)
    assertThat(info.userNotified).isTrue()
  }

  @Test
  fun testTrackDeprecationOnlyOnce() {
    replaceDeprecationService()

    // AND-142 JetBrains patch (Google Play Publishing): LoginUsersRule is in tools/vendor/google/login, which this repository does not carry.
    // The deprecation banner this test drives does not read the signed-in account, so the test runs without it.
    // loginUsersRule.setActiveUser("user@google.com")

    val state = PlayPublishingWizardState()
    val wizard = TestComposeWizard {
      getOrCreateState { state }
      AccountChooserPage()
    }
    composeTestRule.setContent { CompositionLocalProvider(LocalProject provides projectRule.project) { wizard.Content() } }

    composeTestRule.waitForIdle()

    // First composition triggers 1 event
    var loggedEvents =
      usageTrackerRule.usages.filter { it.studioEvent.kind == AndroidStudioEvent.EventKind.STUDIO_DEPRECATION_NOTIFICATION_EVENT }
    assertThat(loggedEvents).hasSize(1)

    // Trigger a recomposition / navigation away and back
    wizard.pushPage { ChooseBundlePage() }
    composeTestRule.waitForIdle()
    wizard.popPage()
    composeTestRule.waitForIdle()

    // Verify deprecation display event is still only logged exactly once
    loggedEvents =
      usageTrackerRule.usages.filter { it.studioEvent.kind == AndroidStudioEvent.EventKind.STUDIO_DEPRECATION_NOTIFICATION_EVENT }
    assertThat(loggedEvents).hasSize(1)
  }

  @Test
  fun testTrackDeprecationOnMoreInfoClicked() {
    replaceDeprecationService()

    // AND-142 JetBrains patch (Google Play Publishing): LoginUsersRule is in tools/vendor/google/login, which this repository does not carry.
    // The deprecation banner this test drives does not read the signed-in account, so the test runs without it.
    // loginUsersRule.setActiveUser("user@google.com")

    val wizard = TestComposeWizard { AccountChooserPage() }
    composeTestRule.setContent { CompositionLocalProvider(LocalProject provides projectRule.project) { wizard.Content() } }

    Mockito.mockStatic(BrowserUtil::class.java).use { browserUtil ->
      browserUtil.staticWhenever<Unit> { BrowserUtil.browse(anyString()) }.thenAnswer {}
      composeTestRule.onNodeWithText("More info").performClick()
      composeTestRule.waitForIdle()
    }

    // Verify deprecation click event is logged
    val clickedEvents =
      usageTrackerRule.usages.filter {
        it.studioEvent.kind == AndroidStudioEvent.EventKind.STUDIO_DEPRECATION_NOTIFICATION_EVENT &&
          it.studioEvent.studioDeprecationNotificationEvent.devServiceDeprecationInfo.moreInfoClicked
      }
    assertThat(clickedEvents).hasSize(1)
    val info = clickedEvents[0].studioEvent.studioDeprecationNotificationEvent.devServiceDeprecationInfo
    assertThat(info.deprecationStatus).isEqualTo(DevServiceDeprecationInfo.DeprecationStatus.DEPRECATED)
    assertThat(info.deliveryType).isEqualTo(DevServiceDeprecationInfo.DeliveryType.BANNER)
  }

  @Test
  fun testTrackDeprecationOnUpdateClicked() {
    replaceDeprecationService()

    // AND-142 JetBrains patch (Google Play Publishing): LoginUsersRule is in tools/vendor/google/login, which this repository does not carry.
    // The deprecation banner this test drives does not read the signed-in account, so the test runs without it.
    // loginUsersRule.setActiveUser("user@google.com")

    val wizard = TestComposeWizard { AccountChooserPage() }
    composeTestRule.setContent { CompositionLocalProvider(LocalProject provides projectRule.project) { wizard.Content() } }

    composeTestRule.onNodeWithText("Update Android Studio").performClick()
    composeTestRule.waitForIdle()

    // Verify deprecation click event is logged
    val clickedEvents =
      usageTrackerRule.usages.filter {
        it.studioEvent.kind == AndroidStudioEvent.EventKind.STUDIO_DEPRECATION_NOTIFICATION_EVENT &&
          it.studioEvent.studioDeprecationNotificationEvent.devServiceDeprecationInfo.updateClicked
      }
    assertThat(clickedEvents).hasSize(1)
    val info = clickedEvents[0].studioEvent.studioDeprecationNotificationEvent.devServiceDeprecationInfo
    assertThat(info.deprecationStatus).isEqualTo(DevServiceDeprecationInfo.DeprecationStatus.DEPRECATED)
    assertThat(info.deliveryType).isEqualTo(DevServiceDeprecationInfo.DeliveryType.BANNER)
  }

  @Test
  fun testTrackDeprecationOnDismissed() {
    replaceDeprecationService()

    // AND-142 JetBrains patch (Google Play Publishing): LoginUsersRule is in tools/vendor/google/login, which this repository does not carry.
    // The deprecation banner this test drives does not read the signed-in account, so the test runs without it.
    // loginUsersRule.setActiveUser("user@google.com")

    val wizard = TestComposeWizard { AccountChooserPage() }
    composeTestRule.setContent { CompositionLocalProvider(LocalProject provides projectRule.project) { wizard.Content() } }

    composeTestRule.onNodeWithContentDescription("Dismiss").performClick()
    composeTestRule.waitForIdle()

    // Verify deprecation dismissal event is logged
    val dismissedEvents =
      usageTrackerRule.usages.filter {
        it.studioEvent.kind == AndroidStudioEvent.EventKind.STUDIO_DEPRECATION_NOTIFICATION_EVENT &&
          it.studioEvent.studioDeprecationNotificationEvent.devServiceDeprecationInfo.deliveryDismissed
      }
    assertThat(dismissedEvents).hasSize(1)
    val info = dismissedEvents[0].studioEvent.studioDeprecationNotificationEvent.devServiceDeprecationInfo
    assertThat(info.deprecationStatus).isEqualTo(DevServiceDeprecationInfo.DeprecationStatus.DEPRECATED)
    assertThat(info.deliveryType).isEqualTo(DevServiceDeprecationInfo.DeliveryType.BANNER)
  }

  private fun replaceDeprecationService() {
    val mockDeprecationProvider = mock<DevServicesDeprecationDataProvider>()
    val deprecationData =
      DevServicesDeprecationData(
        header = "Deprecation Header",
        description = "Play Publishing service is deprecated.",
        moreInfoUrl = "https://google.com",
        showUpdateAction = true,
        status = DevServicesDeprecationStatus.DEPRECATED,
      )
    whenever(mockDeprecationProvider.getCurrentDeprecationData("play/publishing", "Google Play Publishing")).thenReturn(deprecationData)
    application.replaceService(DevServicesDeprecationDataProvider::class.java, mockDeprecationProvider, disposableRule.disposable)
  }

  private fun createChooseBundleWizard(
    state: PlayPublishingWizardState = PlayPublishingWizardState(bundlePath = "/some/fake/path"),
    appMetadata: () -> AppMetadata,
  ): TestComposeWizard {
    val client =
      mock<AdiClient>().apply {
        runBlocking {
          whenever(checkPackageRegistrationStatus(any(), anyOrNull()))
            .thenReturn(mapOf("com.fake.app" to RegistrationState.REGISTERED) to null)
        }
      }
    val wizard = TestComposeWizard {
      getOrCreateState { state }
      ChooseBundlePage(createAdiClient = { client }, shouldExtractMetadata = { true }) { appMetadata() }
    }
    composeTestRule.setContent { CompositionLocalProvider(LocalProject provides projectRule.project) { wizard.Content() } }
    return wizard
  }

  private fun createCreateReleaseWizard(state: PlayPublishingWizardState = PlayPublishingWizardState()): TestComposeWizard {
    val wizard = TestComposeWizard {
      getOrCreateState { state }
      CreateReleasePage(releaseNotesDebounceMillis = 0L)
    }
    composeTestRule.setContent { CompositionLocalProvider(LocalProject provides projectRule.project) { wizard.Content() } }
    return wizard
  }

  private fun getLoggedEvents() = usageTrackerRule.usages.filter { it.studioEvent.hasPlayPublishingEvent() }
}
