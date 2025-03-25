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
package com.android.tools.idea.insights.ui.insight

import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.insights.experiments.InsightFeedback
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.TestActionEvent
import icons.StudioIcons
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class InsightFeedbackPanelTest {
  @get:Rule val projectRule = ProjectRule()

  private lateinit var fakeUi: FakeUi

  private lateinit var feedbackStateFlow: MutableStateFlow<InsightFeedback>
  private lateinit var submittedFeedback: MutableList<InsightFeedback>

  @Before
  fun setup() = runBlocking {
    submittedFeedback = mutableListOf<InsightFeedback>()
    feedbackStateFlow = MutableStateFlow(InsightFeedback.NONE)
    withContext(AndroidDispatchers.uiThread) {
      fakeUi = FakeUi(InsightFeedbackPanel(feedbackStateFlow) { submittedFeedback.add(it) })
    }
  }

  @Test
  fun `test upvote and downvote actions`() = runBlocking {
    val (upvote, downvote) = fakeUi.findAllComponents<ActionButton>()
    assertThat(upvote.icon).isEqualTo(StudioIcons.Common.LIKE)
    assertThat(upvote.presentation.text).isEqualTo("Upvote this insight")
    assertThat(upvote.isSelected).isFalse()

    assertThat(downvote.icon).isEqualTo(StudioIcons.Common.DISLIKE)
    assertThat(downvote.presentation.text).isEqualTo("Downvote this insight")
    assertThat(upvote.isSelected).isFalse()

    val upvoteEvent = TestActionEvent.createTestEvent()
    val downvoteEvent = TestActionEvent.createTestEvent()

    feedbackStateFlow.value = InsightFeedback.THUMBS_UP
    upvote.actionPerformed(upvoteEvent)
    downvote.updateAction(downvoteEvent)
    assertThat(upvoteEvent.isSelected).isTrue()
    assertThat(downvoteEvent.isSelected).isFalse()

    feedbackStateFlow.value = InsightFeedback.THUMBS_DOWN
    downvote.actionPerformed(downvoteEvent)
    upvote.updateAction(upvoteEvent)
    assertThat(upvoteEvent.isSelected).isFalse()
    assertThat(downvoteEvent.isSelected).isTrue()

    feedbackStateFlow.value = InsightFeedback.NONE
    downvote.actionPerformed(downvoteEvent)
    upvote.updateAction(upvoteEvent)
    assertThat(upvoteEvent.isSelected).isFalse()
    assertThat(downvoteEvent.isSelected).isFalse()
  }

  @Test
  fun `test sentiment tracked when feedback clicked`() = runBlocking {
    val (upvote, downvote) = fakeUi.findAllComponents<ActionButton>()
    upvote.actionPerformed(TestActionEvent.createTestEvent())
    downvote.actionPerformed(TestActionEvent.createTestEvent())

    assertThat(submittedFeedback)
      .containsExactly(InsightFeedback.THUMBS_UP, InsightFeedback.THUMBS_DOWN)
      .inOrder()
  }

  private val AnActionEvent.isSelected: Boolean
    get() = Toggleable.isSelected(presentation)

  private fun ActionButton.updateAction(e: AnActionEvent) = action.update(e)

  private fun ActionButton.actionPerformed(e: AnActionEvent) {
    action.actionPerformed(e)
    action.update(e)
  }
}
