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
package com.android.tools.profilers.taskbased.tabs.task.leakcanary

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.android.tools.profilers.IdeProfilerComponents
import com.android.tools.profilers.cpu.CpuProfilerStage
import com.android.tools.profilers.cpu.config.CpuProfilerConfigModel
import com.android.tools.profilers.leakcanary.LeakCanaryModel
import com.android.tools.profilers.taskbased.common.dividers.ToolWindowHorizontalDivider
import com.android.tools.profilers.taskbased.tabs.task.leakcanary.actionbars.LeakCanaryActionBar
import com.android.tools.profilers.taskbased.tabs.task.leakcanary.banner.LeakCanaryBanner
import com.android.tools.profilers.taskbased.tabs.task.leakcanary.leakdetails.LeakDetailsPanel
import com.android.tools.profilers.taskbased.tabs.task.leakcanary.leaklist.LeakListView
import com.android.tools.profilers.tasks.analytics.LeakCanaryUiAction
import org.jetbrains.jewel.ui.component.HorizontalSplitLayout
import org.jetbrains.jewel.ui.component.rememberSplitLayoutState

@Composable
fun LeakCanaryScreen(leakCanaryModel: LeakCanaryModel, ideProfilerComponents: IdeProfilerComponents) {
  val selectedLeak by leakCanaryModel.selectedLeak.collectAsState()
  val isBannerVisible by leakCanaryModel.isBannerVisible.collectAsState()
  val traceNodes = selectedLeak?.displayedLeakTrace?.firstOrNull()?.nodes ?: emptyList()
  var openStates by remember(selectedLeak) { mutableStateOf(List(traceNodes.size) { false }) }

  val focusRequester = remember { FocusRequester() }

  Column(
    modifier =
      Modifier.fillMaxSize().focusRequester(focusRequester).focusable().onKeyEvent { keyEvent ->
        if (keyEvent.type == KeyEventType.KeyDown && keyEvent.isCtrlPressed) {
          when (keyEvent.key) {
            Key.Plus,
            Key.NumPadAdd,
            Key.Equals -> {
              openStates = List(traceNodes.size) { true }
              leakCanaryModel.trackUiAction(LeakCanaryUiAction.EXPAND_ALL_NODES_CLICKED)
              true
            }
            Key.NumPadSubtract,
            Key.Minus -> {
              openStates = List(traceNodes.size) { false }
              leakCanaryModel.trackUiAction(LeakCanaryUiAction.COLLAPSE_ALL_NODES_CLICKED)
              true
            }
            else -> false
          }
        } else {
          false
        }
      }
  ) {
    if (isBannerVisible) {
      LeakCanaryBanner(
        onBannerClose = leakCanaryModel::dismissBanner,
        onBannerDoNotAskAgainClick = leakCanaryModel::setBannerDoNotShowAgain,
        onEditConfigurationClick = {
          val dummyStage = CpuProfilerStage(leakCanaryModel.studioProfilers)
          val configModel = CpuProfilerConfigModel(leakCanaryModel.studioProfilers, dummyStage)
          ideProfilerComponents.openTaskConfigurationsDialog(configModel, leakCanaryModel.studioProfilers.ideServices)
          leakCanaryModel.updateModeFromSettings()
        },
      )
    }
    LeakCanaryActionBar(leakCanaryModel)
    ToolWindowHorizontalDivider()

    // Use a Row to place the main content and the sidebar next to each other.
    Row(modifier = Modifier.fillMaxSize()) {
      // The main content area lives inside a weight modifier, so it will take up
      // all available space, pushing the fixed-width sidebar to the right.
      HorizontalSplitLayout(
        state = rememberSplitLayoutState(0.3f),
        firstPaneMinWidth = 150.dp,
        secondPaneMinWidth = 600.dp,
        first = { LeakListView(leakCanaryModel) },
        second = {
          val selectedLeak by leakCanaryModel.selectedLeak.collectAsState()
          val isRecording by leakCanaryModel.isRecording.collectAsState()
          val isLeakCanaryPresent by leakCanaryModel.isLeakCanaryPresent.collectAsState()
          LeakDetailsPanel(
            selectedLeak = selectedLeak,
            gotoDeclaration = leakCanaryModel::goToDeclaration,
            isRecording = isRecording,
            isLeakCanaryPresent = isLeakCanaryPresent,
            isDeclarationAvailableAsync = leakCanaryModel::isDeclarationAvailableAsync,
            openStates = openStates,
            onOpenStatesChange = { newStates -> openStates = newStates },
            onCopy = { leakCanaryModel.trackUiAction(LeakCanaryUiAction.COPY_TRACE_CLICKED) },
            trackUiAction = leakCanaryModel::trackUiAction,
            onAnalyzeLeakWithStudioBot = { leak -> leak?.let { leakCanaryModel.analyzeLeakWithStudioBot(it) } },
            isLeakCanaryStudioBotEnabled = leakCanaryModel.isLeakCanaryStudioBotEnabled,
          )
        },
        modifier = Modifier.weight(1f),
      )
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
  }
}
