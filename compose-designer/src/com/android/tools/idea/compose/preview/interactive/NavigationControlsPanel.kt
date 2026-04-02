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

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
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
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Slider
import org.jetbrains.jewel.ui.component.Text

/** @see also [NavigationControlsPanel] */
@Composable
fun NavigationControlsContent(
  interactivePreviewNavigationController: InteractivePreviewNavigationController,
  modifier: Modifier = Modifier,
) {
  Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    NavigationControlsPanel(modifier, interactivePreviewNavigationController)
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
 * @param interactivePreviewNavigationController The controller for handling interactive navigation events.
 */
@Composable
fun NavigationControlsPanel(modifier: Modifier = Modifier, interactivePreviewNavigationController: InteractivePreviewNavigationController) {
  var sliderPosition by remember { mutableFloatStateOf(0f) }
  var backStarted by remember { mutableStateOf(false) }
  val selectedEdge = remember { mutableStateOf(BackNavigationEdge.LEFT_EDGE) }

  Column(modifier.padding(16.dp).fillMaxWidth().testTag(NavigationControlsPanelTestTags.panel)) {
    Row(
      modifier = modifier.padding(vertical = 8.dp).fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      OutlinedButton(
        modifier = modifier.testTag(NavigationControlsPanelTestTags.backButton),
        enabled = true,
        onClick = {
          interactivePreviewNavigationController.backPressCompleted()
          interactivePreviewNavigationController.trackNavigationBackPress()
          backStarted = false
          sliderPosition = 0f
        },
      ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
          Icon(
            key = StudioIconsCompose.Emulator.Toolbar.Back,
            // The contentDescription is not needed as this icon is decorative to a text label which describes already what the button does.
            contentDescription = null,
            tint = Color(IntUiPaletteDefaults.Dark.Green7),
          )
          Text(text = message("action.navigate.back.button.text"))
        }
      }
      DropDownAction(modifier, message("action.navigate.back.navigation.edge.label"), selectedEdge, interactivePreviewNavigationController)
    }
    Row(
      modifier =
        modifier
          .padding(vertical = 8.dp)
          .fillMaxWidth()
          .border(width = 1.dp, color = JewelTheme.globalColors.borders.normal, shape = RoundedCornerShape(4.dp)),
      horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column {
        Text(modifier = modifier.padding(8.dp), text = message("action.navigate.back.predictive.back.progress", sliderPosition))
        Slider(
          modifier = modifier.padding(8.dp).testTag(NavigationControlsPanelTestTags.progressSlider),
          value = sliderPosition,
          valueRange = 0f..1f,
          onValueChange = {
            if (!backStarted) {
              backStarted = true
              interactivePreviewNavigationController.backPressStart(selectedEdge.value)
            }
            sliderPosition = it
          },
          onValueChangeFinished = { interactivePreviewNavigationController.trackNavigationProgressPress() },
        )
        SideEffect {
          if (backStarted) {
            interactivePreviewNavigationController.backPressProgress(sliderPosition, selectedEdge.value)
          }
        }
      }
    }
  }
}

/**
 * A dropdown component which allows the selection of [BackNavigationEdge]
 *
 * @param modifier The modifier to be applied to this Composable.
 * @param label The text to show in the Label located on the right of the Dropdown.
 * @param selectedEdge The edge to be selected among the [BackNavigationEdge] enum.
 */
@OptIn(ExperimentalJewelApi::class)
@Composable
private fun DropDownAction(
  modifier: Modifier = Modifier,
  label: String,
  selectedEdge: MutableState<BackNavigationEdge>,
  interactivePreviewNavigationController: InteractivePreviewNavigationController,
) =
  Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
    Text(text = label, modifier = Modifier.padding(8.dp))
    Dropdown(
      modifier = Modifier.testTag(NavigationControlsPanelTestTags.edgeDropdown),
      menuContent = {
        for (edge in BackNavigationEdge.entries) {
          selectableItem(
            selected = selectedEdge.value == edge,
            onClick = {
              selectedEdge.value = edge
              interactivePreviewNavigationController.trackEdgeDropdownPress()
            },
          ) {
            Text(text = edge.visibleName)
          }
        }
      },
    ) {
      Text(selectedEdge.value.visibleName)
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
}
