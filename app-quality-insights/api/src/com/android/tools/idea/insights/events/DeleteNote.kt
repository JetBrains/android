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

import com.android.tools.idea.insights.AppInsightsState
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.Note
import com.android.tools.idea.insights.NoteId
import com.android.tools.idea.insights.NoteState
import com.android.tools.idea.insights.Permission
import com.android.tools.idea.insights.analytics.AppInsightsTracker
import com.android.tools.idea.insights.events.actions.Action
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent

data class DeleteNoteRequested(val id: NoteId) : ChangeEvent {
  override fun transition(
    state: AppInsightsState,
    tracker: AppInsightsTracker
  ): StateTransition<Action> {
    check(!isCreatingNoteInProgress()) {
      "Deleting on \"creating in progress\" note is not allowed."
    }

    return StateTransition(
      newState =
        state.copy(
          issues = state.issues.incrementPendingRequests(id.issueId),
          currentNotes = state.currentNotes.markDeletePending(id)
        ),
      action = Action.DeleteNote(id)
    )
  }

  private fun isCreatingNoteInProgress(): Boolean {
    // This is just a local check, thus not accurate. But it's fine if we just use this to guard
    // against incorrect implementation.
    return id.noteId.isEmpty() && id.sessionId != null
  }

  private fun LoadingState<List<Note>?>.markDeletePending(id: NoteId): LoadingState<List<Note>> {
    return map {
      checkNotNull(it) { "No prior notes fetched, thus deleting any note is not allowed." }
      it.map { note -> if (note.id == id) note.copy(state = NoteState.DELETING) else note }
    }
  }
}

data class RollbackDeleteNoteRequest(val id: NoteId, val cause: LoadingState.Failure) :
  ChangeEvent {
  override fun transition(
    state: AppInsightsState,
    tracker: AppInsightsTracker
  ): StateTransition<Action> {
    return StateTransition(
      newState =
        state.copy(
          issues = state.issues.decrementPendingRequests(id.issueId),
          currentNotes = state.currentNotes.revertMarkDeletePending(id),
          permission = state.permission.updatePermissionIfApplicable(cause),
        ),
      action = Action.NONE
    )
  }

  private fun LoadingState<List<Note>?>.revertMarkDeletePending(
    id: NoteId
  ): LoadingState<List<Note>> {
    return map {
      checkNotNull(it) { "No prior notes fetched, thus deleting any note is not allowed." }
      it.map { note -> if (note.id == id) note.copy(state = NoteState.CREATED) else note }
    }
  }

  private fun Permission.updatePermissionIfApplicable(cause: LoadingState.Failure): Permission {
    return if (cause is LoadingState.PermissionDenied) Permission.READ_ONLY else this
  }
}

data class NoteDeleted(val id: NoteId) : ChangeEvent {
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
            noteEvent =
              AppQualityInsightsUsageEvent.AppQualityInsightsNotesDetails.NoteEvent.REMOVED
          }
          .build()
      )
    }
    return StateTransition(
      newState =
        state.copy(
          issues =
            state.issues.decrementPendingRequests(id.issueId).decrementNotesCount(id.issueId),
          currentNotes =
            if (state.selectedIssue?.id == id.issueId) state.currentNotes.delete(id)
            else state.currentNotes
        ),
      action = Action.NONE
    )
  }

  private fun LoadingState<List<Note>?>.delete(id: NoteId): LoadingState<List<Note>> {
    return map {
      checkNotNull(it) { "No prior notes fetched, thus deleting any note is not allowed." }
      it.filterNot { note -> note.id == id }
    }
  }
}
