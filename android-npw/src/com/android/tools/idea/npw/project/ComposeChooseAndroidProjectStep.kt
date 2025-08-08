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
package com.android.tools.idea.npw.project

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.tools.idea.npw.project.ChooseAndroidProjectStep.Companion.getTemplateTitle
import com.android.tools.idea.wizard.template.Template
import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import icons.StudioIconsCompose
import icons.StudioIllustrationsCompose
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.foundation.lazy.SelectableLazyColumn
import org.jetbrains.jewel.foundation.lazy.SelectionMode
import org.jetbrains.jewel.foundation.lazy.itemsIndexed
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextArea
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.icon.IconKey

// This should be unified with com.android.studio.ml.bot.ui.compose.timeline.emptystate.Greeting.kt
internal val brandColor1 = Color(0xFF3186FF)
internal val brandColor2 = Color(0xFF346BF1)
internal val brandColor3 = Color(0xFF4FA0FF)
val colors =
  listOf(
    brandColor1,
    brandColor2,
    brandColor3,
    brandColor3,
    brandColor2,
    brandColor1,
    brandColor2,
    brandColor3,
    Color.Transparent,
    Color.Transparent,
  )
val stops = listOf(0f, .09f, .2f, .24f, .35f, .44f, .5f, .56f, .75f, 1f)
private val templateCellSize = 192.dp

@Composable
fun ChooseAndroidProjectStepUI(model: ChooseAndroidProjectStepModel) {
  val isLoading by model::isLoading
  val selectedEntry by model::selectedAndroidProjectEntry
  val entries by model::chooseAndroidProjectEntries

  if (isLoading) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      CircularProgressIndicator()
    }
  } else if (entries.isNotEmpty()) {
    Row(modifier = Modifier.fillMaxSize()) {
      LeftSidePanel(
        entries = entries,
        updateEntrySelected = { entry -> model.updateSelectedCell(entry) },
      )
      Divider(Orientation.Vertical, thickness = 1.dp, modifier = Modifier.fillMaxHeight())
      RightSidePanel(selectedEntry)
    }
  }
}

@Composable
private fun LeftSidePanel(
  entries: List<ChooseAndroidProjectEntry>,
  updateEntrySelected: (ChooseAndroidProjectEntry?) -> Unit,
) {
  val focusRequester = remember { FocusRequester() }

  LaunchedEffect(Unit) { focusRequester.requestFocus() }

  Column {
    Text(
      modifier = Modifier.background(UIUtil.getListBackground().toComposeColor()).padding(20.dp),
      text = "Templates",
      color = JBColor(0x999999, 0x787878).toComposeColor(),
    )

    SelectableLazyColumn(
      modifier =
        Modifier.testTag(ChooseAndroidProjectStepLayoutTags.LeftPanel.column)
          .focusRequester(focusRequester),
      selectionMode = SelectionMode.Single,
      onSelectedIndexesChange = { newSelectedList ->
        val newSelectedCell = newSelectedList.firstOrNull()
        updateEntrySelected(if (newSelectedCell != null) entries[newSelectedCell] else null)
      },
    ) {
      itemsIndexed(entries) { _, entry -> entry.AndroidProjectListEntry(isSelected, isActive) }
    }
  }
}

@Composable
internal fun GeminiListCell(isSelected: Boolean, isFocused: Boolean) {
  Column {
    Divider(
      modifier = Modifier.width(260.dp).padding(horizontal = 20.dp, vertical = 8.dp),
      orientation = Orientation.Horizontal,
      thickness = 3.dp,
    )
    ListCell(
      text = "Create with Gemini...",
      iconKey = StudioIconsCompose.StudioBot.Logo,
      isSelected = isSelected,
      isFocused = isFocused,
    )
  }
}

@Composable
internal fun ListCell(
  text: String,
  iconKey: IconKey? = null,
  isSelected: Boolean,
  isFocused: Boolean,
) {
  Row(
    modifier =
      Modifier.size(width = 260.dp, height = 32.dp)
        .background(UIUtil.getListBackground(isSelected, isFocused).toComposeColor()),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (iconKey != null) {
      Icon(
        key = iconKey,
        contentDescription = "",
        modifier = Modifier.padding(start = 20.dp, end = 8.dp),
      )
    }
    Text(
      modifier = Modifier.padding(start = if (iconKey == null) 20.dp else 0.dp),
      text = text,
      color = UIUtil.getListForeground(isSelected, isFocused).toComposeColor(),
    )
  }
}

@Composable
private fun RightSidePanel(selectedEntry: ChooseAndroidProjectEntry?) {
  selectedEntry?.AndroidProjectEntryDetails()
}

@Composable
internal fun GeminiRightPanel(
  textFieldState: TextFieldState,
  geminiPluginAvailable: Boolean,
  hasContextSharing: Boolean,
) {
  Column(
    modifier = Modifier.fillMaxSize(),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    if (!geminiPluginAvailable) {
      PermissionsError("Log in to Gemini to create a new project.")
    } else if (!hasContextSharing) {
      PermissionsError("Enable project context sharing to continue.")
    } else {
      NewProjectWizardWithGemini(textFieldState)
    }
  }
}

@Composable
private fun PermissionsError(text: String) {
  Text(
    modifier =
      Modifier.padding(bottom = 24.dp)
        .testTag(ChooseAndroidProjectStepLayoutTags.RightPanel.geminiErrorText),
    text = text,
    style =
      JewelTheme.defaultTextStyle.merge(
        fontSize = 13.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight(500),
      ),
  )
}

