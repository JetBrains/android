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

package com.android.tools.idea.compose.preview.interactive

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.android.tools.adtui.compose.IntUiPaletteDefaults
import com.android.tools.idea.compose.preview.BackNavigationEdge
import com.android.tools.idea.compose.preview.InteractivePreviewNavigationController
import com.android.tools.idea.compose.preview.message
import icons.StudioIconsCompose
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Slider
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip

private val DEFAULT_SPACING = 8.dp

/** @see also [NavigationControlsPanel] */
@Composable
fun NavigationControlsContent(
  interactivePreviewNavigationController: InteractivePreviewNavigationController,
  fpsUpdater: SharedFlow<Unit>,
  isEdgeNavigationImplemented: suspend () -> Boolean,
) {
  Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    NavigationControlsPanel(
      isEdgeNavigationImplemented = isEdgeNavigationImplemented,
      canBackPress = { interactivePreviewNavigationController.canBackPress() },
      onBackPress = {
        interactivePreviewNavigationController.backPressCompleted()
        interactivePreviewNavigationController.trackNavigationBackPress()
      },
      onBackPressStart = { interactivePreviewNavigationController.backPressStart(it) },
      onBackPressProgress = { progress, edge -> interactivePreviewNavigationController.backPressProgress(progress, edge) },
      onBackPressTrackProgress = { interactivePreviewNavigationController.trackNavigationProgressPress() },
      onEdgeDropdownPress = {
        interactivePreviewNavigationController.trackEdgeDropdownPress()
        interactivePreviewNavigationController.backPressCancelled()
      },
      fpsUpdater = fpsUpdater,
      backPressCompletedFlow = interactivePreviewNavigationController.backPressCompletedFlow,
    )
  }
}

/**
 * A panel providing controls for back navigation in Interactive mode.
 *
 * It includes:
 * - A "Back" button to trigger back navigation.
 * - A dropdown to select the [BackNavigationEdge].
 * - A slider to simulate predictive back progress.
 *
 * @param modifier The modifier to be applied to this Composable.
 * @param isEdgeNavigationImplemented A callback returning whether the back navigation edge represents an implemented capability.
 * @param canBackPress A callback that returns whether back navigation is currently available.
 * @param onBackPress A callback invoked when the back button is clicked.
 * @param onBackPressStart A callback invoked when a predictive back gesture starts.
 * @param onBackPressProgress A callback invoked with the current progress of a predictive back gesture.
 * @param onBackPressTrackProgress A callback invoked when the predictive back gesture tracking finishes.
 * @param onEdgeDropdownPress A callback invoked when the navigation edge dropdown is interacted with.
 * @param fpsUpdater A [SharedFlow] used to refresh the state of the panel (e.g., re-evaluating [canBackPress]).
 * @param backPressCompletedFlow A [SharedFlow] to listen for back navigation completion events.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NavigationControlsPanel(
  modifier: Modifier = Modifier,
  isEdgeNavigationImplemented: suspend () -> Boolean,
  canBackPress: () -> Boolean,
  onBackPress: () -> Unit,
  onBackPressStart: (BackNavigationEdge) -> Unit,
  onBackPressProgress: (Float, BackNavigationEdge) -> Unit,
  onBackPressTrackProgress: () -> Unit,
  onEdgeDropdownPress: () -> Unit,
  fpsUpdater: SharedFlow<Unit>,
  backPressCompletedFlow: SharedFlow<Unit> = remember { MutableSharedFlow() },
) {
  var sliderPosition by remember { mutableFloatStateOf(0f) }
  var backStarted by remember { mutableStateOf(false) }
  val showEdgeNavigation by produceState(false, isEdgeNavigationImplemented) { value = isEdgeNavigationImplemented() }
  val selectedEdge = remember { mutableStateOf(BackNavigationEdge.EDGE_NONE) }
  val backNavigationAvailable by produceState(canBackPress(), fpsUpdater) { fpsUpdater.collect { value = canBackPress() } }

  LaunchedEffect(backPressCompletedFlow) {
    backPressCompletedFlow.collect {
      sliderPosition = 0f
      backStarted = false
    }
  }

  Column(modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().testTag(NavigationControlsPanelTestTags.panel)) {
    // Add a horizontal divider at the top of the panel to clearly indicate the splitter boundary
    Divider(
      orientation = org.jetbrains.jewel.ui.Orientation.Horizontal,
      modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).testTag(NavigationControlsPanelTestTags.divider),
    )

    FlowRow(
      modifier = Modifier.padding(vertical = DEFAULT_SPACING).fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalArrangement = Arrangement.spacedBy(DEFAULT_SPACING, Alignment.CenterVertically),
    ) {
      Tooltip(tooltip = { Text(message("action.navigate.back.button.disabled.tooltip")) }, enabled = !backNavigationAvailable) {
        OutlinedButton(
          modifier = Modifier.testTag(NavigationControlsPanelTestTags.backButton).widthIn(min = 135.dp),
          enabled = backNavigationAvailable,
          onClick = onBackPress,
        ) {
          Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
              key = StudioIconsCompose.Emulator.Toolbar.Back,
              // The contentDescription is not needed as this icon is decorative to a text label which describes already what the button
              // does.
              contentDescription = null,
              tint = Color(IntUiPaletteDefaults.Dark.Green7),
            )
            Text(text = message("action.navigate.back.button.text"), maxLines = 1, softWrap = false)
          }
        }
      }
      DropDownAction(
        label = message("action.navigate.back.navigation.edge.label"),
        selectedEdge = selectedEdge,
        enabled = showEdgeNavigation,
        onEdgeDropdownPress = onEdgeDropdownPress,
      )
    }
    Row(
      modifier =
        Modifier.padding(vertical = DEFAULT_SPACING)
          .widthIn(min = 16.dp, max = 800.dp)
          .align(Alignment.CenterHorizontally)
          .border(width = 1.dp, color = JewelTheme.globalColors.borders.normal, shape = RoundedCornerShape(4.dp)),
      horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column {
        Text(modifier = Modifier.padding(DEFAULT_SPACING), text = message("action.navigate.back.predictive.back.progress", sliderPosition))
        Slider(
          modifier = Modifier.padding(DEFAULT_SPACING).testTag(NavigationControlsPanelTestTags.progressSlider),
          value = sliderPosition,
          valueRange = 0f..1f,
          enabled = backNavigationAvailable,
          onValueChange = {
            if (!backStarted) {
              backStarted = true
              onBackPressStart(selectedEdge.value)
            }
            sliderPosition = it
          },
          onValueChangeFinished = { onBackPressTrackProgress() },
        )
        SideEffect {
          if (backStarted) {
            onBackPressProgress(sliderPosition, selectedEdge.value)
          }
        }
      }
    }
  }
}

/**
 * A dropdown component which allows the selection of [BackNavigationEdge]
 *
 * @param label The text to show in the Label located on the right of the Dropdown.
 * @param selectedEdge The edge to be selected among the [BackNavigationEdge] enum.
 */
