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
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.android.tools.idea.publishing.play.extractAppMetadata
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
import icons.StudioIllustrationsCompose
import java.awt.geom.Ellipse2D
import java.awt.image.BufferedImage
import java.nio.file.Path
import kotlin.io.path.Path
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.LocalComponent
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.ExternalLink
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.InlineSuccessBanner
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.theme.inlineBannerStyle

private const val UPLOAD_BUNDLE_DAC_URL = "https://developer.android.com/r/studio-ui/publish/upload-app-bundle"

@Suppress("UnstableApiUsage")
@OptIn(ExperimentalFoundationApi::class, ExperimentalJewelApi::class)
@Composable
fun WizardPageScope.ChooseArtifactPage(extractMetadata: suspend (Path) -> AppMetadata = ::extractAppMetadata) {
  // TODO: android-merge; the signed in user comes from com.google.gct.login2.GoogleLoginService in
  // tools/vendor/google/login, which this repository does not carry.
  // val user by GoogleLoginService.instance.activeUserFlow.collectAsState()
  val project = LocalProject.current
  val component = LocalComponent.current
  val state = getOrCreateState<PlayPublishingWizardState> { error("State not initialized") }
  val isPathLocked = !state.artifactPath.isNullOrEmpty()
  val initialArtifactPath = remember { state.artifactPath ?: project?.guessProjectDir()?.path ?: "" }
  val artifactPathState = rememberTextFieldState(initialArtifactPath)
  var versionCode: String? by remember { mutableStateOf(null) }
  var versionName: String? by remember { mutableStateOf(null) }

  LaunchedEffect(artifactPathState.text) {
    val path = artifactPathState.text.toString()
    state.artifactPath = path
    val metadata =
      try {
        extractMetadata(Path(path))
      } catch (e: Exception) {
        Logger.getInstance("ChooseArtifactPage").warn("Failed to read metadata from artifact", e)
        null
      }
    state.appName = metadata?.appName
    state.packageName = metadata?.packageName
    versionName = metadata?.versionName
    versionCode = metadata?.versionCode
  }

  val fileChooserDescriptor = remember {
    FileChooserDescriptor(true, false, false, false, false, false).withFileFilter { it.extension?.lowercase() in listOf("aab", "apk") }
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

  Column(modifier = Modifier.fillMaxSize().padding(24.dp).focusTarget()) {
    // Header
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(key = StudioIllustrationsCompose.Common.PlayConsoleIcon, contentDescription = null, modifier = Modifier.size(24.dp))
      Spacer(modifier = Modifier.width(12.dp))
      Text(text = "Upload to Play", style = JewelTheme.defaultTextStyle.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold))
      Spacer(modifier = Modifier.width(8.dp))
      Text(text = "Choose App Bundle or APK", style = JewelTheme.defaultTextStyle.copy(fontSize = 18.sp, color = Color.Gray))
    }

    Spacer(modifier = Modifier.height(24.dp))

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
      text = "Select the App Bundle (.aab) or APK you want to upload to Google Play.",
      style = JewelTheme.defaultTextStyle.copy(fontSize = 13.sp),
    )

    Spacer(modifier = Modifier.height(16.dp))

    // Info Banner
    InlineSuccessBanner(modifier = Modifier.fillMaxWidth(), style = JewelTheme.inlineBannerStyle.success) {
      Text(text = "Path pre-filled from the 'Generate Signed App Bundle or APK' wizard.")
    }

    Spacer(modifier = Modifier.height(32.dp))

    // Artifact path field
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(text = "App bundle or APK:", modifier = Modifier.width(150.dp), style = JewelTheme.defaultTextStyle.copy(fontSize = 13.sp))
      TextField(
        state = artifactPathState,
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
                    val currentPath = artifactPathState.text.toString()
                    val toSelect =
                      (if (currentPath.isNotBlank()) LocalFileSystem.getInstance().findFileByPath(currentPath) else null)
                        ?: project?.guessProjectDir()
                    val virtualFile = FileChooser.chooseFile(fileChooserDescriptor, component, project, toSelect)
                    if (virtualFile != null) {
                      artifactPathState.setTextAndPlaceCursorAtEnd(virtualFile.toNioPath().toString())
                    }
                  },
              )
            }
          },
      )
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Artifact Details
    Column(modifier = Modifier.padding(start = 150.dp)) {
      // Package Name
      Row(modifier = Modifier.testTag("PackageNameRow"), verticalAlignment = Alignment.CenterVertically) {
        Text(text = "Package name", modifier = Modifier.width(100.dp), style = JewelTheme.defaultTextStyle.copy(fontSize = 13.sp))
        Text(text = state.packageName.takeIf { !it.isNullOrEmpty() } ?: "—", style = JewelTheme.defaultTextStyle.copy(fontSize = 13.sp))
      }

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

  prevButtonEnabled = false
  nextActionName = "Next"
  nextAction = if (state.packageName.isNullOrEmpty() || state.appName.isNullOrEmpty()) WizardAction.Disabled else WizardAction { close() }
}
