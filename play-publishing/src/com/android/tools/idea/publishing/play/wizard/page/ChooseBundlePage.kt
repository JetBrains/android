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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.tools.adtui.compose.LocalProject
import com.android.tools.adtui.compose.WizardAction
import com.android.tools.adtui.compose.WizardPageScope
import com.android.tools.idea.publishing.AdiClient
import com.android.tools.idea.publishing.RegistrationState
import com.android.tools.idea.publishing.play.AppMetadata
import com.android.tools.idea.publishing.play.PlayPublishingUsageTracker
import com.android.tools.idea.publishing.play.client.PlayPublishingClient
import com.android.tools.idea.publishing.play.client.type.App
import com.android.tools.idea.publishing.play.extractAppMetadata
import com.android.tools.idea.publishing.play.wizard.PlayPublishingWizardHeader
import com.android.tools.idea.publishing.play.wizard.PlayPublishingWizardState
// AND-142 JetBrains patch (Google Play Publishing): com.google.gct.login2 is tools/vendor/google/login, which this repository does not carry.
// import com.google.gct.login2.GoogleLoginService
// import com.google.gct.login2.ui.GoogleLoginUserRow
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.toNioPathOrNull
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.extension
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.LocalComponent
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.ExternalLink
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.InlineErrorBanner
import org.jetbrains.jewel.ui.component.InlineSuccessBanner
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.icons.AllIconsKeys

private const val UPLOAD_BUNDLE_DAC_URL = "https://developer.android.com/r/studio-ui/publish/upload-app-bundle"

