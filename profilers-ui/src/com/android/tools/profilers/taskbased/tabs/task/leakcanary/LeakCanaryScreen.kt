/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.profilers.taskbased.tabs.task.leakcanary

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.android.tools.leakcanarylib.data.Leak
import com.android.tools.profilers.leakcanary.LeakCanaryModel
import com.android.tools.profilers.taskbased.common.constants.dimensions.TaskBasedUxDimensions
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings
import com.android.tools.profilers.taskbased.common.dividers.ToolWindowHorizontalDivider
import com.android.tools.profilers.taskbased.tabs.task.leakcanary.actionbars.LeakCanaryActionBar
import com.android.tools.profilers.taskbased.tabs.task.leakcanary.leakdetails.LeakDetailsPanel
import com.android.tools.profilers.taskbased.tabs.task.leakcanary.leaklist.LeakListView
import icons.StudioIconsCompose
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.HorizontalSplitLayout
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.component.rememberSplitLayoutState
import org.jetbrains.jewel.ui.icons.AllIconsKeys

@Composable
fun LeakCanaryScreen(leakCanaryModel: LeakCanaryModel) {
  val selectedLeak by leakCanaryModel.selectedLeak.collectAsState()
  val traceNodes = selectedLeak?.displayedLeakTrace?.firstOrNull()?.nodes ?: emptyList()
  var openStates by remember(selectedLeak) { mutableStateOf(List(traceNodes.size) { false }) }

  val onExpandAll = { openStates = List(traceNodes.size) { true } }
  val onCollapseAll = { openStates = List(traceNodes.size) { false } }

  val focusRequester = remember { FocusRequester() }

  Column(modifier = Modifier.fillMaxSize()
    .focusRequester(focusRequester)
    .focusable()
    .onKeyEvent { keyEvent ->
      if (keyEvent.type == KeyEventType.KeyDown && keyEvent.isCtrlPressed) {
        when (keyEvent.key) {
          Key.Plus, Key.NumPadAdd, Key.Equals -> {
            onExpandAll()
            true
          }
          Key.NumPadSubtract, Key.Minus -> {
            onCollapseAll()
            true
          }
          else -> false
        }
      }
      else {
        false
      }
    }
  ) {
    LeakCanaryActionBar(leakCanaryModel)
    ToolWindowHorizontalDivider()

    // Use a Row to place the main content and the sidebar next to each other.
    Row(modifier = Modifier.fillMaxSize()) {
      // The main content area lives inside a weight modifier, so it will take up
      // all available space, pushing the fixed-width sidebar to the right.
      HorizontalSplitLayout(
        state = rememberSplitLayoutState(0.3f),
        firstPaneMinWidth = 150.dp,
        secondPaneMinWidth = 250.dp,
        first = { LeakListView(leakCanaryModel) },
        second = {
          val selectedLeak by leakCanaryModel.selectedLeak.collectAsState()
          val isRecording by leakCanaryModel.isRecording.collectAsState()
          LeakDetailsPanel(
            selectedLeak = selectedLeak,
            gotoDeclaration = leakCanaryModel::goToDeclaration,
            isRecording = isRecording,
            isDeclarationAvailableAsync = leakCanaryModel::isDeclarationAvailableAsync,
            openStates = openStates,
            onOpenStatesChange = { newStates -> openStates = newStates }
          )
        },
        modifier = Modifier.weight(1f),
      )
      if (selectedLeak != null) {
        ToolWindowVerticalDivider()
        RightSidebar(
          selectedLeak = selectedLeak!!,
          modifier = Modifier.width(48.dp),
          onExpandAll = onExpandAll,
          onCollapseAll = onCollapseAll
        )
      }
    }
    LaunchedEffect(Unit) {
      focusRequester.requestFocus()
    }
  }
}

@Composable
private fun ToolWindowVerticalDivider() {
  Divider(
    modifier = Modifier.fillMaxHeight(),
    orientation = Orientation.Vertical,
    color = JewelTheme.globalColors.borders.normal
  )
}

/**
 * A composable for the content of the right sidebar.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RightSidebar(
  selectedLeak: Leak,
  modifier: Modifier = Modifier,
  onExpandAll: () -> Unit,
  onCollapseAll: () -> Unit
) {
  Column(
    modifier = modifier.padding(vertical = 8.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Tooltip(
      tooltip = {
        Column(horizontalAlignment = Alignment.Start) {
          Text(TaskBasedUxStrings.LEAKCANARY_EXPAND_ALL)
          Text(TaskBasedUxStrings.LEAKCANARY_EXPAND_ALL_SHORTCUT, color = JewelTheme.globalColors.text.info)
        }
      }
    ) {
      IconButton(onClick = onExpandAll) {
        Icon(
          key = StudioIconsCompose.Profiler.Toolbar.ExpandSession,
          contentDescription = TaskBasedUxStrings.LEAKCANARY_EXPAND_ALL,
          modifier = Modifier.padding(TaskBasedUxDimensions.TASK_ACTION_BAR_CONTENT_PADDING_DP),
        )
      }
    }
    Tooltip(
      tooltip = {
        Column(horizontalAlignment = Alignment.Start) {
          Text(TaskBasedUxStrings.LEAKCANARY_COLLAPSE_ALL)
          Text(TaskBasedUxStrings.LEAKCANARY_COLLAPSE_ALL_SHORTCUT, color = JewelTheme.globalColors.text.info)
        }
      }
    ) {
      IconButton(onClick = onCollapseAll) {
        Icon(
          key = StudioIconsCompose.Profiler.Toolbar.CollapseSession,
          contentDescription = TaskBasedUxStrings.LEAKCANARY_COLLAPSE_ALL,
          modifier = Modifier.padding(TaskBasedUxDimensions.TASK_ACTION_BAR_CONTENT_PADDING_DP),
        )
      }
    }
    Tooltip(
      tooltip = { Text(TaskBasedUxStrings.LEAKCANARY_COPY_TO_CLIPBOARD) }
    ) {
      IconButton(onClick = { copyLeakToClipboard(selectedLeak.toString()) }) {
        Icon(
          key = AllIconsKeys.Actions.Copy,
          contentDescription = TaskBasedUxStrings.LEAKCANARY_COPY_TO_CLIPBOARD,
          modifier = Modifier.padding(TaskBasedUxDimensions.TASK_ACTION_BAR_CONTENT_PADDING_DP),
        )
      }
    }
  }
}

/**
 * Copies a simplified text representation of the selected leak to the system clipboard.
 */
internal var copyLeakToClipboard: (String) -> Unit = { content ->
  val clipboard = Toolkit.getDefaultToolkit().systemClipboard
  clipboard.setContents(StringSelection(content), null)
}