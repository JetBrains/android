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
package com.android.tools.profilers.taskbased.tabs.task.leakcanary.leaklist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.tools.profilers.leakcanary.LeakCanaryModel
import com.android.tools.profilers.leakcanary.LeakFilterScope
import com.android.tools.profilers.taskbased.common.constants.colors.TaskBasedUxColors
import com.android.tools.profilers.taskbased.common.constants.dimensions.TaskBasedUxDimensions
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings
import com.android.tools.profilers.taskbased.common.dropdowns.DropdownOptionText
import com.android.tools.profilers.taskbased.common.dropdowns.ProfilerComposeDropdown
import com.android.tools.profilers.taskbased.common.text.EllipsisText
import icons.StudioIconsCompose
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.icons.AllIconsKeys

/**
 * Renders the filter toolbar for the LeakCanary leak list. Includes a scope dropdown ([LeakFilterScope.ALL], [LeakFilterScope.APP],
 * [LeakFilterScope.LIBRARY]), an inline search input box, and checkboxes for Match Case and Regex matching.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LeakCanaryFilterBar(leakCanaryModel: LeakCanaryModel, modifier: Modifier = Modifier) {
  val filterScope by leakCanaryModel.filterScope.collectAsState()
  val searchQuery by leakCanaryModel.searchQuery.collectAsState()
  val matchCase by leakCanaryModel.matchCase.collectAsState()
  val useRegex by leakCanaryModel.useRegex.collectAsState()

  val searchFieldTextState = rememberTextFieldState(searchQuery)

  LaunchedEffect(searchQuery) {
    if (searchFieldTextState.text.toString() != searchQuery) {
      searchFieldTextState.setTextAndPlaceCursorAtEnd(searchQuery)
    }
  }

  LaunchedEffect(searchFieldTextState) {
    snapshotFlow { searchFieldTextState.text.toString() }.collect { newText -> leakCanaryModel.setSearchQuery(newText) }
  }

  Row(
    modifier = modifier.fillMaxWidth().height(TaskBasedUxDimensions.LEAKCANARY_PANE_HEADER_HEIGHT_DP).padding(horizontal = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Start,
  ) {
    Text(text = TaskBasedUxStrings.LEAKCANARY_FILTER_TYPE, color = TaskBasedUxColors.LABEL_DISABLED_FOREGROUND)
    Tooltip(tooltip = { Text(TaskBasedUxStrings.LEAKCANARY_TOOLTIP_FILTER_TYPE) }) {
      ProfilerComposeDropdown(
        modifier = Modifier.testTag("LeakFilterScopeDropdown"),
        menuContent = {
          LeakFilterScope.entries.forEach { scope ->
            selectableItem(selected = filterScope == scope, onClick = { leakCanaryModel.setFilterScope(scope) }) {
              DropdownOptionText(primaryText = getScopeDisplayName(scope), secondaryText = null, isEnabled = true)
            }
          }
        },
      ) {
        EllipsisText(text = getScopeDisplayName(filterScope))
      }
    }

    Spacer(modifier = Modifier.width(8.dp))

    TextField(
      searchFieldTextState,
      leadingIcon = {
        Icon(
          StudioIconsCompose.Common.Search,
          contentDescription = TaskBasedUxStrings.LEAKCANARY_CONTENT_DESCRIPTION_SEARCH,
          modifier = Modifier.padding(end = 4.dp),
        )
      },
      trailingIcon =
        (@Composable {
            Icon(
              AllIconsKeys.General.CloseSmall,
              contentDescription = TaskBasedUxStrings.LEAKCANARY_CONTENT_DESCRIPTION_CLEAR_SEARCH,
              modifier =
                Modifier.clickable(onClick = { searchFieldTextState.setTextAndPlaceCursorAtEnd("") }).pointerHoverIcon(PointerIcon.Default),
            )
          })
          .takeIf { searchFieldTextState.text.isNotEmpty() },
      placeholder = { Text(TaskBasedUxStrings.LEAKCANARY_FILTER_SEARCH_PLACEHOLDER, fontWeight = FontWeight.Light) },
      modifier = Modifier.width(TaskBasedUxDimensions.LEAKCANARY_SEARCH_BAR_WIDTH_DP).testTag("LeakCanarySearchTextField"),
    )

    Spacer(modifier = Modifier.width(8.dp))

    CheckboxRow(
      text = TaskBasedUxStrings.LEAKCANARY_FILTER_MATCH_CASE,
      checked = matchCase,
      onCheckedChange = { leakCanaryModel.setMatchCase(it) },
      modifier = Modifier.testTag("LeakCanaryMatchCaseCheckbox"),
    )

    Spacer(modifier = Modifier.width(8.dp))

    CheckboxRow(
      text = TaskBasedUxStrings.LEAKCANARY_FILTER_REGEX,
      checked = useRegex,
      onCheckedChange = { leakCanaryModel.setUseRegex(it) },
      modifier = Modifier.testTag("LeakCanaryRegexCheckbox"),
    )
  }
}

/** Maps a [LeakFilterScope] enum to its display label. */
private fun getScopeDisplayName(scope: LeakFilterScope): String {
  return when (scope) {
    LeakFilterScope.ALL -> TaskBasedUxStrings.LEAKCANARY_FILTER_SCOPE_ALL
    LeakFilterScope.APP -> TaskBasedUxStrings.LEAKCANARY_LEAK_TYPE_APP
    LeakFilterScope.LIBRARY -> TaskBasedUxStrings.LEAKCANARY_LEAK_TYPE_LIBRARY
  }
}