@Suppress("UnstableApiUsage")
@OptIn(ExperimentalFoundationApi::class, ExperimentalJewelApi::class)
@Composable
fun WizardPageScope.ChooseBundlePage(
  createAdiClient: (CoroutineScope) -> AdiClient = { AdiClient(it) },
  shouldExtractMetadata: suspend (Path) -> Boolean = ::shouldExtractMetadata,
  extractMetadata: suspend (Path) -> AppMetadata = ::extractAppMetadata,
) {
  // AND-142 JetBrains patch (Google Play Publishing): the signed in user comes from com.google.gct.login2.GoogleLoginService in
  // tools/vendor/google/login, which this repository does not carry.
  // val user by GoogleLoginService.instance.activeUserFlow.collectAsState()
  val project = LocalProject.current
  val adiClient = remember(coroutineScope) { createAdiClient(coroutineScope) }
  val component = LocalComponent.current
  val state = getOrCreateState<PlayPublishingWizardState> { error("State not initialized") }
  val pageState = getOrCreateState { ChooseBundlePageState(isPathLocked = !state.bundlePath.isNullOrEmpty()) }
  val initialBundlePath = remember { state.bundlePath ?: project?.guessBuildPath() ?: "" }
  val bundlePathState = rememberTextFieldState(initialBundlePath)
  val listAppsResult: ListAppsResult by
    produceState<ListAppsResult>(initialValue = ListAppsResult.Loading) {
      value =
        try {
          val apps = PlayPublishingClient.getInstance().listApps()
          ListAppsResult.Success(apps)
        } catch (e: Exception) {
          ListAppsResult.Error("Failed to check package availability: ${e.message}")
        }
    }
  val isAppInConsole =
    remember(state.packageName, listAppsResult) {
      (listAppsResult as? ListAppsResult.Success)?.apps?.any { it.packageName == state.packageName } ?: false
    }
  var metadataResult by pageState::metadataResult
  fun updateMetadataResult(result: MetadataResult) {
    metadataResult = result
    if (result is MetadataResult.Success) {
      state.appName = result.metadata.appName
      state.packageName = result.metadata.packageName
    } else {
      state.appName = null
      state.packageName = null
    }
  }
  val metadata = (metadataResult as? MetadataResult.Success)?.metadata
  val versionName = metadata?.versionName
  val versionCode = metadata?.versionCode

  var registrationResult: RegistrationResult by
    remember(state.packageName) {
      mutableStateOf(
        if (pageState.isPathLocked && state.isRegistered != null) {
          RegistrationResult.Success(state.isRegistered)
        } else {
          RegistrationResult.Loading
        }
      )
    }

  LaunchedEffect(state.packageName, adiClient) {
    if (pageState.isPathLocked && state.isRegistered != null) {
      registrationResult = RegistrationResult.Success(state.isRegistered)
      return@LaunchedEffect
    }
    val packageName = state.packageName
    if (packageName.isNullOrEmpty()) {
      state.isRegistered = null
      registrationResult = RegistrationResult.Success(null)
      return@LaunchedEffect
    }
    registrationResult = RegistrationResult.Loading

    try {
      val (resultMap, _) = withContext(Dispatchers.IO) { adiClient.checkPackageRegistrationStatus(setOf(packageName), null) }
      val isReg =
        when (resultMap[packageName]) {
          RegistrationState.REGISTERED -> true
          RegistrationState.NOT_REGISTERED -> false
          else -> null
        }
      state.isRegistered = isReg
      registrationResult = RegistrationResult.Success(isReg)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Logger.getInstance("ChooseBundlePage").warn("Failed to check package registration status", e)
      state.isRegistered = null
      registrationResult = RegistrationResult.Success(null)
    }
  }

  val bundleState =
    remember(metadataResult, listAppsResult, state.packageName, registrationResult, isAppInConsole) {
      val registrationResult = registrationResult
      when (metadataResult) {
        is MetadataResult.Idle,
        is MetadataResult.Loading -> BundleState.Loading
        is MetadataResult.InvalidPath -> BundleState.InvalidPath
        is MetadataResult.ParseError -> BundleState.ParseError
        is MetadataResult.Success -> {
          when (listAppsResult) {
            is ListAppsResult.Loading -> BundleState.Loading
            is ListAppsResult.Error -> BundleState.Empty
            is ListAppsResult.Success -> {
              when (registrationResult) {
                is RegistrationResult.Loading -> BundleState.Loading
                is RegistrationResult.Success -> {
                  val isRegistered = registrationResult.isRegistered
                  when {
                    state.packageName.isNullOrEmpty() -> BundleState.Empty
                    isRegistered == true && !isAppInConsole -> BundleState.PackageNotAvailable
                    metadata?.isSigned == false -> BundleState.Unsigned
                    metadata?.isDebug == true -> BundleState.Debug
                    else -> BundleState.Valid
                  }
                }
              }
            }
          }
        }
      }
    }

  val packageNameCheck =
    remember(metadataResult, listAppsResult, state.packageName, registrationResult, isAppInConsole) {
      val isRegistered = (registrationResult as? RegistrationResult.Success)?.isRegistered
      when {
        listAppsResult !is ListAppsResult.Success || metadataResult !is MetadataResult.Success -> null
        isRegistered == false && !state.packageName.isNullOrEmpty() -> {
          PackageNameCheck(ElementType.SUCCESS, "Package name available")
        }
        isRegistered == true && isAppInConsole -> {
          PackageNameCheck(ElementType.SUCCESS, "Matches existing app")
        }
        else -> null
      }
    }

  LaunchedEffect(bundlePathState.text) {
    val bundlePath = bundlePathState.text.toString()
    val path =
      try {
        Path(bundlePath)
      } catch (_: Exception) {
        updateMetadataResult(MetadataResult.InvalidPath)
        return@LaunchedEffect
      }
    if (!shouldExtractMetadata(path)) {
      updateMetadataResult(MetadataResult.InvalidPath)
      return@LaunchedEffect
    }
    state.bundlePath = bundlePath
    updateMetadataResult(MetadataResult.Loading)
    val metadata =
      try {
        extractMetadata(path)
      } catch (e: Exception) {
        Logger.getInstance("ChooseBundlePage").warn("Failed to read metadata from bundle", e)
        updateMetadataResult(MetadataResult.ParseError)
        null
      }

    if (metadata != null) {
      updateMetadataResult(MetadataResult.Success(metadata))
    } else {
      updateMetadataResult(MetadataResult.ParseError)
    }
  }

  val fileChooserDescriptor = remember {
    FileChooserDescriptor(true, false, false, false, false, false).withFileFilter { it.extension?.lowercase() == "aab" }
  }

  val bannerData =
    when (bundleState) {
      BundleState.ParseError ->
        BannerData(
          ElementType.ERROR,
          "Failed to parse metadata. Please verify that the selected App Bundle (.aab) is valid and not corrupted.",
        )
      BundleState.PackageNotAvailable ->
        BannerData(
          ElementType.ERROR,
          buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("The package name (${state.packageName}) is not available. ") }
            append("You can change your package name to another available name from Project Settings and rebuild the distributable.")
          },
        )
      BundleState.Unsigned ->
        BannerData(
          ElementType.ERROR,
          buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("App is unsigned. ") }
            append(
              "Unsigned apps cannot be uploaded to Google Play. You can generate a signed release build via Build > Generate Signed App Bundle or APK."
            )
          },
        )
      BundleState.Debug ->
        BannerData(
          ElementType.ERROR,
          buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Build type is incorrect. ") }
            append(
              "Your app needs to be built for release. You can generate a signed release build via Build > Generate Signed App Bundle or APK."
            )
          },
        )
      BundleState.Valid ->
        if (pageState.isPathLocked) BannerData(ElementType.SUCCESS, "Field pre-filled from the 'Generate Signed App Bundle or APK' wizard.")
        else null
      BundleState.Loading,
      BundleState.Empty,
      BundleState.InvalidPath -> null
    }

  Column(modifier = Modifier.fillMaxSize()) {
    PlayPublishingWizardHeader(subtitle = "Choose App Bundle")
    Column(modifier = Modifier.weight(1f).padding(24.dp).focusTarget()) {
      // User Info
      // AND-142 JetBrains patch (Google Play Publishing): the signed in account row comes from
      // com.google.gct.login2.ui.GoogleLoginUserRow in tools/vendor/google/login, which this repository does not carry.
      // Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      //   Text("Signed in as: ", style = JewelTheme.defaultTextStyle.copy(fontSize = 13.sp))
      //   GoogleLoginUserRow(user = user ?: throw IllegalStateException("Logged in user not found"))
      // }
      //
      // Spacer(modifier = Modifier.height(24.dp))

      Text(
        text = "Select the App Bundle (.aab) you want to upload to Google Play.",
        style = JewelTheme.defaultTextStyle.copy(fontSize = 13.sp),
      )

      Spacer(modifier = Modifier.height(16.dp))

      // Bundle path field
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = "App bundle:", modifier = Modifier.width(100.dp), style = JewelTheme.defaultTextStyle.copy(fontSize = 13.sp))
        TextField(
          state = bundlePathState,
          modifier = Modifier.weight(1f),
          enabled = !pageState.isPathLocked,
          trailingIcon =
            if (pageState.isPathLocked) null
            else {
              {
                Icon(
                  key = AllIconsKeys.General.OpenDisk,
                  contentDescription = "Browse",
                  modifier =
                    Modifier.padding(end = 4.dp).pointerHoverIcon(PointerIcon.Hand).clickable {
                      val currentPath = bundlePathState.text.toString()
                      val toSelect =
                        (if (currentPath.isNotBlank()) LocalFileSystem.getInstance().findFileByPath(currentPath) else null)
                          ?: project?.guessProjectDir()
                      val virtualFile = FileChooser.chooseFile(fileChooserDescriptor, component, project, toSelect)
                      if (virtualFile != null) {
                        bundlePathState.setTextAndPlaceCursorAtEnd(virtualFile.toNioPath().toString())
                      }
                    },
                )
              }
            },
        )
      }

      bannerData?.let {
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          // Match the width of the spacer with the label above to align the banner with the path field
          Spacer(Modifier.width(100.dp))
          when (it.type) {
            ElementType.SUCCESS -> InlineSuccessBanner(modifier = Modifier.weight(1f)) { Text(it.message) }
            ElementType.ERROR -> InlineErrorBanner(modifier = Modifier.weight(1f)) { Text(it.message) }
          }
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Bundle Details
      Column(modifier = Modifier.padding(start = 100.dp)) {
        // Package Name
        Row(modifier = Modifier.testTag("PackageNameRow"), verticalAlignment = Alignment.CenterVertically) {
          Text(text = "Package name", modifier = Modifier.width(100.dp), style = JewelTheme.defaultTextStyle.copy(fontSize = 13.sp))
          Text(text = state.packageName.takeIf { !it.isNullOrEmpty() } ?: "—", style = JewelTheme.defaultTextStyle.copy(fontSize = 13.sp))
          packageNameCheck?.let {
            Spacer(modifier = Modifier.width(8.dp))
            when (it.type) {
              ElementType.SUCCESS -> Icon(key = AllIconsKeys.Status.Success, contentDescription = null, modifier = Modifier.size(14.dp))
              ElementType.ERROR -> Icon(key = AllIconsKeys.General.Error, contentDescription = null, modifier = Modifier.size(14.dp))
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = it.message, style = JewelTheme.defaultTextStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold))
          }
        }

        if (bannerData?.type != ElementType.ERROR && !state.packageName.isNullOrEmpty()) {
          Spacer(modifier = Modifier.height(4.dp))
          Column(modifier = Modifier.padding(start = 100.dp)) {
            Text(
              text = "This wizard will guide you through uploading a new release for this application.",
              style = JewelTheme.defaultTextStyle.copy(fontSize = 12.sp, color = Color.Gray),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
              ExternalLink("Learn more", UPLOAD_BUNDLE_DAC_URL, modifier = Modifier.pointerHoverIcon(PointerIcon.Hand))
            }
          }
          Spacer(modifier = Modifier.height(16.dp))
        } else {
          Spacer(modifier = Modifier.height(8.dp))
        }

        // Version Name
        Row(Modifier.testTag("VersionNameRow")) {
          Text(text = "Version name", modifier = Modifier.width(100.dp), style = JewelTheme.defaultTextStyle.copy(fontSize = 13.sp))
          Text(text = versionName.takeIf { !it.isNullOrEmpty() } ?: "—", style = JewelTheme.defaultTextStyle.copy(fontSize = 13.sp))
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Version Code
        Row(Modifier.testTag("VersionCodeRow")) {
          Text(text = "Version code", modifier = Modifier.width(100.dp), style = JewelTheme.defaultTextStyle.copy(fontSize = 13.sp))
          Text(text = versionCode.takeIf { !it.isNullOrEmpty() } ?: "—", style = JewelTheme.defaultTextStyle.copy(fontSize = 13.sp))
        }
      }
    }

    (listAppsResult as? ListAppsResult.Error)?.let {
      InlineErrorBanner(it.message, Modifier.align(Alignment.End).padding(24.dp).fillMaxWidth())
    }
  }

  nextActionName = "Next"
  nextAction =
    if (!bundleState.isNextEnabled) WizardAction.Disabled
    else
      WizardAction {
        PlayPublishingUsageTracker.trackChooseBundle(
          isPackageRegistered = state.isRegistered,
          isAppNameRead = !state.appName.isNullOrEmpty(),
          isPackageNameRead = !state.packageName.isNullOrEmpty(),
          isVersionCodeRead = !versionCode.isNullOrEmpty(),
          isVersionNameRead = !versionName.isNullOrEmpty(),
        )
        if (isAppInConsole) {
          pushPage { CreateReleasePage() }
        } else {
          pushPage { CreateAppRecordPage() }
        }
      }
}

