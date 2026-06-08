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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.tools.adtui.compose.LocalProject
import com.android.tools.adtui.compose.WizardAction
import com.android.tools.adtui.compose.WizardPageScope
import com.android.tools.idea.publishing.AppPublishingService
import com.android.tools.idea.publishing.play.PlayPublishingUsageTracker
import com.android.tools.idea.publishing.play.client.NO_APP_LISTING_CORRECTION_MESSAGE
import com.android.tools.idea.publishing.play.client.PlayPublishingClient
import com.android.tools.idea.publishing.play.client.PlayPublishingException
import com.android.tools.idea.publishing.play.client.type.AppEdit
import com.android.tools.idea.publishing.play.client.type.Bundle
import com.android.tools.idea.publishing.play.client.type.Track
import com.android.tools.idea.publishing.play.wizard.FormField
import com.android.tools.idea.publishing.play.wizard.PlayPublishingWizardHeader
import com.android.tools.idea.publishing.play.wizard.PlayPublishingWizardState
// TODO: android-merge; com.google.gct.login2 is tools/vendor/google/login, which this repository does not carry.
// import com.google.gct.login2.GoogleLoginService
import com.google.wireless.android.sdk.stats.PlayPublishingEvent.CreateReleaseDetails.CreateReleaseResult
import com.google.wireless.android.sdk.stats.PlayPublishingEvent.CreateReleaseDetails.TrackType
import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.withBackgroundProgress
import icons.StudioIcons
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.InlineErrorBanner
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextArea
import org.jetbrains.jewel.ui.component.TextField

private const val RELEASE_NAME_CHAR_LIMIT = 50
private const val INTERNAL_TEST_TRACK_NAME = "internal"
private const val ALPHA_TRACK_NAME = "alpha"
private const val BETA_TRACK_NAME = "beta"
private const val PRODUCTION_TRACK_NAME = "production"

private val trackNameMap =
  mapOf(
    INTERNAL_TEST_TRACK_NAME to "Internal Test Track",
    ALPHA_TRACK_NAME to "Alpha Track",
    BETA_TRACK_NAME to "Beta Track",
    PRODUCTION_TRACK_NAME to "Production Track",
  )

private val VERSION_CODE_USED_REGEX = Regex("version code \\d+ has already been used", RegexOption.IGNORE_CASE)
private val WRONG_KEY_REGEX = Regex("The Android App Bundle was signed with the wrong key", RegexOption.IGNORE_CASE)

private fun String.displayTrackName() = trackNameMap[this] ?: (replaceFirstChar { it.uppercase() } + " Track")

