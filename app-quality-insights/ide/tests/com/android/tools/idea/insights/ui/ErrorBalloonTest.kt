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

import com.android.flags.junit.FlagRule
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.insights.AppInsightsProjectLevelControllerRule
import com.android.tools.idea.insights.ISSUE1
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.NOTE1
import com.android.tools.idea.insights.NOTE2_BODY
import com.android.tools.idea.insights.Permission
import com.android.tools.idea.insights.client.IssueResponse
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.wm.ToolWindowBalloonShowOptions
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.replaceService
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.ArgumentMatcher
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class ErrorBalloonTest {

  private val projectRule = ProjectRule()
  private val controllerRule =
    AppInsightsProjectLevelControllerRule(projectRule) { msg, hyperlinkListener ->
      AppInsightsToolWindowFactory.showBalloon(
        projectRule.project,
        MessageType.ERROR,
        msg,
        hyperlinkListener
      )
    }

  @get:Rule val ruleChain = RuleChain.outerRule(projectRule).around(controllerRule)!!

  @get:Rule val flagRule = FlagRule(StudioFlags.OFFLINE_MODE_SUPPORT_ENABLED, true)

  private class TransferableArgumentMatcher(private val expectedData: String) :
    ArgumentMatcher<Transferable> {
    override fun matches(actualTransferable: Transferable) =
      expectedData == actualTransferable.getTransferData(DataFlavor.stringFlavor)
  }

  @Test
  fun `add note during network failure causes balloon with copy link`() = runBlocking {
    var balloonShown = false
    val clipboard = mock(Clipboard::class.java)
    val mockToolkit = mock(Toolkit::class.java)
    `when`(mockToolkit.systemClipboard).thenReturn(clipboard)

    projectRule.project.replaceService(
      ToolWindowManager::class.java,
      object : ToolWindowHeadlessManagerImpl(projectRule.project) {
        override fun notifyByBalloon(options: ToolWindowBalloonShowOptions) {
          assertThat(options.htmlBody).contains("Copy note to clipboard")
          Mockito.mockStatic(Toolkit::class.java).use { toolkit ->
            toolkit.`when`<Toolkit>(Toolkit::getDefaultToolkit).thenReturn(mockToolkit)
            options.listener?.hyperlinkUpdate(null)
          }
          verify(clipboard)
            .setContents(
              ArgumentMatchers.argThat(
                TransferableArgumentMatcher("Update: I managed to reproduce this issue.")
              ),
              ArgumentMatchers.isNull()
            )
          balloonShown = true
        }
      },
      controllerRule.disposable
    )

    val testIssue = ISSUE1.copy(issueDetails = ISSUE1.issueDetails.copy(notesCount = 1))
    controllerRule.consumeInitialState(
      state =
        LoadingState.Ready(
          IssueResponse(listOf(testIssue), emptyList(), emptyList(), emptyList(), Permission.FULL)
        ),
      notesState = LoadingState.Ready(listOf(NOTE1))
    )

    controllerRule.controller.addNote(testIssue, NOTE2_BODY)
    controllerRule.consumeNext()

    controllerRule.client.completeCreateNoteCallWith(LoadingState.NetworkFailure(null))
    controllerRule.consumeNext()

    assertThat(balloonShown).isTrue()
  }
}
