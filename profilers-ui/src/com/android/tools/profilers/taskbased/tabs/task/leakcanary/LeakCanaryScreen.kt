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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import com.android.tools.profilers.IdeProfilerComponents
import com.android.tools.profilers.cpu.CpuProfilerStage
import com.android.tools.profilers.cpu.config.CpuProfilerConfigModel
import com.android.tools.profilers.leakcanary.LeakCanaryModel
import com.android.tools.profilers.taskbased.common.dividers.ToolWindowHorizontalDivider
import com.android.tools.profilers.taskbased.common.dividers.ToolWindowVerticalDivider
import com.android.tools.profilers.taskbased.tabs.task.leakcanary.actionbars.LeakCanaryActionBar
import com.android.tools.profilers.taskbased.tabs.task.leakcanary.banner.LeakCanaryBanner
import com.android.tools.profilers.taskbased.tabs.task.leakcanary.insight.LeakInsightPanel
import com.android.tools.profilers.taskbased.tabs.task.leakcanary.leakdetails.LeakDetailsPanel
import com.android.tools.profilers.taskbased.tabs.task.leakcanary.leaklist.LeakListView
import com.android.tools.profilers.tasks.analytics.LeakCanaryUiAction
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.HorizontalSplitLayout
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticalSplitLayout
import org.jetbrains.jewel.ui.component.rememberSplitLayoutState
import org.jetbrains.jewel.ui.icons.AllIconsKeys

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
          val leakConfig =
            leakCanaryModel.studioProfilers.ideServices.getTaskCpuProfilerConfigs(0).find {
              it.traceType == com.android.tools.profilers.cpu.config.ProfilingConfiguration.TraceType.LEAKCANARY
            }
          if (leakConfig != null) {
            configModel.profilingConfiguration = leakConfig
          }
          ideProfilerComponents.openCpuProfilingConfigurationsDialog(configModel, 0, {}, leakCanaryModel.studioProfilers.ideServices)
          leakCanaryModel.updateModeFromSettings()
        },
      )
    }
    LeakCanaryActionBar(leakCanaryModel)
    ToolWindowHorizontalDivider()

    val leaks by leakCanaryModel.leaks.collectAsState()
    val filteredLeaks by leakCanaryModel.filteredLeaks.collectAsState()
    val hasLeaks = leaks.isNotEmpty()
    val isStudioBotEnabled = leakCanaryModel.isLeakCanaryStudioBotEnabled && hasLeaks
    val insightModel = leakCanaryModel.insightModel
    val isInsightVisible by insightModel.isInsightVisible.collectAsState()
    val insightState by insightModel.currentInsight.collectAsState()
    val isInsightAutoGenerateEnabled by insightModel.isInsightAutoGenerateEnabled.collectAsState()

    // Use a Row to place the main resizable workspace area and the vertical tab sidebar next to each other.
    Row(modifier = Modifier.fillMaxSize()) {
      BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxHeight()) {
        val horizontalInnerSplitState = rememberSplitLayoutState(0.4f)
        val horizontalOuterSplitState = rememberSplitLayoutState(0.75f)
        val verticalListSplitState = rememberSplitLayoutState(0.3f)
        val bottomDetailsInsightSplitState = rememberSplitLayoutState(0.65f)

        val isRecording by leakCanaryModel.isRecording.collectAsState()

        val leakListContent = @Composable { LeakListView(leakCanaryModel) }

        val leakDetailsContent =
          @Composable {
            LeakDetailsPanel(
              selectedLeak = selectedLeak,
              gotoDeclaration = leakCanaryModel::goToDeclaration,
              isRecording = isRecording,
              hasActiveFilter = hasLeaks && filteredLeaks.size != leaks.size,
              isDeclarationAvailableAsync = leakCanaryModel::isDeclarationAvailableAsync,
              openStates = openStates,
              onOpenStatesChange = { newStates -> openStates = newStates },
              onCopy = { leakCanaryModel.trackUiAction(LeakCanaryUiAction.COPY_TRACE_CLICKED) },
              trackUiAction = leakCanaryModel::trackUiAction,
            )
          }

        val leakInsightContent =
          @Composable {
            LeakInsightPanel(
              insightState = insightState,
              isLeakSelected = selectedLeak != null,
              autoGenerateEnabled = isInsightAutoGenerateEnabled,
              onAutoGenerateChange = insightModel::setInsightAutoGenerateEnabled,
              onClose = { insightModel.setInsightVisible(false) },
              onFeedback = { feedback -> insightModel.submitInsightFeedback(feedback) },
              onGenerateFix = { _ ->
                selectedLeak?.let {
                  leakCanaryModel.trackUiAction(LeakCanaryUiAction.INSIGHT_GENERATE_FIX_CLICKED)
                  leakCanaryModel.analyzeLeakWithStudioBot(it)
                }
              },
              onCopy = { leakCanaryModel.trackUiAction(LeakCanaryUiAction.INSIGHT_COPY_CLICKED) },
              onRefresh = { selectedLeak?.let { insightModel.fetchInsight(it) } },
              modifier = Modifier.fillMaxSize(),
            )
          }

        val minHorizontalWidth = if (isStudioBotEnabled && isInsightVisible) 850.dp else 600.dp
        val isHorizontal = (maxWidth >= minHorizontalWidth) && (maxWidth * 2 >= maxHeight * 3)

        if (isHorizontal) {
          val mainWorkspace =
            @Composable {
              HorizontalSplitLayout(
                state = horizontalInnerSplitState,
                firstPaneMinWidth = 310.dp,
                secondPaneMinWidth = 360.dp,
                first = leakListContent,
                second = leakDetailsContent,
                modifier = Modifier.fillMaxSize(),
              )
            }

          if (isStudioBotEnabled && isInsightVisible) {
            HorizontalSplitLayout(
              state = horizontalOuterSplitState,
              firstPaneMinWidth = 600.dp,
              secondPaneMinWidth = 250.dp,
              first = mainWorkspace,
              second = leakInsightContent,
              modifier = Modifier.fillMaxSize(),
            )
          } else {
            mainWorkspace()
          }
        } else {
          val bottomContent =
            @Composable {
              if (isStudioBotEnabled && isInsightVisible) {
                HorizontalSplitLayout(
                  state = bottomDetailsInsightSplitState,
                  firstPaneMinWidth = 350.dp,
                  secondPaneMinWidth = 250.dp,
                  first = leakDetailsContent,
                  second = leakInsightContent,
                  modifier = Modifier.fillMaxSize(),
                )
              } else {
                leakDetailsContent()
              }
            }

          VerticalSplitLayout(
            state = verticalListSplitState,
            firstPaneMinWidth = 140.dp,
            secondPaneMinWidth = 180.dp,
            first = leakListContent,
            second = bottomContent,
            modifier = Modifier.fillMaxSize(),
          )
        }
      }

      if (isStudioBotEnabled) {
        ToolWindowVerticalDivider()

        Column(modifier = Modifier.width(26.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
          val activeBgColor =
            if (isInsightVisible) {
              JewelTheme.globalColors.borders.normal.copy(alpha = 0.4f)
            } else {
              Color.Transparent
            }
          val activeTextColor =
            if (isInsightVisible) {
              JewelTheme.globalColors.text.info
            } else {
              JewelTheme.globalColors.text.normal
            }

          Box(
            modifier =
              Modifier.fillMaxWidth()
                .background(activeBgColor)
                .clickable { insightModel.setInsightVisible(!isInsightVisible) }
                .padding(top = 8.dp),
            contentAlignment = Alignment.Center,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.rotateVertically()) {
              Icon(
                key = AllIconsKeys.Actions.IntentionBulbGrey,
                contentDescription = "AI Insights",
                tint = activeTextColor,
                modifier = Modifier.graphicsLayer(rotationZ = -90f),
              )
              Spacer(Modifier.width(6.dp))
              Text("Insights", color = activeTextColor, maxLines = 1, softWrap = false)
              Spacer(Modifier.width(12.dp))
            }
          }
        }
      }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
  }
}

private fun Modifier.rotateVertically() =
  this.layout { measurable, constraints ->
    val placeable =
      measurable.measure(
        constraints.copy(
          minWidth = constraints.minHeight,
          maxWidth = constraints.maxHeight,
          minHeight = constraints.minWidth,
          maxHeight = constraints.maxWidth,
        )
      )
    layout(placeable.height, placeable.width) {
      placeable.placeWithLayer(x = 0, y = 0) {
        transformOrigin = TransformOrigin(0f, 0f)
        rotationZ = 90f
        translationX = placeable.height.toFloat()
      }
    }
  }
