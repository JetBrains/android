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
package com.android.tools.idea.insights.ui.actions

import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.insights.AppInsightsIssue
import com.android.tools.idea.insights.AppInsightsProjectLevelController
import com.android.tools.idea.insights.AppInsightsState
import com.android.tools.idea.insights.ConnectionMode
import com.android.tools.idea.insights.Event
import com.android.tools.idea.insights.FailureType
import com.android.tools.idea.insights.IssueDetails
import com.android.tools.idea.insights.IssueId
import com.android.tools.idea.insights.IssueState
import com.android.tools.idea.insights.Permission
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.testFramework.ProjectRule
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.times

class ToggleIssueActionTest {

  @get:Rule val projectRule = ProjectRule()

  private var controllerPermission = Permission.FULL
  private var connectionMode = ConnectionMode.ONLINE
  private val mockAppInsightState =
    mock<AppInsightsState>().apply {
      whenever(permission).thenAnswer { controllerPermission }
      whenever(mode).thenAnswer { connectionMode }
    }

  private val mockController = mock<AppInsightsProjectLevelController>()

  @Test
  fun `close issue`() {
    val issue = createAppInsightIssue(IssueState.OPEN)
    val toggleIssueAction = createToggleIssueAction(issue)
    val event = createAnActionEvent(toggleIssueAction, issue)

    toggleIssueAction.update(event)

    assertThat(event.presentation.text).isEqualTo("Close issue")
    assertThat(event.presentation.isEnabled).isTrue()

    toggleIssueAction.actionPerformed(event)

    Mockito.verify(mockController, times(1)).closeIssue(issue)
  }

  @Test
  fun `reopen issue`() {
    val issue = createAppInsightIssue(IssueState.CLOSED)
    val toggleIssueAction = createToggleIssueAction(issue)
    val event = createAnActionEvent(toggleIssueAction, issue)

    toggleIssueAction.update(event)

    assertThat(event.presentation.text).isEqualTo("Undo close")
    assertThat(event.presentation.isEnabled).isTrue()

    toggleIssueAction.actionPerformed(event)
    Mockito.verify(mockController, times(1)).openIssue(issue)
  }

  @Test
  fun `menu item has tooltip when user does not have permission to close issue`() {
    controllerPermission = Permission.READ_ONLY
    val issue = createAppInsightIssue(IssueState.OPEN)
    val toggleIssueAction = createToggleIssueAction(issue)
    val event = createAnActionEvent(toggleIssueAction, issue)

    toggleIssueAction.update(event)

    assertThat(event.presentation.text).isEqualTo("Close issue")
    assertThat(event.presentation.isEnabled).isFalse()
    assertThat(event.presentation.description)
      .isEqualTo("You don't have the necessary permissions to open/close issues.")
  }

  @Test
  fun `menu item has tooltip when AQI in offline mode`() {
    connectionMode = ConnectionMode.OFFLINE
    val issue = createAppInsightIssue(IssueState.OPEN)
    val toggleIssueAction = createToggleIssueAction(issue)
    val event = createAnActionEvent(toggleIssueAction, issue)

    toggleIssueAction.update(event)

    assertThat(event.presentation.text).isEqualTo("Close issue")
    assertThat(event.presentation.isEnabled).isFalse()
    assertThat(event.presentation.description).isEqualTo("AQI is offline.")
  }

  @Test
  fun `menu item disabled for opening state`() {
    val issue = createAppInsightIssue(IssueState.OPENING)
    val toggleIssueAction = createToggleIssueAction(issue)
    val event = createAnActionEvent(toggleIssueAction, issue)

    toggleIssueAction.update(event)

    assertThat(event.presentation.text).isEqualTo("Opening...")
    assertThat(event.presentation.isEnabled).isFalse()
  }

  @Test
  fun `menu item disabled for closing state`() {
    val issue = createAppInsightIssue(IssueState.CLOSING)
    val toggleIssueAction = createToggleIssueAction(issue)
    val event = createAnActionEvent(toggleIssueAction, issue)

    toggleIssueAction.update(event)

    assertThat(event.presentation.text).isEqualTo("Closing...")
    assertThat(event.presentation.isEnabled).isFalse()
  }

  private fun createToggleIssueAction(issue: AppInsightsIssue) =
    ToggleIssueAction(mockController, mockAppInsightState, issue)

  private fun createAnActionEvent(action: AnAction, issue: AppInsightsIssue) =
    AnActionEvent.createFromAnAction(action, null, "") {}

  private fun createAppInsightIssue(state: IssueState) =
    AppInsightsIssue(
      IssueDetails(
        IssueId("1234"),
        "Issue1",
        "com.google.crash.Crash",
        FailureType.FATAL,
        "Sample Event",
        "1.2.3",
        "1.2.3",
        8L,
        14L,
        5L,
        50L,
        emptySet(),
        "https://url.for-crash.com",
        0,
        annotations = emptyList(),
      ),
      Event(),
      state,
    )
}
