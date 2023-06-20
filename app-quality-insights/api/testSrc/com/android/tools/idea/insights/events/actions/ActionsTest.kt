/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.insights.events.actions

import com.android.tools.idea.insights.IssueId
import com.android.tools.idea.insights.NOTE1
import com.android.tools.idea.insights.NoteId
import com.android.tools.idea.insights.events.actions.Action.AddNote
import com.android.tools.idea.insights.events.actions.Action.CancelFetches
import com.android.tools.idea.insights.events.actions.Action.CloseIssue
import com.android.tools.idea.insights.events.actions.Action.DeleteNote
import com.android.tools.idea.insights.events.actions.Action.Fetch
import com.android.tools.idea.insights.events.actions.Action.FetchDetails
import com.android.tools.idea.insights.events.actions.Action.FetchNotes
import com.android.tools.idea.insights.events.actions.Action.Multiple
import com.android.tools.idea.insights.events.actions.Action.OpenIssue
import com.android.tools.idea.insights.events.actions.Action.Refresh
import com.android.tools.idea.insights.events.actions.Action.Single
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent.AppQualityInsightsFetchDetails.FetchSource
import org.junit.Test

private class CancellationEntry(
  val cancelledBy: List<Single> = listOf(),
  val notCancelledBy: List<Single> = listOf()
)

private val ID1 = IssueId("1")
private val ID2 = IssueId("2")
private val NOTE_ID1 = NoteId(issueId = ID1, noteId = "1", sessionId = "1")
private val fetchActions = listOf(Refresh, Fetch(FetchSource.FILTER), CancelFetches)
private val nonFetchActions =
  listOf(FetchDetails(ID1), OpenIssue(ID1), CloseIssue(ID2), FetchNotes(ID1), DeleteNote(NOTE_ID1))

private val CANCELLATION_TABLE =
  mapOf(
    Refresh to CancellationEntry(cancelledBy = fetchActions, notCancelledBy = nonFetchActions),
    Fetch(FetchSource.PROJECT_SELECTION) to
      CancellationEntry(cancelledBy = fetchActions, notCancelledBy = nonFetchActions),
    CancelFetches to
      CancellationEntry(cancelledBy = fetchActions, notCancelledBy = nonFetchActions),
    FetchDetails(ID1) to
      CancellationEntry(
        cancelledBy = fetchActions + listOf(FetchDetails(ID1), FetchDetails(ID2)),
        notCancelledBy = nonFetchActions - FetchDetails(ID1)
      ),
    OpenIssue(ID1) to
      CancellationEntry(
        cancelledBy = listOf(OpenIssue(ID1), CloseIssue(ID1)),
        notCancelledBy = fetchActions + listOf(OpenIssue(ID2), CloseIssue(ID2))
      ),
    CloseIssue(ID1) to
      CancellationEntry(
        cancelledBy = listOf(OpenIssue(ID1), CloseIssue(ID1)),
        notCancelledBy = fetchActions + listOf(OpenIssue(ID2), CloseIssue(ID2))
      ),
    FetchNotes(ID1) to
      CancellationEntry(
        cancelledBy = fetchActions + listOf(FetchNotes(ID1), FetchNotes(ID2)),
        notCancelledBy = nonFetchActions - FetchNotes(ID1)
      ),
    AddNote(NOTE1) to
      CancellationEntry(cancelledBy = listOf(), notCancelledBy = fetchActions + nonFetchActions),
    DeleteNote(NOTE_ID1) to CancellationEntry(cancelledBy = (listOf(DeleteNote(NOTE_ID1))))
  )

class ActionsTest {
  @Test
  fun `single actions are composed correctly wrt cancellation`() {
    for ((action, cancellationEntry) in CANCELLATION_TABLE) {
      for (cancellation in cancellationEntry.cancelledBy) {
        assertThat(action and cancellation).isEqualTo(cancellation)
        assertThat(action.maybeCancel(cancellation)).isNull()
      }
      for (cancellation in cancellationEntry.notCancelledBy) {
        assertThat((action.and(cancellation) as Multiple).actions)
          .isEqualTo(listOf(action, cancellation))
        assertThat(action.maybeCancel(cancellation)).isEqualTo(action)
      }
    }
  }

  @Test
  fun `Actions compose according to cancellation rules above`() {
    val originalMultipleAction = Refresh and FetchDetails(ID1) and FetchNotes(ID1)
    assertThat(originalMultipleAction.and(CancelFetches)).isEqualTo(CancelFetches)

    val anotherMultipleAction = AddNote(NOTE1) and Fetch(FetchSource.FILTER) and CloseIssue(ID2)
    val composed: Multiple = originalMultipleAction.and(anotherMultipleAction) as Multiple
    assertThat(composed.actions)
      .isEqualTo(listOf(AddNote(NOTE1), Fetch(FetchSource.FILTER), CloseIssue(ID2)))
  }

  @Test
  fun `Actions compose according to cancellation rules above when multiple previous issues are not cancelled`() {
    val originalMultipleAction = Refresh and AddNote(NOTE1) and CloseIssue(ID2)

    val anotherMultipleAction = AddNote(NOTE1) and Fetch(FetchSource.FILTER) and CloseIssue(ID1)
    val composed = originalMultipleAction.and(anotherMultipleAction) as Multiple
    assertThat(composed.actions)
      .isEqualTo(
        listOf(
          AddNote(NOTE1),
          CloseIssue(ID2),
          AddNote(NOTE1),
          Fetch(FetchSource.FILTER),
          CloseIssue(ID1)
        )
      )
  }

  @Test
  fun `Single action's isNoop is false`() {
    for (single in listOf(Refresh, Fetch(FetchSource.PROJECT_SELECTION), CloseIssue(ID2))) {
      assertThat(single.isNoop).isFalse()
    }
  }

  @Test
  fun `Action_NONE isNoop is true`() {
    assertThat(Action.NONE.isNoop).isTrue()
  }

  @Test
  fun `Composite action isNoop is false`() {
    assertThat((Refresh and CloseIssue(ID1)).isNoop).isFalse()
  }
}
