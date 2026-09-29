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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.tools.idea.npw.ui.getTemplateTitle
import com.android.tools.idea.wizard.template.Template
import com.android.tools.idea.wizard.template.Thumb
import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import icons.StudioIllustrationsCompose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.foundation.lazy.SelectableLazyColumn
import org.jetbrains.jewel.foundation.lazy.SelectableLazyListState
import org.jetbrains.jewel.foundation.lazy.SelectionMode
import org.jetbrains.jewel.foundation.lazy.itemsIndexed
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.icon.IconKey

private val templateCellSize = 192.dp

@Composable
fun ChooseAndroidProjectStepUI(model: ChooseAndroidProjectStepModel) {
  val isLoading by model::isLoading
  val selectedEntry by model::selectedAndroidProjectEntry
  val entries by model::chooseAndroidProjectEntries

  if (isLoading) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
  } else if (entries.isNotEmpty()) {
    Row(modifier = Modifier.fillMaxSize()) {
      LeftSidePanel(entries = entries, selectedEntry = selectedEntry, updateEntrySelected = { entry -> model.updateSelectedCell(entry) })
      Divider(Orientation.Vertical, thickness = 1.dp, modifier = Modifier.fillMaxHeight())
      RightSidePanel(selectedEntry)
    }
  }
}

@Composable
private fun LeftSidePanel(
  entries: List<ChooseAndroidProjectEntry>,
  selectedEntry: ChooseAndroidProjectEntry?,
  updateEntrySelected: (ChooseAndroidProjectEntry?) -> Unit,
) {
  val focusRequester = remember { FocusRequester() }
  val stateList = remember {
    SelectableLazyListState(lazyListState = LazyListState(), selectionMode = SelectionMode.Single).apply {
      // Initialize state based on selectedEntry, i.e., restore previous selection state
      val selectedEntryIndex = entries.indexOf(selectedEntry)
      if (selectedEntryIndex >= 0) {
        selectedKeys = setOf(selectedEntry!!)
        lastActiveItemIndex = selectedEntryIndex
      }
    }
  }

  Column {
    Text(
      modifier = Modifier.background(UIUtil.getListBackground().toComposeColor()).padding(20.dp),
      text = "Templates",
      color = JBColor(0x999999, 0x787878).toComposeColor(),
    )
    SelectableLazyColumn(
      modifier = Modifier.testTag(ChooseAndroidProjectStepLayoutTags.LeftPanel.column).focusRequester(focusRequester),
      selectionMode = SelectionMode.Single,
      onSelectedIndexesChange = { newSelectedList ->
        val newSelectedCell = newSelectedList.firstOrNull()
        updateEntrySelected(if (newSelectedCell != null) entries[newSelectedCell] else null)
      },
      state = stateList,
      interactionSource = null,
    ) {
      itemsIndexed(entries, key = { _, entry -> entry }) { _, entry -> entry.AndroidProjectListEntry(isSelected, isActive) }
    }
  }

  LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
fun ProjectEntryListCell(text: String, iconKey: IconKey? = null, isSelected: Boolean, isFocused: Boolean) {
  Row(
    modifier = Modifier.size(width = 260.dp, height = 32.dp).background(UIUtil.getListBackground(isSelected, isFocused).toComposeColor()),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (iconKey != null) {
      Icon(key = iconKey, contentDescription = "", modifier = Modifier.padding(start = 20.dp, end = 8.dp))
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
internal fun TemplateGrid(
  templates: List<Template>,
  selectedTemplate: Template?,
  onTemplateClick: (Template?) -> Unit,
  onTemplateDoubleClick: (Template) -> Unit = {},
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
      val columnCount = (maxWidth / templateCellSize).toInt().coerceAtLeast(1)

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
              if (currentTemplateIndex < 0) return@onKeyEvent false

              return@onKeyEvent when {
                event.type == KeyEventType.KeyUp && event.key == Key.DirectionUp && currentTemplateIndex > columnCount - 1 -> {
                  onTemplateClick(templates[currentTemplateIndex - columnCount])
                  true
                }
                event.type == KeyEventType.KeyUp &&
                  event.key == Key.DirectionDown &&
                  currentTemplateIndex < templates.size - columnCount -> {
                  onTemplateClick(templates[currentTemplateIndex + columnCount])
                  true
                }
                event.type == KeyEventType.KeyUp && event.key == Key.DirectionLeft && currentTemplateIndex > 0 -> {
                  onTemplateClick(templates[currentTemplateIndex - 1])
                  true
                }
                event.type == KeyEventType.KeyUp && event.key == Key.DirectionRight && currentTemplateIndex < templates.size - 1 -> {
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
            onTemplateDoubleClick = { onTemplateDoubleClick(template) },
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Template(
  template: Template,
  isSelected: Boolean,
  isFocused: Boolean,
  onTemplateClick: () -> Unit,
  onTemplateDoubleClick: () -> Unit,
) {
  Column(
    modifier =
      Modifier.fillMaxSize()
        .focusProperties { canFocus = false }
        .border(1.dp, UIUtil.getListBackground(isSelected, isFocused).toComposeColor())
        .semantics {
          onClick {
            onTemplateClick()
            true
          }
        }
        .pointerInput(template) { detectTapGestures(onPress = { onTemplateClick() }, onDoubleTap = { onTemplateDoubleClick() }) },
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
  if (template.thumb() == Thumb.NoThumb) {
    Icon(key = StudioIllustrationsCompose.Wizards.NoActivity, contentDescription = "")
  } else {
    val imageBitmap by
      produceState<ImageBitmap?>(initialValue = null, template) {
        value =
          withContext(Dispatchers.Default) {
            val iconUrl = template.thumb().path()
            try {
              val bytes = withContext(Dispatchers.IO) { iconUrl.openStream().use { it.readAllBytes() } }
              bytes.decodeToImageBitmap()
            } catch (e: Exception) {
              rethrowControlFlowException(e)
              fileLogger().error("Failed to load icon: $iconUrl", e)
              null
            }
          }
      }

    if (imageBitmap == null) {
      Icon(key = StudioIllustrationsCompose.Wizards.NoActivity, contentDescription = "")
    } else {
      Image(bitmap = imageBitmap!!, contentDescription = "")
    }
  }
}

@Composable
private fun TemplateText(template: Template, isSelected: Boolean, isFocused: Boolean) {
  Box(
    modifier = Modifier.fillMaxWidth().background(UIUtil.getListBackground(isSelected, isFocused).toComposeColor()),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      modifier = Modifier.padding(vertical = 4.dp),
      text = getTemplateTitle(template),
      color = UIUtil.getListForeground(isSelected, isFocused).toComposeColor(),
      textAlign = TextAlign.Center,
    )
  }
}
