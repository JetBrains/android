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
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.android.mockito.kotlin.whenever
import com.android.tools.adtui.compose.LocalProject
import com.android.tools.adtui.compose.TestComposeWizard
import com.android.tools.adtui.compose.utils.StudioComposeTestRule
import com.android.tools.idea.publishing.play.client.FakePlayPublishingClient
import com.android.tools.idea.publishing.play.client.PlayPublishingException
import com.android.tools.idea.publishing.play.client.type.AppEdit
import com.android.tools.idea.publishing.play.client.type.Bundle
import com.android.tools.idea.publishing.play.client.type.Track
import com.android.tools.idea.publishing.play.wizard.PlayPublishingWizardState
import com.android.tools.idea.testing.NotificationRule
import com.google.common.truth.Truth.assertThat
// TODO: android-merge; com.google.gct.login2 is tools/vendor/google/login, which this repository does not carry.
// import com.google.gct.login2.LoginFeatureRule
// import com.google.gct.login2.LoginUsersRule
import com.intellij.ide.BrowserUtil
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.impl.NotificationGroupEP
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.TestActionEvent
import com.intellij.util.xmlb.XmlSerializer
import kotlinx.coroutines.CompletableDeferred
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito

private const val NOTIFICATION_GROUP_ID = "Play Publishing"

@RunsInEdt
class CreateReleasePageTest {
  private val edtRule = EdtRule()
  private val applicationRule = ApplicationRule()
  private val disposableRule = DisposableRule()
  private val notificationRule = NotificationRule()
  private val composeTestRule = StudioComposeTestRule.createStudioComposeTestRule()
  // TODO: android-merge; LoginFeatureRule and LoginUsersRule are in tools/vendor/google/login, which this
  // repository does not carry.
  // private val loginFeatureRule = LoginFeatureRule()
  // private val loginUsersRule = LoginUsersRule()

  @get:Rule
  val ruleChain: RuleChain =
    RuleChain.outerRule(edtRule)
      .around(applicationRule)
      .around(disposableRule)
      .around(notificationRule)
      // .around(loginFeatureRule)
      // .around(loginUsersRule)
      .around(composeTestRule)

  private lateinit var fakeClient: FakePlayPublishingClient