@OptIn(ExperimentalJewelApi::class, ExperimentalFoundationApi::class)
@Composable
private fun DropDownAction(
  label: String,
  selectedEdge: MutableState<BackNavigationEdge>,
  enabled: Boolean,
  onEdgeDropdownPress: () -> Unit,
) =
  FlowRow(
    modifier = Modifier.widthIn(min = 220.dp),
    verticalArrangement = Arrangement.spacedBy(DEFAULT_SPACING, Alignment.CenterVertically),
    horizontalArrangement = Arrangement.spacedBy(DEFAULT_SPACING),
  ) {
    val labelColor = if (enabled) JewelTheme.globalColors.text.normal else JewelTheme.globalColors.text.disabled
    Text(text = label, modifier = Modifier.padding(vertical = DEFAULT_SPACING), color = labelColor, maxLines = 1, softWrap = false)
    Tooltip(tooltip = { Text(message("action.navigate.back.navigation.edge.disabled.tooltip")) }, enabled = !enabled) {
      Dropdown(
        modifier = Modifier.testTag(NavigationControlsPanelTestTags.edgeDropdown).widthIn(min = 100.dp),
        enabled = enabled,
        menuContent = {
          for (edge in BackNavigationEdge.entries) {
            selectableItem(
              selected = selectedEdge.value == edge,
              onClick = {
                selectedEdge.value = edge
                onEdgeDropdownPress()
              },
            ) {
              Text(text = edge.visibleName, maxLines = 1, softWrap = false)
            }
          }
        },
      ) {
        Text(selectedEdge.value.visibleName, maxLines = 1, softWrap = false)
      }
    }
  }

/** Layout tags used for UI testing the [NavigationControlsPanel]. */
object NavigationControlsPanelTestTags {
  private const val base = "NavigationControlsPanel"

  /** Tag for the main panel container. */
  const val panel = base

  /** Tag for the "Navigate back" button. */
  const val backButton = "$base.backButton"

  /** Tag for the predictive back progress slider. */
  const val progressSlider = "$base.progressSlider"

  /** Tag for the navigation edge selection dropdown. */
  const val edgeDropdown = "$base.edgeDropdown"

  /** Tag for the horizontal divider. */
  const val divider = "$base.divider"
}