@Composable
private fun NewProjectWizardWithGemini(textFieldState: TextFieldState) {
  // Need to do animation here.
  val brush = CssGradientBrush(angleDegrees = -16.0, colors = colors, stops = stops, scaleX = 4f)

  Text(
    modifier = Modifier.padding(bottom = 8.dp),
    text = "What do you want to build?",
    style =
      JewelTheme.defaultTextStyle
        .merge(fontSize = 24.sp, fontWeight = FontWeight.Medium, letterSpacing = (-1).sp)
        .copy(brush = brush),
  )
  Text(
    modifier = Modifier.padding(bottom = 24.dp),
    text = "Bring your app to life faster",
    style =
      JewelTheme.defaultTextStyle.merge(
        fontSize = 13.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight(500),
        color = JewelTheme.globalColors.text.info,
      ),
  )
  TextArea(
    modifier =
      Modifier.size(450.dp, 150.dp)
        .testTag(ChooseAndroidProjectStepLayoutTags.RightPanel.geminiTextArea),
    state = textFieldState,
    placeholder = { Text(text = "Ask Gemini to create a to-do list app") },
  )
}

@Composable
internal fun TemplateGrid(
  templates: List<Template>,
  selectedTemplate: Template?,
  onTemplateClick: (Template?) -> Unit,
) {
  val scrollState = rememberLazyGridState()
  var hasFocus by remember { mutableStateOf(false) }
  val gridFocusRequester = remember { FocusRequester() }

  LaunchedEffect(selectedTemplate) {
    if (selectedTemplate != null) {
      val index = templates.indexOf(selectedTemplate)
      if (index != -1) {
        scrollState.animateScrollToItem(index)
      }
    }
  }

  VerticallyScrollableContainer(scrollState) {
    BoxWithConstraints {
      val columnCount = (maxWidth / templateCellSize).toInt()

      LazyVerticalGrid(
        columns = GridCells.FixedSize(templateCellSize),
        state = scrollState,
        modifier =
          Modifier.testTag(ChooseAndroidProjectStepLayoutTags.RightPanel.templateGrid)
            .fillMaxWidth()
            .onFocusChanged { hasFocus = it.hasFocus }
            .focusRequester(gridFocusRequester)
            .focusable()
            .onKeyEvent { event ->
              if (!hasFocus || selectedTemplate == null) return@onKeyEvent false
              val currentTemplateIndex = templates.indexOf(selectedTemplate)

              return@onKeyEvent when {
                event.type == KeyEventType.KeyUp &&
                  event.key == Key.DirectionUp &&
                  currentTemplateIndex > columnCount - 1 -> {
                  onTemplateClick(templates[currentTemplateIndex - columnCount])
                  true
                }
                event.type == KeyEventType.KeyUp &&
                  event.key == Key.DirectionDown &&
                  currentTemplateIndex < templates.size - columnCount -> {
                  onTemplateClick(templates[currentTemplateIndex + columnCount])
                  true
                }
                event.type == KeyEventType.KeyUp &&
                  event.key == Key.DirectionLeft &&
                  currentTemplateIndex > 0 -> {
                  onTemplateClick(templates[currentTemplateIndex - 1])
                  true
                }
                event.type == KeyEventType.KeyUp &&
                  event.key == Key.DirectionRight &&
                  currentTemplateIndex < templates.size - 1 -> {
                  onTemplateClick(templates[currentTemplateIndex + 1])
                  true
                }
                else -> false
              }
            },
      ) {
        itemsIndexed(items = templates) { _, template ->
          val isSelected = template == selectedTemplate
          val isFocused = hasFocus && isSelected

          Template(
            template = template,
            isSelected = isSelected,
            isFocused = isFocused,
            onTemplateClick = {
              onTemplateClick(template)
              if (!hasFocus) gridFocusRequester.requestFocus()
            },
          )
        }
      }
    }
  }
}

@Composable
private fun Template(
  template: Template,
  isSelected: Boolean,
  isFocused: Boolean,
  onTemplateClick: () -> Unit,
) {
  Column(
    modifier =
      Modifier.fillMaxSize()
        .focusProperties { canFocus = false }
        .border(1.dp, UIUtil.getListBackground(isSelected, isFocused).toComposeColor())
        .clickable(interactionSource = null, indication = null, onClick = onTemplateClick),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    TemplateImage(template)
    TemplateText(template, isSelected, isFocused)
  }
}

@OptIn(ExperimentalResourceApi::class)
@Composable
private fun TemplateImage(template: Template) {
  if (template == Template.NoActivity) {
    Icon(key = StudioIllustrationsCompose.Wizards.NoActivity, contentDescription = "")
  } else {

    val imageState by
      produceState<ImageBitmap?>(initialValue = null, template) {
        withContext(Dispatchers.IO) {
          try {
            val file = File(template.thumb().path().file)
            val bytes = file.inputStream().use { it.readAllBytes() }
            value = bytes.decodeToImageBitmap()
          } catch (_: Exception) {}
        }
      }

    if (imageState == null) {
      Icon(key = StudioIllustrationsCompose.Wizards.NoActivity, contentDescription = "")
    } else {
      Image(bitmap = imageState!!, contentDescription = "")
    }
  }
}

@Composable
private fun TemplateText(template: Template, isSelected: Boolean, isFocused: Boolean) {
  Box(
    modifier =
      Modifier.fillMaxWidth()
        .background(UIUtil.getListBackground(isSelected, isFocused).toComposeColor()),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      modifier = Modifier.padding(vertical = 4.dp),
      text = template.getTemplateTitle(),
      color = UIUtil.getListForeground(isSelected, isFocused).toComposeColor(),
      textAlign = TextAlign.Center,
    )
  }
}