internal suspend fun shouldExtractMetadata(path: Path) =
  withContext(Dispatchers.IO) { path.extension.lowercase() == "aab" && Files.isRegularFile(path) }

private fun Project.guessBuildPath(): String? {
  val projectDir = guessProjectDir()?.toNioPathOrNull() ?: return guessProjectDir()?.path
  val appDir = projectDir.resolve("app")
  return if (Files.isDirectory(appDir)) {
    appDir.toAbsolutePath().toString()
  } else {
    projectDir.toAbsolutePath().toString()
  }
}

private enum class ElementType {
  SUCCESS,
  ERROR,
}

private data class BannerData(val type: ElementType, val message: AnnotatedString) {
  constructor(type: ElementType, text: String) : this(type, buildAnnotatedString { append(text) })
}

private class ChooseBundlePageState(val isPathLocked: Boolean) {
  var metadataResult by mutableStateOf<MetadataResult>(MetadataResult.Idle)
}

private data class PackageNameCheck(val type: ElementType, val message: String)

private enum class BundleState(val isNextEnabled: Boolean = false) {
  Valid(isNextEnabled = true),
  InvalidPath,
  ParseError,
  PackageNotAvailable,
  Unsigned,
  Debug,
  Loading,
  Empty,
}

private sealed interface ListAppsResult {
  data object Loading : ListAppsResult

  data class Success(val apps: List<App>) : ListAppsResult

  data class Error(val message: String) : ListAppsResult
}

private sealed interface MetadataResult {
  data object Idle : MetadataResult

  data object Loading : MetadataResult

  data object InvalidPath : MetadataResult

  data object ParseError : MetadataResult

  data class Success(val metadata: AppMetadata) : MetadataResult
}

private sealed interface RegistrationResult {
  data object Loading : RegistrationResult

  data class Success(val isRegistered: Boolean?) : RegistrationResult
}
