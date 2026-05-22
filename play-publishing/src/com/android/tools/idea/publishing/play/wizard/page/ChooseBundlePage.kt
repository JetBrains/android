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
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPainter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.tools.adtui.compose.LocalProject
import com.android.tools.adtui.compose.WizardAction
import com.android.tools.adtui.compose.WizardPageScope
import com.android.tools.idea.publishing.play.AppMetadata
import com.android.tools.idea.publishing.play.PlayPublishingUsageTracker
import com.android.tools.idea.publishing.play.client.PlayPublishingClient
import com.android.tools.idea.publishing.play.client.PlayPublishingException
import com.android.tools.idea.publishing.play.client.type.App
import com.android.tools.idea.publishing.play.extractAppMetadata
import com.android.tools.idea.publishing.play.wizard.PlayPublishingWizardHeader
import com.android.tools.idea.publishing.play.wizard.PlayPublishingWizardState
// TODO: android-merge; com.google.gct.login2 is tools/vendor/google/login, which this repository does not carry.
// import com.google.gct.login2.GoogleLoginService
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.LocalFileSystem
// TODO: android-merge; icons.GoogleLoginIcons is tools/vendor/google/login, which this repository does not
// carry, and com.intellij.util.ui.ImageUtil was only used by the avatar fallback that goes with it.
// import com.intellij.util.ui.ImageUtil
// import icons.GoogleLoginIcons
import java.awt.geom.Ellipse2D
import java.awt.image.BufferedImage
import java.nio.file.Path
import kotlin.io.path.Path
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
fun WizardPageScope.ChooseBundlePage(extractMetadata: suspend (Path) -> AppMetadata = ::extractAppMetadata) {
  // TODO: android-merge; the signed in user comes from com.google.gct.login2.GoogleLoginService in
  // tools/vendor/google/login, which this repository does not carry.
  // val user by GoogleLoginService.instance.activeUserFlow.collectAsState()
  val project = LocalProject.current
  val component = LocalComponent.current
  val state = getOrCreateState<PlayPublishingWizardState> { error("State not initialized") }
  val isPathLocked = !state.bundlePath.isNullOrEmpty()
  val initialBundlePath = remember { state.bundlePath ?: project?.guessProjectDir()?.path ?: "" }
  val bundlePathState = rememberTextFieldState(initialBundlePath)
  var errorMessage: String? by remember { mutableStateOf(null) }
  var isAppsLoading by remember { mutableStateOf(true) }
  val apps: List<App>? by
    produceState(initialValue = null) {
      isAppsLoading = true
      value =
        try {
          PlayPublishingClient.getInstance().listApps()
        } catch (e: PlayPublishingException) {
          errorMessage = "Failed to check package availability: ${e.message}"
          null
        } catch (e: Exception) {
          errorMessage = "Failed to check package availability: ${e.message}"
          null
        } finally {
          isAppsLoading = false
        }
    }
  val isAppInConsole = remember(state.packageName, apps) { apps?.any { it.packageName == state.packageName } ?: false }
  var versionCode: String? by remember { mutableStateOf(null) }
  var versionName: String? by remember { mutableStateOf(null) }
  var bannerData: BannerData? by remember { mutableStateOf(null) }
  var packageNameCheck: PackageNameCheck? by remember { mutableStateOf(null) }

  LaunchedEffect(apps, isAppInConsole, isAppsLoading, state.packageName, state.isRegistered) {
    bannerData =
      when {
        // Don't show any banner when we are still determining if the user has access to the app.
        isAppsLoading || state.packageName.isNullOrEmpty() -> null
        apps != null && state.isRegistered == true && !isAppInConsole -> {
          BannerData(
            ElementType.ERROR,
            """
      The package name (${state.packageName}) is not available. You can change your package name to another available name from Project Settings and rebuild the distributable.
    """
              .trimIndent(),
          )
        }
        isPathLocked -> {
          BannerData(ElementType.SUCCESS, "Field pre-filled from the 'Generate Signed App Bundle or APK' wizard.")
        }
        else -> null
      }

    packageNameCheck =
      when {
        isAppsLoading || apps == null -> null
        state.isRegistered == false && !state.packageName.isNullOrEmpty() -> {
          PackageNameCheck(ElementType.SUCCESS, "Package name available")
        }
        state.isRegistered == true && isAppInConsole -> {
          PackageNameCheck(ElementType.SUCCESS, "Matches existing app")
        }
        else -> null
      }
  }

  LaunchedEffect(bundlePathState.text) {
    val path = bundlePathState.text.toString()
    state.bundlePath = path
    if (errorMessage == "Failed to parse metadata. Please verify that the selected App Bundle (.aab) is valid and not corrupted.") {
      errorMessage = null
    }
    val metadata =
      try {
        extractMetadata(Path(path))
      } catch (e: Exception) {
        Logger.getInstance("ChooseBundlePage").warn("Failed to read metadata from bundle", e)
        errorMessage = "Failed to parse metadata. Please verify that the selected App Bundle (.aab) is valid and not corrupted."
        null
      }
    state.appName = metadata?.appName
    state.packageName = metadata?.packageName
    versionName = metadata?.versionName
    versionCode = metadata?.versionCode
  }

  val fileChooserDescriptor = remember {
    FileChooserDescriptor(true, false, false, false, false, false).withFileFilter { it.extension?.lowercase() == "aab" }
  }

  // TODO: android-merge; the avatar is built from the signed in user and from the fallback icon in
  // tools/vendor/google/login, which this repository does not carry.
  // val avatarPainter =
  //   remember(user) {
  //     val icon =
  //       user?.picture?.let { src ->
  //         val width = 64
  //         val height = 64
  //         val dest = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
  //         val g2d = dest.createGraphics()
  //         g2d.clip(Ellipse2D.Float(0f, 0f, width.toFloat(), height.toFloat()))
  //         g2d.drawImage(src, 0, 0, width, height, null)
  //         g2d.dispose()
  //         dest
  //       }
  //         ?: run {
  //           val fallbackIcon = GoogleLoginIcons.LOGGED_IN_FALLBACK_USER_AVATAR
  //           val width = fallbackIcon.iconWidth
  //           val height = fallbackIcon.iconHeight
  //           val image = ImageUtil.createImage(width, height, BufferedImage.TYPE_INT_ARGB)
  //           val g = image.createGraphics()
  //           fallbackIcon.paintIcon(null, g, 0, 0)
  //           g.dispose()
  //           image
  //         }
  //     icon.toPainter()
  //   }

  Column(modifier = Modifier.fillMaxSize()) {
    PlayPublishingWizardHeader(subtitle = "Choose App Bundle")
    Column(modifier = Modifier.weight(1f).padding(24.dp).focusTarget()) {
      // User Info
      // TODO: android-merge; the signed in account and its avatar come from tools/vendor/google/login, which
      // this repository does not carry.
      // Row(verticalAlignment = Alignment.CenterVertically) {
      //   Image(painter = avatarPainter, contentDescription = null, modifier = Modifier.size(24.dp).clip(RoundedCornerShape(12.dp)))
      //   Spacer(modifier = Modifier.width(8.dp))
      //   Text("Signed in as: ", style = JewelTheme.defaultTextStyle.copy(fontSize = 13.sp))
      //   Text(
      //     text = user?.email ?: throw IllegalStateException("Logged in user not found"),
      //     style = JewelTheme.defaultTextStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
      //   )
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
          enabled = !isPathLocked,
          trailingIcon =
            if (isPathLocked) null
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
            ElementType.SUCCESS -> InlineSuccessBanner(it.message)
            ElementType.ERROR -> InlineErrorBanner(it.message)
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

        if (bannerData?.type != ElementType.ERROR) {
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

    errorMessage?.let { InlineErrorBanner(it, Modifier.align(Alignment.End).padding(24.dp)) }
  }

  prevButtonEnabled = false
  nextActionName = "Next"
  nextAction =
    if (state.packageName.isNullOrEmpty() || (state.isRegistered == true && !isAppInConsole)) WizardAction.Disabled
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

private enum class ElementType {
  SUCCESS,
  ERROR,
}

private data class BannerData(val type: ElementType, val message: String)

private data class PackageNameCheck(val type: ElementType, val message: String)
