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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.tools.idea.compose.preview.BackNavigationEdge
import com.android.tools.idea.compose.preview.InteractivePreviewNavigationController
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.flags.StudioFlags
import icons.StudioIconsCompose
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
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
      getNavigationHistory = { interactivePreviewNavigationController.getNavigationHistory() },
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
      backToState = { navigationState -> interactivePreviewNavigationController.backToState(navigationState) },
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
 * @param getNavigationHistory A callback returning the list of items in the navigation back stack.
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
  getNavigationHistory: () -> List<Any> = { emptyList() },
  isEdgeNavigationImplemented: suspend () -> Boolean,
  canBackPress: () -> Boolean,
  onBackPress: () -> Unit,
  onBackPressStart: (BackNavigationEdge) -> Unit,
  onBackPressProgress: (Float, BackNavigationEdge) -> Unit,
  onBackPressTrackProgress: () -> Unit,
  onEdgeDropdownPress: () -> Unit,
  backToState: (Any) -> Unit = {},
  fpsUpdater: SharedFlow<Unit>,
  backPressCompletedFlow: SharedFlow<Unit> = remember { MutableSharedFlow() },
) {
  var sliderPosition by rememberSaveable { mutableFloatStateOf(0f) }
  var backStarted by remember { mutableStateOf(false) }
  val coroutineScope = rememberCoroutineScope()
  val showEdgeNavigation by produceState(false, isEdgeNavigationImplemented) { value = isEdgeNavigationImplemented() }
  val selectedEdge = rememberSaveable { mutableStateOf(BackNavigationEdge.EDGE_NONE) }
  val backNavigationAvailable by produceState(canBackPress(), fpsUpdater) { fpsUpdater.collect { value = canBackPress() } }
  val navigationHistory by produceState(getNavigationHistory(), fpsUpdater) { fpsUpdater.collect { value = getNavigationHistory() } }

  LaunchedEffect(backPressCompletedFlow) {
    backPressCompletedFlow.collect {
      sliderPosition = 0f
      backStarted = false
    }
  }

  Column(modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().testTag(NavigationControlsPanelTestTags.panel)) {
    FlowRow(
      modifier = Modifier.padding(vertical = DEFAULT_SPACING).fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalArrangement = Arrangement.spacedBy(DEFAULT_SPACING, Alignment.CenterVertically),
    ) {
      Tooltip(tooltip = { Text(message("action.navigate.back.button.disabled.tooltip")) }, enabled = !backNavigationAvailable) {
        OutlinedButton(
          modifier = Modifier.testTag(NavigationControlsPanelTestTags.backButton).widthIn(min = 135.dp),
          enabled = backNavigationAvailable,
          onClick = {
            coroutineScope.launch {
              if (!backStarted) {
                backStarted = true
                val selectedEdge = selectedEdge.value
                // TODO(b/539916536): This is a temporary solution for predictive back navigation.
                // Currently, the limitation is about the predictive back gesture transition continuing to the end,
                // but we expect it to hand off to a separate transition spec after the swipe passes the committed threshold.
                // This should be properly fixed when new APIs for predictive back are released.
                onBackPressStart(selectedEdge)
                // Introduce a minor delay to allow the initial back navigation gesture animation frame
                // to initialize properly before triggering the back press completion event.
                delay(100.milliseconds)
              }
              onBackPress()
            }
          },
        ) {
          Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
              key = StudioIconsCompose.Emulator.Toolbar.Back,
              // The contentDescription is not needed as this icon is decorative to a text label which describes already what the button
              // does.
              contentDescription = null,
              tint =
                if (backNavigationAvailable) {
                  JewelTheme.globalColors.text.normal
                } else {
                  JewelTheme.globalColors.text.disabled
                },
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
      modifier = Modifier.padding(vertical = DEFAULT_SPACING).widthIn(min = 16.dp, max = 800.dp).align(Alignment.CenterHorizontally),
      horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column {
        Divider(
          orientation = Orientation.Horizontal,
          modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).testTag(NavigationControlsPanelTestTags.divider),
        )
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
    if (StudioFlags.COMPOSE_INTERACTIVE_PREVIEW_PREDICTIVE_BACK_STACK_VISUAL.get()) {
      BackStack(navigationHistory, backToState)
    }
  }
}

@Composable
private fun BackStack(navigationHistory: List<Any>, onBackToState: (Any) -> Unit) {
  Column(
    modifier = Modifier.padding(vertical = DEFAULT_SPACING).fillMaxWidth().testTag(NavigationControlsPanelTestTags.visualStack),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(text = message("action.navigate.back.stack.title"), fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
    if (navigationHistory.isEmpty()) {
      Text(
        text = message("action.navigate.back.stack.empty"),
        color = JewelTheme.globalColors.text.info,
        modifier = Modifier.padding(DEFAULT_SPACING),
      )
    } else {
      Column(
        modifier =
          Modifier.fillMaxWidth()
            // Stacks up to 240.dp before we enable the scrollbar.
            .heightIn(max = 240.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        // Display in reverse order so the newest/active item is on top of the stack.
        navigationHistory.asReversed().forEachIndexed { index, navigationInfoItem ->
          val isCurrentActiveNavigationItem = index == 0
          BackStackItem(
            navigationInfoItem = navigationInfoItem,
            isCurrentActiveNavigationItem = isCurrentActiveNavigationItem,
            onItemClick = onBackToState,
          )
        }
      }
    }
  }
}

@Composable
private fun BackStackItem(
  navigationInfoItem: Any,
  isCurrentActiveNavigationItem: Boolean,
  onItemClick: (Any) -> Unit,
) {
  val activeAccent = JewelTheme.globalColors.outlines.focused
  val borderColors = if (isCurrentActiveNavigationItem) activeAccent else JewelTheme.globalColors.borders.normal
  val backGroundColor = if (isCurrentActiveNavigationItem) activeAccent.copy(alpha = 0.12f) else JewelTheme.globalColors.panelBackground
  val textColor = if (isCurrentActiveNavigationItem) activeAccent else JewelTheme.globalColors.text.normal

  val navKeys = parseNavigationItems(navigationInfoItem)
  Row(
    modifier =
      Modifier.border(width = 1.dp, color = borderColors, shape = RoundedCornerShape(8.dp))
        .background(color = backGroundColor, shape = RoundedCornerShape(8.dp))
        .padding(12.dp)
        .fillMaxWidth()
        .widthIn(max = 500.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      // In adaptive layouts on larger screens (such as foldables, tablets, or desktop displaying two-pane or list-detail
      // scenes), a single back stack entry can contain multiple active navigation keys displayed side by side.
      // We render each navigation key on its own line for clarity.
      navKeys.forEach { navKey ->
        Text(text = message("action.navigate.back.stack.navkey", navKey), fontWeight = FontWeight.Bold, color = textColor)
      }
    }
    if (isCurrentActiveNavigationItem) {
      Box(
        modifier =
          Modifier.background(activeAccent.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
      ) {
        Text(
          text = message("action.navigate.back.stack.current"),
          fontWeight = FontWeight.Bold,
          color = activeAccent,
        )
      }
    } else {
      // If the item is not currently shown in the Preview, clicking it navigates back to this state.
      OutlinedButton(
        onClick = { onItemClick(navigationInfoItem) },
        modifier = Modifier.padding(start = 8.dp),
      ) {
        Text(message("action.navigate.back.stack.navigate"))
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
    val labelColor =
      if (enabled) {
        JewelTheme.globalColors.text.normal
      } else {
        JewelTheme.globalColors.text.disabled
      }
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

  /** Tag for the visual stack. */
  const val visualStack = "$base.visualStack"
}

/**
 * Extracts the value of `key=...` from string representations of navigation objects (e.g., `NavigationInfo(key=HomeScreen, ...)` or
 * `NavEntry(key=[Pane1, Pane2], ...)`), capturing until the next named property or closing parenthesis/end-of-string.
 */
private val KEY_REGEX = Regex("""\bkey\s*=\s*(.+?)(?:,\s*\w+\s*=|\)$|$)""")

/**
 * Matches commas that are at the top level (i.e. not enclosed within nested parentheses, square brackets, or curly braces) via negative
 * lookahead, allowing compound navigation keys like `Home, Product(id=1, name=foo)` to split only at top-level separators.
 */
private val TOP_LEVEL_COMMA_REGEX = Regex(""",\s*(?![^()\[\]{}]*[)\]}])""")

/**
 * Parses a navigation info object into a list of navigation key names.
 *
 * In adaptive layouts (e.g. on bigger screens like tablets, foldables, or desktop using two-pane, supporting pane, or list-detail
 * patterns), multiple screens can be displayed simultaneously. In Navigation3, these are represented as compound entries (such as a Pair,
 * List, or tuple) in the back stack history.
 *
 * @param navigationInfoItem The navigation history item object or string.
 * @return A list of parsed navigation keys.
 */
private fun parseNavigationItems(navigationInfoItem: Any): List<String> {
  // Extract the `key` property value from the toString() output if present (e.g. "NavigationInfo(key=...)"), otherwise use the full string.
  val key = KEY_REGEX.find(navigationInfoItem.toString())?.groupValues?.get(1)?.trim() ?: navigationInfoItem.toString()
  // Remove enclosing parentheses or brackets for compound entries (e.g. tuples or lists like "(ScreenA, ScreenB)").
  val unwrapped = key.trim().removeSurrounding("(", ")").removeSurrounding("[", "]").trim()
  // Guard against empty strings to avoid returning a single-element list with an empty string.
  if (unwrapped.isEmpty()) return emptyList()
  // Split on top-level commas (ignoring commas inside nested brackets/parens) and trim whitespace from each key.
  return unwrapped.split(TOP_LEVEL_COMMA_REGEX).map { it.trim() }.filter { it.isNotEmpty() }
}