  @Before
  fun setUp() {
    // TODO: android-merge; LoginUsersRule is in tools/vendor/google/login, which this repository does not carry.
    // loginUsersRule.setActiveUser("user@example.com")
    fakeClient = FakePlayPublishingClient()

    // TODO: android-merge; play-publishing.xml is not loaded in this test JVM, so the notification group it
    // declares has to be registered here.
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

  @Test
  fun testInitialStateLoading() {
    fakeClient.config = FakePlayPublishingClient.Config(listEditTracksCall = { _, _ -> CompletableDeferred<List<Track>>().await() })
    val state = PlayPublishingWizardState(packageName = "com.example.app", client = fakeClient)
    createWizard(state)

    composeTestRule.onNodeWithText("Create release").assertIsDisplayed()
    composeTestRule.onNodeWithText("Loading tracks...").assertIsDisplayed()
    composeTestRule.onNodeWithText("Publish app").assertIsNotEnabled()
  }

  @Test
  fun testNoTracksFound() {
    fakeClient.config = FakePlayPublishingClient.Config(listEditTracksCall = { _, _ -> emptyList() })
    val state = PlayPublishingWizardState(packageName = "com.example.app", client = fakeClient)
    createWizard(state)

    composeTestRule.onNodeWithText("No tracks found.").assertIsDisplayed()
    composeTestRule.onNodeWithText("Publish app").assertIsNotEnabled()
  }

  @Test
  fun testTracksLoaded() {
    fakeClient.config =
      FakePlayPublishingClient.Config(listEditTracksCall = { _, _ -> listOf(Track("internal"), Track("alpha"), Track("production")) })
    val state = PlayPublishingWizardState(packageName = "com.example.app", client = fakeClient)
    createWizard(state)

    // Verify it automatically selects "internal"
    composeTestRule.onNodeWithText("Internal Test Track").assertIsDisplayed()

    // Production track should not be available for selection since shouldFilterTrack filters it out
    composeTestRule.onNodeWithText("Internal Test Track").performClick()
    composeTestRule.onNodeWithText("Alpha Track").assertIsDisplayed()

    // We don't support releasing to production from Android Studio.
    composeTestRule.onNodeWithText("Production Track").assertDoesNotExist()

    // By default <en-US> is provided in DEFAULT_RELEASE_NOTES which is valid XML tags.
    // Publish app should be enabled.
    composeTestRule.onNodeWithText("Publish app").assertIsEnabled()
  }

  @Test
  fun testNewAppTrackFiltering() {
    fakeClient.config =
      FakePlayPublishingClient.Config(listEditTracksCall = { _, _ -> listOf(Track("internal"), Track("alpha"), Track("production")) })
    val state = PlayPublishingWizardState(packageName = "com.example.app", client = fakeClient, isAppCreated = true)
    createWizard(state)

    // Verify it automatically selects "internal"
    composeTestRule.onNodeWithText("Internal Test Track").assertIsDisplayed()

    // For new apps, ONLY "internal" should be available
    composeTestRule.onNodeWithText("Internal Test Track").performClick()
    composeTestRule.onNodeWithText("Alpha Track").assertDoesNotExist()
  }

  @Test
  fun testInvalidReleaseNotesDisablesNext() {
    fakeClient.config = FakePlayPublishingClient.Config(listEditTracksCall = { _, _ -> listOf(Track("internal")) })
    val state = PlayPublishingWizardState(packageName = "com.example.app", client = fakeClient)
    state.releaseNotes = "<en-US> valid </en-US>"
    createWizard(state)

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Publish app").assertIsEnabled()

    // Replace text with invalid XML tags
    composeTestRule
      .onNode(hasText("<en-US> valid </en-US>", substring = true))
      .performTextReplacement("<en-US> valid </en-US> invalid text between")

    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithText("Publish app").assertIsNotEnabled()
  }

  @Test
  fun testPublishAction() {
    var commitEditCalled = false
    var createReleaseCalled = false
    var uploadArtifactCalled = false

    fakeClient.config =
      FakePlayPublishingClient.Config(
        insertEditCall = { AppEdit("editId123", "expiry") },
        listEditTracksCall = { _, _ -> listOf(Track("internal")) },
        uploadArtifactCall = { _, _, _ ->
          uploadArtifactCalled = true
          Bundle(1234, "", "")
        },
        createReleaseCall = { pkg, editId, releaseName, releaseNotes, versionCode, trackId ->
          createReleaseCalled = true
          assertThat(pkg).isEqualTo("com.example.app")
          assertThat(editId).isEqualTo("editId123")
          assertThat(releaseName).isEqualTo("My Release")
          assertThat(versionCode).isEqualTo(1234)
          assertThat(trackId).isEqualTo("internal")
          assertThat(releaseNotes).containsEntry("en-US", "These are some notes")
        },
        commitEditCall = { _, _ -> commitEditCalled = true },
      )

    val state = PlayPublishingWizardState(packageName = "com.example.app", client = fakeClient, bundlePath = "/some/path/app.aab")
    state.releaseName = "My Release"
    state.releaseNotes = "<en-US>These are some notes</en-US>"
    createWizard(state)

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Publish app").assertIsEnabled()
    composeTestRule.onNodeWithText("Publish app").performClick()

    composeTestRule.waitForIdle()

    // We can't easily wait for background Task, so we'll poll briefly
    composeTestRule.waitUntil(5000) { commitEditCalled }

    assertThat(uploadArtifactCalled).isTrue()
    assertThat(createReleaseCalled).isTrue()
    assertThat(commitEditCalled).isTrue()
  }

  // TODO: android-merge; the "Open Play Console" notification action is added only when the signed-in
  // email can be read from com.google.gct.login2.GoogleLoginService in tools/vendor/google/login, which
  // this repository does not carry. showUploadSucceededNotification in CreateReleasePage.kt has that block
  // commented out for the same reason, so the notification here has no action to assert on.
  // @Test
  // fun testNotificationLink() {
  //   fakeClient.config =
  //     FakePlayPublishingClient.Config(
  //       insertEditCall = { AppEdit("editId123", "expiry") },
  //       listEditTracksCall = { _, _ -> listOf(Track("internal")) },
  //       uploadArtifactCall = { _, _, _ -> Bundle(1234, "", "") },
  //       createReleaseCall = { _, _, _, _, _, _ -> },
  //       commitEditCall = { _, _ -> },
  //     )
  //
  //   val state =
  //     PlayPublishingWizardState(packageName = "com.example.app", appName = "My App", client = fakeClient, bundlePath = "/some/path/app.aab")
  //   state.releaseName = "My Release"
  //   state.releaseNotes = "<en-US>These are some notes</en-US>"
  //   createWizard(state)
  //
  //   composeTestRule.waitForIdle()
  //   composeTestRule.onNodeWithText("Publish app").performClick()
  //   composeTestRule.waitForIdle()
  //
  //   composeTestRule.waitUntil(5000) { notificationRule.notifications.isNotEmpty() }
  //
  //   val notificationInfo = notificationRule.notifications.single()
  //   assertThat(notificationInfo.title).isEqualTo("Publishing successful")
  //   assertThat(notificationInfo.content)
  //     .isEqualTo("Your release \"My Release\" has been successfully uploaded to Internal Test Track for \"My App\".")
  //
  //   val action = notificationInfo.actions.single()
  //   assertThat(action.templateText).isEqualTo("Open Play Console ↗")
  //
  //   Mockito.mockStatic(BrowserUtil::class.java).use { browserUtil ->
  //     val browsedUrls = mutableListOf<String>()
  //     browserUtil.whenever<Unit> { BrowserUtil.browse(anyString()) }.thenAnswer { browsedUrls.add(it.arguments[0] as String) }
  //
  //     val dummyNotification = Mockito.mock(Notification::class.java)
  //     (action as NotificationAction).actionPerformed(TestActionEvent.createTestEvent(), dummyNotification)
  //     assertThat(browsedUrls)
  //       .containsExactly(
  //         "https://accounts.google.com/AccountChooser?Email=user%40example.com&continue=https%3A%2F%2Fplay.google.com%2Fconsole%2Fpackage%2Fcom.example.app"
  //       )
  //   }
  // }

  @Test
  fun testPublishActionFailureNotification() {
    fakeClient.config =
      FakePlayPublishingClient.Config(
        insertEditCall = { AppEdit("editId123", "expiry") },
        listEditTracksCall = { _, _ -> listOf(Track("internal")) },
        uploadArtifactCall = { _, _, _ -> throw PlayPublishingException("Socket Closed") },
      )

    val state = PlayPublishingWizardState(packageName = "com.example.app", client = fakeClient, bundlePath = "/some/path/app.aab")
    state.releaseName = "My Release"
    state.releaseNotes = "<en-US>These are some notes</en-US>"
    createWizard(state)

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Publish app").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.waitUntil(5000) { notificationRule.notifications.isNotEmpty() }

    val notificationInfo = notificationRule.notifications.single()
    assertThat(notificationInfo.title).isEqualTo("Publishing failed")
    assertThat(notificationInfo.content).isEqualTo("Socket Closed")
  }

  @Test
  fun testFailedToLoadTracksError() {
    fakeClient.config = FakePlayPublishingClient.Config(listEditTracksCall = { _, _ -> throw Exception("Network failure") })
    val state = PlayPublishingWizardState(packageName = "com.example.app", client = fakeClient)
    createWizard(state)

    composeTestRule.onNodeWithText("Failed to load tracks: Network failure").assertIsDisplayed()
    composeTestRule.onNodeWithText("Publish app").assertIsNotEnabled()
  }

  private fun createWizard(state: PlayPublishingWizardState = PlayPublishingWizardState(client = fakeClient)): TestComposeWizard {
    val wizard = TestComposeWizard {
      getOrCreateState { state }
      CreateReleasePage()
    }
    composeTestRule.setContent { CompositionLocalProvider(LocalProject provides null) { wizard.Content() } }
    return wizard
  }
}
