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
package com.android.tools.idea.testartifacts.instrumented.testsuite.view

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.JourneyActionArtifacts
import icons.StudioIconsCompose
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun JourneysResultsView(
  modifier: Modifier = Modifier,
  artifact: JourneyActionArtifacts,
  index: Int,
  numEntries: Int,
  onImageDoubleClicked: () -> Unit = {},
) {
  Row(modifier = modifier.height(160.dp).padding(horizontal = 16.dp)) {
    val path = artifact.screenshotImage
    if (path != null) {
      DoubleClickableWrapper(modifier = Modifier, onDoubleClick = onImageDoubleClicked) {
        JourneyScreenshot(modifier = Modifier, path = path)
      }
    }
    else {
      Tooltip(modifier = Modifier, tooltip = { Text("No screenshot available") }) {
        Box(
          modifier = Modifier.aspectRatio(0.5f).fillMaxHeight().background(color = Color.LightGray)
        )
      }
    }

    Spacer(modifier = Modifier.width(12.dp))

    Column {
      val textScrollState = rememberScrollState()
      Box {
        Column(
          modifier = Modifier.verticalScroll(textScrollState),
          verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Top),
        ) {
          Row {
            ArtifactText(
              modifier = Modifier.weight(1f, fill = true),
              title = "Action Taken",
              text = artifact.description ?: "No action",
            )
            if (numEntries > 1) {
              StepCounter(modifier = Modifier, index = index, numEntries = numEntries)
            }
          }
          ArtifactText(
            modifier = Modifier,
            title = "Reasoning",
            text = artifact.reasoning ?: "None",
          )
        }
        VerticalScrollbar(
          modifier = Modifier.fillMaxHeight().align(Alignment.TopEnd),
          adapter = rememberScrollbarAdapter(textScrollState),
        )
      }
    }
  }
}

@Composable
fun JourneysResultsViewCompact(
  modifier: Modifier = Modifier,
  artifact: JourneyActionArtifacts,
  index: Int,
  numEntries: Int,
  onImageDoubleClicked: (() -> Unit)? = null
) {
  Column(modifier = modifier.padding(16.dp)) {
    Row(modifier = Modifier.weight(1f, fill = true)) {
      val path = artifact.screenshotImage
      if (path != null) {
        if (onImageDoubleClicked != null) {
          DoubleClickableWrapper(
            modifier = Modifier.fillMaxHeight(),
            onDoubleClick = onImageDoubleClicked,
          ) {
            JourneyScreenshot(modifier = Modifier.widthIn(max = 160.dp), path = path)
          }
        } else {
          JourneyScreenshot(modifier = Modifier.widthIn(min = 0.dp, max = 160.dp), path = path)
        }
        Spacer(modifier = Modifier.width(12.dp))
      }

      val textScrollState = rememberScrollState()
      Box {
        Column(
          modifier = Modifier.widthIn(max = 400.dp).verticalScroll(textScrollState),
          verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Top),
        ) {
          ArtifactText(
            modifier = Modifier,
            title = "Action Taken",
            text = artifact.description ?: "No action",
          )
          ArtifactText(
            modifier = Modifier,
            title = "Reasoning",
            text = artifact.reasoning ?: "None",
          )
        }
        VerticalScrollbar(
          modifier = Modifier.fillMaxHeight().align(Alignment.TopEnd),
          adapter = rememberScrollbarAdapter(textScrollState),
        )
      }
    }

    Spacer(modifier = Modifier.height(12.dp))

    if (numEntries > 1) {
      StepCounter(
        modifier = Modifier.align(Alignment.CenterHorizontally),
        index = index,
        numEntries = numEntries,
      )
    }
  }
}

@Composable
fun DoubleClickableWrapper(
  modifier: Modifier,
  onDoubleClick: () -> Unit,
  content: @Composable () -> Unit,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isHovered by interactionSource.collectIsHoveredAsState()
  var lastClickTime by remember { mutableLongStateOf(0L) }
  val doubleClickTimeout = 300L
  Box(
    modifier =
      modifier
        .pointerInput(Unit) {
          awaitPointerEventScope {
            while (true) {
              val event = awaitPointerEvent(PointerEventPass.Initial)
              val currentTime = System.currentTimeMillis()
              if (event.changes.any { it.pressed }) {
                if (currentTime - lastClickTime < doubleClickTimeout) {
                  onDoubleClick()
                  lastClickTime = 0L
                } else {
                  lastClickTime = currentTime
                }
              }
            }
          }
        }
        .clickable(enabled = false) {}
        .hoverable(interactionSource = interactionSource)
  ) {
    content()
    if (isHovered) {
      Box(
        modifier = Modifier
        .matchParentSize()
        .background(Color(red = 255, green = 255, blue = 255, alpha = 240))
        .padding(2.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = "Double click to open",
          style = TextStyle(
            fontWeight = FontWeight(500),
            color = JewelTheme.globalColors.text.info,
            textAlign = TextAlign.Center
          ),
        )
      }
    }
  }
}

@OptIn(ExperimentalResourceApi::class)
@Composable
fun JourneyScreenshot(modifier: Modifier, path: String) {
  var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
  var isLoading by remember { mutableStateOf(true) }
  var error by remember { mutableStateOf(false) }
  val file = File(path)

  LaunchedEffect(path) {
    isLoading = true
    error = false
    imageBitmap = null
    withContext(Dispatchers.IO) {
      try {
        val bytes = file.inputStream().readAllBytes()
        imageBitmap = bytes.decodeToImageBitmap()
      } catch (e: Exception) {
        error = true
      } finally {
        isLoading = false
      }
    }
  }
  val bitmap = imageBitmap
  Box(modifier = modifier, contentAlignment = Alignment.Center) {
    when {
      isLoading -> LoadingPlaceholder(modifier = Modifier)
      error -> ScreenshotError(modifier = Modifier, screenshotPath = path)
      bitmap != null ->
        Image(
          modifier = Modifier,
          bitmap = bitmap,
          contentDescription = "Screenshot of app prior to action",
        )
    }
  }
}

@Composable
private fun LoadingPlaceholder(modifier: Modifier) {
  Box(
    modifier = modifier.aspectRatio(0.5f).background(color = Color.LightGray),
    contentAlignment = Alignment.Center,
  ) {
    CircularProgressIndicator()
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScreenshotError(modifier: Modifier, screenshotPath: String) {
  Tooltip(
    modifier = modifier,
    tooltip = { Text("Error loading screenshot at path: $screenshotPath") },
  ) {
    Box(
      modifier =
        modifier.testTag("ScreenshotError").aspectRatio(0.5f).background(color = Color.LightGray),
      contentAlignment = Alignment.Center,
    ) {
      Icon(key = StudioIconsCompose.Common.Error, contentDescription = null)
    }
  }
}

@Composable
private fun ArtifactText(modifier: Modifier, title: String, text: String) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(
      title,
      style =
        TextStyle(
          fontSize = 13.sp,
          lineHeight = 16.sp,
          fontWeight = FontWeight(500),
        ),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Text(text)
  }
}

@Composable
private fun StepCounter(modifier: Modifier, index: Int, numEntries: Int) {
  Text(
    "Step ${index + 1} of $numEntries",
    style =
    TextStyle(
      fontSize = 13.sp,
      lineHeight = 16.sp,
      fontWeight = FontWeight(500),
      color = JewelTheme.globalColors.text.info
    ),
    modifier = modifier,
  )
}