@OptIn(ExperimentalFoundationApi::class, ExperimentalJewelApi::class)
@Composable
fun WizardPageScope.CreateReleasePage() {
  val state = getOrCreateState<PlayPublishingWizardState> { error("State not initialized") }
  val project = LocalProject.current ?: error("Project cannot be null")

  val releaseNameState = rememberTextFieldState(state.releaseName ?: "")
  val releaseNotesState = rememberTextFieldState(state.releaseNotes ?: "")
  var extractedTags: Map<String, String>? by remember { mutableStateOf(null) }

  LaunchedEffect(releaseNotesState.text) { extractedTags = extractAndValidateTags(releaseNotesState.text.toString().trim()) }

  var errorMessage: String? by remember { mutableStateOf(null) }
  var isLoadingTracks by remember { mutableStateOf(true) }
  var appEdit: AppEdit? by remember { mutableStateOf(null) }
  var tracks: List<Track> by remember { mutableStateOf(emptyList()) }
  var selectedTrack: String? by remember { mutableStateOf(null) }

  // For a new app, we only allow internal test track.
  // For an existing app, we allow all tracks except production
  fun shouldFilterTrack(track: Track) =
    if (state.isAppCreated) track.track == INTERNAL_TEST_TRACK_NAME else track.track != PRODUCTION_TRACK_NAME

  LaunchedEffect(Unit) {
    try {
      state.packageName?.let { packageName ->
        val edit = PlayPublishingClient.getInstance().insertEdit(packageName)
        appEdit = edit
        // We don't support releasing to production from Android Studio.
        tracks = PlayPublishingClient.getInstance().listEditTracks(packageName, edit.id).filter(::shouldFilterTrack)
        selectedTrack =
          if (tracks.isNotEmpty()) {
            if (tracks.any { it.track == INTERNAL_TEST_TRACK_NAME }) {
              INTERNAL_TEST_TRACK_NAME
            } else {
              tracks.first().track
            }
          } else {
            null
          }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      errorMessage = "Failed to load tracks: ${e.message}"
      PlayPublishingUsageTracker.trackCreateRelease(CreateReleaseResult.FAILED_TO_LIST_TRACKS)
    } finally {
      isLoadingTracks = false
    }
  }

  LaunchedEffect(releaseNameState.text) { state.releaseName = releaseNameState.text.toString() }
  LaunchedEffect(releaseNotesState.text) { state.releaseNotes = releaseNotesState.text.toString() }

  Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
      PlayPublishingWizardHeader(subtitle = "Create release")

      Column(modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())) {
        // Form fields
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
          FormField(label = "Publish to:") {
            if (isLoadingTracks) {
              Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.width(8.dp))
                Text("Loading tracks...")
              }
            } else if (tracks.isEmpty()) {
              Text("No tracks found.", color = JewelTheme.globalColors.text.disabled, modifier = Modifier.padding(top = 8.dp))
            } else {
              Dropdown(
                modifier = Modifier.fillMaxWidth(),
                menuContent = {
                  tracks.forEach { track ->
                    selectableItem(selected = (track.track == selectedTrack), onClick = { selectedTrack = track.track }) {
                      Text(track.track.displayTrackName())
                    }
                  }
                },
              ) {
                selectedTrack?.let { Text(it.displayTrackName()) }
              }
            }
          }

          // Release Name
          FormField(label = "Release name:") {
            Column {
              TextField(
                state = releaseNameState,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(DEFAULT_RELEASE_NAME) },
                inputTransformation = InputTransformation.maxLength(RELEASE_NAME_CHAR_LIMIT),
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "For internal identification. Not shown on Google Play.",
                style = JewelTheme.defaultTextStyle.copy(fontSize = 12.sp, color = Color.Gray),
              )
            }
          }

          // Release Notes
          FormField(label = "Release notes:") {
            Column {
              TextArea(
                state = releaseNotesState,
                modifier = Modifier.fillMaxWidth().height(200.dp),
                placeholder = { Text(DEFAULT_RELEASE_NOTES) },
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "Enter release notes for each language within the tags.",
                style = JewelTheme.defaultTextStyle.copy(fontSize = 12.sp, color = Color.Gray),
              )
            }
          }
        }
      }
    }

    if (errorMessage != null) {
      InlineErrorBanner(modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp)) { Text(errorMessage!!) }
    }
  }

  fun shouldDisableNextAction() = isLoadingTracks || tracks.isEmpty() || extractedTags == null || selectedTrack == null

  // If the app is created in this dialog, we shouldn't go back. Coming back to this page will call
  // the create app API again and fail since the package name will already exist.
  prevButtonEnabled = !state.isAppCreated
  nextActionName = "Publish app"
  nextAction =
    if (shouldDisableNextAction()) {
      WizardAction.Disabled
    } else
      WizardAction {
        val packageName = state.packageName ?: return@WizardAction
        val editId = appEdit?.id ?: return@WizardAction
        val artifactPath = state.bundlePath ?: return@WizardAction
        val selectedTrackId = selectedTrack ?: return@WizardAction
        val tags = extractedTags ?: emptyMap()
        val releaseName = releaseNameState.text.toString()
        val appName = state.appName

        AppPublishingService.getInstance(project).coroutineScope.launch {
          withBackgroundProgress(project, "Uploading Build to Google Play...", true) {
            val startTimeMs = System.currentTimeMillis()
            val trackType = selectedTrackId.toTrackType()
            try {
              val responseArtifact = uploadBundleStep(packageName, editId, artifactPath, trackType)
              val uploadTimeMs = (System.currentTimeMillis() - startTimeMs).toInt()

              createReleaseStep(
                packageName,
                editId,
                releaseName,
                tags,
                responseArtifact.versionCode,
                selectedTrackId,
                trackType,
                uploadTimeMs,
              )

              commitEditStep(packageName, editId, trackType, uploadTimeMs)

              PlayPublishingUsageTracker.trackCreateRelease(CreateReleaseResult.SUCCESS, trackType, uploadTimeMs)
              showUploadSuccessfulNotification(project, releaseName, selectedTrackId, appName, packageName)
            } catch (e: PlayPublishingException) {
              val message = e.message ?: "Unknown error"
              showUploadFailedNotification(project, message)
            } catch (e: CancellationException) {
              throw e
            } catch (e: Exception) {
              showUploadFailedNotification(project, "Unknown error")
            }
          }
        }
        close()
      }
}

private fun showUploadSuccessfulNotification(
  project: Project,
  releaseName: String?,
  selectedTrack: String?,
  appName: String?,
  packageName: String?,
) {
  val content =
    "Your release ${if(releaseName.isNullOrEmpty()) "" else "\"${releaseName}\" "}has been successfully uploaded to ${selectedTrack?.displayTrackName()}${if (appName.isNullOrEmpty()) "." else " for \"${appName}\"."}"
  val notification =
    NotificationGroupManager.getInstance()
      .getNotificationGroup("Play Publishing")
      .createNotification("Publishing successful", content, NotificationType.INFORMATION)
      .setIcon(StudioIcons.Common.SUCCESS)

  // TODO: android-merge; the signed-in account comes from com.google.gct.login2.GoogleLoginService in
  // tools/vendor/google/login, which this repository does not carry, so the notification has no
  // "Open Play Console" action here.
  // val email = GoogleLoginService.instance.getEmail()
  // if (email != null) {
  //   notification.addAction(
  //     NotificationAction.createSimpleExpiring("Open Play Console \u2197") {
  //       val packagePath = packageName?.let { "package/$it" } ?: ""
  //       val url = playConsoleViaAccountChooserUrl(email, "https://play.google.com/console/$packagePath")
  //       BrowserUtil.browse(url)
  //     }
  //   )
  // }

  notification.notify(project)
}

