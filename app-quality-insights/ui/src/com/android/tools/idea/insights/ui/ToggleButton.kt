/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.insights.ui

import com.android.tools.idea.insights.AppInsightsIssue
import com.android.tools.idea.insights.ConnectionMode
import com.android.tools.idea.insights.IssueState
import com.android.tools.idea.insights.Permission
import icons.StudioIcons
import javax.swing.JButton

/**
 * A toggle button that can open/close a given issue.
 *
 * It always keeps the state up to date by using the [withIssue] callback(i.e. every time the issue
 * changes, any registered [withIssue] callbacks will be executed).
 *
 * [withIssue] is most likely going to be implemented via a flow collector in an arbitrary scope,
 * the reason [withIssue] is used here is that it avoids having to pass in both the
 * [kotlinx.coroutines.flow.Flow] and the [kotlinx.coroutines.CoroutineScope] which makes using and
 * testing it easier.
 *
 * ```kotlin
 * val button = ToggleButton(
 *   withIssue = { block ->
 *     myScope.launch { myFlow.collect(block) }
 *   },
 *   onOpen = { issue -> open(issue) },
 *   onClose = { issue -> close(issue) }
 * )
 * ```
 *
 * @param withIssue executes registered callbacks when the issue changes.
 * @param onOpen executes whenever the issue is closed by the button click.
 * @param onClose executes whenever the issue is opened by the button click.
 */
@Suppress("FunctionName")
fun ToggleButton(
  withIssue: ((ToggleButtonState) -> Unit) -> Unit,
  onOpen: (AppInsightsIssue) -> Unit,
  onClose: (AppInsightsIssue) -> Unit
): JButton {
  val button = JButton("").apply { isOpaque = false }
  var activeIssue: AppInsightsIssue? = null
  withIssue { state ->
    activeIssue = state.issue
    when (state.issue.state) {
      IssueState.OPEN -> {
        button.icon = StudioIcons.Common.SUCCESS
        button.text = "Close"
        button.isEnabled = state.shouldBeEnabled()
      }
      IssueState.OPENING -> {
        button.icon = null
        button.text = "Opening..."
        button.isEnabled = false
      }
      IssueState.CLOSED -> {
        button.icon = null
        button.text = "Undo close"
        button.isEnabled = state.shouldBeEnabled()
      }
      IssueState.CLOSING -> {
        button.icon = null
        button.text = "Closing..."
        button.isEnabled = false
      }
    }
  }

  button.addActionListener {
    val issue = activeIssue ?: return@addActionListener
    when (issue.state) {
      // TODO(vkryachko): handle failure with API queue.
      IssueState.OPEN -> onClose(issue)
      IssueState.CLOSED -> onOpen(issue)
      else -> Unit
    }
  }
  return button
}

data class ToggleButtonEnabledState(val permission: Permission, val mode: ConnectionMode)

data class ToggleButtonState(
  val issue: AppInsightsIssue,
  val buttonState: ToggleButtonEnabledState
) {
  fun shouldBeEnabled() =
    buttonState.mode == ConnectionMode.ONLINE && buttonState.permission == Permission.FULL
}
