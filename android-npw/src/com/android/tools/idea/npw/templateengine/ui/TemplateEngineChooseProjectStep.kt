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
package com.android.tools.idea.npw.templateengine.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.template.engine.TemplateDefinition
import com.android.tools.idea.npw.project.LeftSidePanel
import com.android.tools.idea.npw.templateengine.viewmodel.ChooseProjectViewModel
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.theme.defaultBannerStyle
import org.jetbrains.jewel.ui.theme.selectableLazyColumnStyle
import org.jetbrains.skia.Image as SkiaImage

@Composable
fun TemplateEngineChooseProjectStep(viewModel: ChooseProjectViewModel) {
  val categories = viewModel.categories
  val selectedCategory = viewModel.selectedCategory
  val errorMessage = viewModel.errorMessage

  Column(modifier = Modifier.fillMaxSize()) {
    if (errorMessage != null) {
      val errorBannerStyle = JewelTheme.defaultBannerStyle.error
      Box(
        modifier = Modifier.fillMaxWidth().background(errorBannerStyle.colors.background).padding(12.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(text = errorMessage, color = JewelTheme.globalColors.text.error)
      }
    }

    if (categories.isNotEmpty()) {
      Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
        LeftSidePanel(
          entries = categories,
          selectedEntry = selectedCategory,
          updateEntrySelected = { entry -> entry?.let { viewModel.selectedCategory = it } },
        )
        Divider(Orientation.Vertical, thickness = 1.dp, modifier = Modifier.fillMaxHeight())
        RightSidePanel(modifier = Modifier.weight(1f).fillMaxHeight(), viewModel = viewModel)
      }
    } else {
      Column(
        modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        val isSdkMissing = IdeSdks.getInstance().androidSdkPath == null
        val titleText =
          if (isSdkMissing) {
            "Android SDK is not configured"
          } else {
            "Project templates are missing or corrupt"
          }
        val descriptionText =
          errorMessage
            ?: if (isSdkMissing) {
              "An Android SDK is required to create new projects. Please configure the SDK path in settings."
            } else {
              "The template pack could not be located in the Android SDK directory."
            }

        Text(
          text = titleText,
          fontWeight = FontWeight.Bold,
          color = JewelTheme.globalColors.text.error,
          modifier = Modifier.padding(bottom = 8.dp),
        )
        Text(text = descriptionText, color = JewelTheme.globalColors.text.info, modifier = Modifier.padding(bottom = 16.dp))
        DefaultButton(onClick = { SdkQuickfixUtils.showAndroidSdkManager() }) {
          Text(if (isSdkMissing) "Setup SDK" else "Open SDK Manager")
        }
      }
    }
  }
}

@Composable
fun CategoryListCell(text: String, isSelected: Boolean, isFocused: Boolean) {
  val colors = JewelTheme.selectableLazyColumnStyle.simpleListItemStyle.colors
  val backgroundColor =
    when {
      isSelected && isFocused -> colors.backgroundSelectedActive
      isSelected -> colors.backgroundSelected
      isFocused -> colors.backgroundActive
      else -> colors.background
    }
  val contentColor =
    when {
      isSelected && isFocused -> colors.contentSelectedActive
      isSelected -> colors.contentSelected
      isFocused -> colors.contentActive
      else -> colors.content
    }
  Row(modifier = Modifier.width(260.dp).height(32.dp).background(backgroundColor), verticalAlignment = Alignment.CenterVertically) {
    Text(modifier = Modifier.padding(start = 20.dp), text = text, color = contentColor)
  }
}

@Composable
private fun RightSidePanel(modifier: Modifier = Modifier, viewModel: ChooseProjectViewModel) {
  val selectedCategory = viewModel.selectedCategory ?: return

  Box(modifier = modifier) { selectedCategory.AndroidProjectEntryDetails() }
}

private val thumbnailLogger = Logger.getInstance("TemplateEngineChooseProjectStep")

fun getTemplateThumbnail(template: TemplateDefinition, isDark: Boolean = false): ImageBitmap? {
  val thumbEntry =
    if (isDark) {
      template.extraFiles.firstOrNull { it.relativePath.endsWith("thumbnail_dark.png") || it.relativePath.endsWith("icon_dark.png") }
        ?: template.extraFiles.firstOrNull { it.relativePath.endsWith("thumbnail.png") || it.relativePath.endsWith("icon.png") }
    } else {
      template.extraFiles.firstOrNull { it.relativePath.endsWith("thumbnail.png") || it.relativePath.endsWith("icon.png") }
    } ?: return null

  return try {
    var bytes: ByteArray? = null
    template.loader.withLoader { loader ->
      val file = loader.loadFile(thumbEntry)
      if (file.content.isNotEmpty()) {
        bytes = file.content
      }
    }
    bytes?.let { SkiaImage.makeFromEncoded(it).toComposeImageBitmap() }
  } catch (e: Exception) {
    thumbnailLogger.warn("Missing or invalid preview asset for '${template.metadata.name}': ${e.message}")
    null
  }
}
