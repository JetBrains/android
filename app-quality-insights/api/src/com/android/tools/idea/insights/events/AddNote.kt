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
package com.android.tools.idea.insights.events

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.insights.AppInsightsIssue
import com.android.tools.idea.insights.AppInsightsState
import com.android.tools.idea.insights.IssueId
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.Note
import com.android.tools.idea.insights.NoteId
import com.android.tools.idea.insights.NoteState
import com.android.tools.idea.insights.Permission
import com.android.tools.idea.insights.Selection
import com.android.tools.idea.insights.Timed
import com.android.tools.idea.insights.analytics.AppInsightsTracker
import com.android.tools.idea.insights.events.actions.Action
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent
import java.time.Clock
import java.util.UUID

data class AddNoteRequested(
  val issueId: IssueId,
  val message: String,
  val clock: Clock,
  private val userEmail: String
) : ChangeEvent {
  override fun transition(
    state: AppInsightsState,
    tracker: AppInsightsTracker
  ): StateTransition<Action> {
    val sessionId = UUID.randomUUID().toString()
    val draft =
      Note(
        id = NoteId(issueId, "", sessionId),
        timestamp = clock.instant(),
        author = userEmail,
        body = message,
        state = NoteState.CREATING
      )

    return StateTransition(
      newState =
        state.copy(
          issues = state.issues.incrementPendingRequests(issueId),
          currentNotes = state.currentNotes.addDraft(draft)
        ),
      action = Action.AddNote(draft)
    )
  }

  private fun LoadingState<List<Note>?>.addDraft(note: Note): LoadingState<List<Note>> {
    return map {
      checkNotNull(it) { "No prior notes fetched, thus adding on new note is not allowed." }
      listOf(note) + it
    }
  }
}

data class RollbackAddNoteRequest(val noteId: NoteId, val cause: LoadingState.Failure) :
  ChangeEvent {
  override fun transition(
    state: AppInsightsState,
    tracker: AppInsightsTracker
  ): StateTransition<Action> {
    return StateTransition(
      newState =
        state.copy(
          issues = state.issues.decrementPendingRequests(noteId.issueId),
          currentNotes = state.currentNotes.deleteDraft(noteId.sessionId!!),
          permission = state.permission.updatePermissionIfApplicable(cause)
        ),
      action = Action.NONE
    )
  }

  private fun LoadingState<List<Note>?>.deleteDraft(sessionId: String): LoadingState<List<Note>> {
    return map {
      checkNotNull(it) { "No prior notes fetched, thus deleting any note is not allowed." }
      it.filterNot { note -> note.state == NoteState.CREATING && note.id.sessionId == sessionId }
    }
  }

  private fun Permission.updatePermissionIfApplicable(cause: LoadingState.Failure): Permission {
    return if (cause is LoadingState.PermissionDenied) Permission.READ_ONLY else this
  }
}

data class NoteAdded(val note: Note, val sessionId: String) : ChangeEvent {
  override fun transition(
    state: AppInsightsState,
    tracker: AppInsightsTracker
  ): StateTransition<Action> {
    state.connections.selected?.connection?.appId?.let { appId ->
      tracker.logNotesAction(
        appId,
        state.mode,
        AppQualityInsightsUsageEvent.AppQualityInsightsNotesDetails.newBuilder()
          .apply {
            noteEvent = AppQualityInsightsUsageEvent.AppQualityInsightsNotesDetails.NoteEvent.ADDED
          }
          .build()
      )
    }
    return StateTransition(
      newState =
        state.copy(
          issues =
            state.issues
              .decrementPendingRequests(note.id.issueId)
              .incrementNotesCount(note.id.issueId),
          currentNotes =
            if (state.selectedIssue?.id == note.id.issueId)
              state.currentNotes.markDraftDone(note, sessionId)
            else state.currentNotes
        ),
      action = Action.NONE
    )
  }

  private fun LoadingState<List<Note>?>.markDraftDone(
    newNote: Note,
    sessionId: String
  ): LoadingState<List<Note>> {
    return map {
      checkNotNull(it) { "No prior notes fetched, thus adding on new note is not allowed." }
      it.map { note ->
        if (note.state == NoteState.CREATING && note.id.sessionId == sessionId) newNote else note
      }
    }
  }
}

internal fun LoadingState<Timed<Selection<AppInsightsIssue>>>.incrementPendingRequests(
  issueId: IssueId
) =
  if (StudioFlags.OFFLINE_MODE_SUPPORT_ENABLED.get()) {
    applyUpdate(issueId, AppInsightsIssue::incrementPendingRequests)
  } else this

internal fun LoadingState<Timed<Selection<AppInsightsIssue>>>.decrementPendingRequests(
  issueId: IssueId
) =
  if (StudioFlags.OFFLINE_MODE_SUPPORT_ENABLED.get()) {
    applyUpdate(issueId, AppInsightsIssue::decrementPendingRequests)
  } else this

internal fun LoadingState<Timed<Selection<AppInsightsIssue>>>.incrementNotesCount(
  issueId: IssueId
) = applyUpdate(issueId, AppInsightsIssue::incrementNotesCount)

internal fun LoadingState<Timed<Selection<AppInsightsIssue>>>.decrementNotesCount(
  issueId: IssueId
) = applyUpdate(issueId, AppInsightsIssue::decrementNotesCount)

private fun LoadingState<Timed<Selection<AppInsightsIssue>>>.applyUpdate(
  issueId: IssueId,
  update: (AppInsightsIssue) -> AppInsightsIssue
): LoadingState<Timed<Selection<AppInsightsIssue>>> = map { timed ->
  timed.copy(
    value =
      timed.value.copy(
        selected =
          timed.value.selected?.let { selectedIssue ->
            if (selectedIssue.id == issueId) {
              update(selectedIssue)
            } else selectedIssue
          },
        items = timed.value.items.map { if (it.id == issueId) update(it) else it }
      )
  )
}