private fun showUploadFailedNotification(project: Project, errorMessage: String) {
  NotificationGroupManager.getInstance()
    .getNotificationGroup("Play Publishing")
    .createNotification("Publishing failed", errorMessage, NotificationType.ERROR)
    .notify(project)
}

/** Navigate to play console via the account chooser link. This will direct the user to the right */
fun playConsoleViaAccountChooserUrl(email: String, continuationUrl: String): String {
  val encodedEmail = URLEncoder.encode(email, StandardCharsets.UTF_8)
  val encodedUrl = URLEncoder.encode(continuationUrl, StandardCharsets.UTF_8)
  return "https://accounts.google.com/AccountChooser?Email=$encodedEmail&continue=$encodedUrl"
}

private val TAG_REGEX = "<([a-zA-Z0-9_.-]+)>(.*?)</\\1>".toRegex(RegexOption.DOT_MATCHES_ALL)

/**
 * We want the release notes to be in this format: <lang1> Notes ... </lang1> <lang2> Notes2 ... </lang2>
 *
 * This function extracts the tags and the release notes content. Returns null otherwise signaling something is wrong.
 *
 * @return A map of tag names to their content, or null if the string contains invalid content.
 */
private fun extractAndValidateTags(xmlString: String): Map<String, String>? {
  val result = mutableMapOf<String, String>()
  var lastEnd = 0

  TAG_REGEX.findAll(xmlString).forEach { match ->
    // If there is text between 2 languages, return null
    if (xmlString.subSequence(lastEnd, match.range.first).isNotBlank()) {
      return null
    }

    result[match.groupValues[1]] = match.groupValues[2].trim()
    lastEnd = match.range.last + 1
  }

  // If there is text after the last language, return null
  if (xmlString.subSequence(lastEnd, xmlString.length).isNotBlank()) {
    return null
  }
  return result
}

private fun String.toTrackType(): TrackType =
  when (this) {
    INTERNAL_TEST_TRACK_NAME -> TrackType.INTERNAL_TESTING
    ALPHA_TRACK_NAME -> TrackType.ALPHA
    BETA_TRACK_NAME -> TrackType.BETA
    PRODUCTION_TRACK_NAME -> TrackType.PRODUCTION
    else -> TrackType.CUSTOM
  }

private suspend fun <T> runPublishingStep(
  trackType: TrackType,
  uploadTimeMs: Int? = null,
  mapException: (Exception) -> CreateReleaseResult,
  block: suspend () -> T,
): T {
  try {
    return block()
  } catch (e: CancellationException) {
    PlayPublishingUsageTracker.trackCreateRelease(CreateReleaseResult.FAILED_USER_CANCELLED, trackType, uploadTimeMs)
    throw e
  } catch (e: Exception) {
    PlayPublishingUsageTracker.trackCreateRelease(mapException(e), trackType, uploadTimeMs)
    throw e
  }
}

private suspend fun uploadBundleStep(packageName: String, editId: String, artifactPath: String, trackType: TrackType): Bundle {
  return runPublishingStep(
    trackType,
    mapException = { e ->
      val msg = e.message ?: ""
      when {
        e !is PlayPublishingException -> CreateReleaseResult.FAILED_TO_UPLOAD_BUNDLE
        msg.contains(VERSION_CODE_USED_REGEX) -> CreateReleaseResult.FAILED_VERSION_CODE_ALREADY_EXISTS
        msg.contains(WRONG_KEY_REGEX) -> CreateReleaseResult.FAILED_BUNDLE_SIGNED_WITH_WRONG_KEY
        else -> CreateReleaseResult.FAILED_TO_UPLOAD_BUNDLE
      }
    },
  ) {
    PlayPublishingClient.getInstance().uploadBundle(packageName, editId, artifactPath)
  }
}

private suspend fun createReleaseStep(
  packageName: String,
  editId: String,
  releaseName: String,
  tags: Map<String, String>,
  versionCode: Int,
  selectedTrackId: String,
  trackType: TrackType,
  uploadTimeMs: Int,
) {
  runPublishingStep(trackType, uploadTimeMs, mapException = { _ -> CreateReleaseResult.FAILED_TO_CREATE_RELEASE }) {
    PlayPublishingClient.getInstance().createRelease(packageName, editId, releaseName, tags, versionCode, selectedTrackId)
  }
}

private suspend fun commitEditStep(packageName: String, editId: String, trackType: TrackType, uploadTimeMs: Int) {
  runPublishingStep(
    trackType,
    uploadTimeMs,
    mapException = { e ->
      val msg = e.message ?: ""
      if (e is PlayPublishingException && msg.contains(NO_APP_LISTING_CORRECTION_MESSAGE)) {
        CreateReleaseResult.FAILED_RELEASE_NOT_ALLOWED_ON_TRACK
      } else {
        CreateReleaseResult.FAILED_TO_COMMIT_RELEASE
      }
    },
  ) {
    PlayPublishingClient.getInstance().commitEdit(packageName, editId)
  }
}
