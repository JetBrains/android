/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.screenshottest.ui.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.android.screenshottest.ScreenshotTestResultProto
import com.android.screenshottest.ui.diffviewer.ImageDiffContent
import com.android.screenshottest.ui.diffviewer.ThreesideImageDiffViewer
import com.intellij.diff.contents.DiffContent
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.panels.Wrapper
import org.intellij.images.editor.impl.ImageEditorManagerImpl
import org.jetbrains.jewel.ui.component.SegmentedControl
import org.jetbrains.jewel.ui.component.SegmentedControlButtonData
import org.jetbrains.jewel.ui.component.Text
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

// Enums to represent the different view states
enum class DiffView {
  All, New, Diff, Reference
}

/**
 * Create a Composable diff view UI. Please put this inside a Composable container
 */
@Composable
fun DiffViewUi(project: Project, testResultProto: ScreenshotTestResultProto) {
  var selectedView by remember { mutableStateOf(DiffView.All) }
  // Create button data for bottom SegmentedControl component
  val buttonData = remember(selectedView) {
    DiffView.values().map { viewId ->
      SegmentedControlButtonData(
        selected = viewId == selectedView,
        content = { _ -> Text(viewId.name) },
        onSelect = { selectedView = viewId },
      )
    }
  }

  // Main content area for the diff view
  Column(
    verticalArrangement = Arrangement.spacedBy(16.dp),
    modifier = Modifier.fillMaxSize()
  ) {
    // Container that automatically resizes for SwingPanel.
    // SwingPanel does not resize properly without this container
    Column(
      modifier = Modifier.weight(1f, true)
    ) {
      when (selectedView) {
        DiffView.All -> ThreeSideDiffVierPanel(project, testResultProto)
        DiffView.New -> SingleImageSwingPanel(ImageIO.read(File(testResultProto.newImageUrl)), selectedView.name)
        DiffView.Diff -> SingleImageSwingPanel(ImageIO.read(File(testResultProto.diffImageUrl)), selectedView.name)
        DiffView.Reference -> SingleImageSwingPanel(ImageIO.read(File(testResultProto.referenceImageUrl)), selectedView.name)
      }
    }
    // Separate ro for SegmentedControl to center the component
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
      modifier = Modifier.fillMaxWidth().wrapContentHeight()
    ) {
      SegmentedControl(buttons = buttonData, enabled = true)
    }
  }
}

/**
 * Creates a ThreesideImageDiffViewer
 *
 * @param testResultProto Test result from screenshot test, should contain three images
 */
@Composable
fun ThreeSideDiffVierPanel(project: Project, testResultProto: ScreenshotTestResultProto) =
  SwingPanel(
    background = inlineCodeBackgroundColor,
    modifier = Modifier.fillMaxSize(),
    factory = {
      val diffContentList: MutableList<DiffContent> = mutableListOf(
        ImageDiffContent(project, ImageIO.read(File(testResultProto.newImageUrl)), DiffView.New.name),
        ImageDiffContent(project, ImageIO.read(File(testResultProto.diffImageUrl)), DiffView.Diff.name),
        ImageDiffContent(project, ImageIO.read(File(testResultProto.referenceImageUrl)), DiffView.Reference.name)
      )
      val diffViewer = ThreesideImageDiffViewer(project, diffContentList)
      diffViewer.init()
      diffViewer.component
    }
  )

/**
 * Creates view for individual images with title
 * @param title Title of the image
 * @param bufferedImage Image data
 */
@Composable
fun SingleImageSwingPanel(bufferedImage: BufferedImage, title: String) {
  Text(
    text = title,
    modifier = Modifier.padding(8.dp),
  )
  SwingPanel(
    background = inlineCodeBackgroundColor,
    modifier = Modifier.fillMaxSize(),
    factory = {
      Wrapper(ImageEditorManagerImpl.createImageEditorUI(bufferedImage))
    }
  )
}

// Copied from org.intellij.plugins.markdown.ui.preview.PreviewLAFThemeStyles#createStylesheet
private val inlineCodeBackgroundColor
  get() =
    if (JBColor.isBright()) {
      Color(red = 212, green = 222, blue = 231, alpha = 255 / 4)
    }
    else {
      Color(red = 212, green = 222, blue = 231, alpha = 25)
    }
