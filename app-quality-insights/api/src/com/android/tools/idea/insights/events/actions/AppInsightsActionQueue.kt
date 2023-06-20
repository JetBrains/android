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
import com.android.tools.idea.insights.Note
import com.android.tools.idea.insights.NoteState
import com.intellij.openapi.diagnostic.Logger
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue

interface AppInsightsActionQueue : Queue<Action> {
  /** Returns the resulting list of notes with pending changes applied to [notes]. */
  fun applyPendingNoteRequests(notes: List<Note>, issueId: IssueId): List<Note>

  /** Get a list of ids of issues with pending requests, in the order they are queued. */
  fun getPendingIssueIds(): List<IssueId>
}

class AppInsightsActionQueueImpl(private val queue: ConcurrentLinkedQueue<Action>) :
  Queue<Action> by queue, AppInsightsActionQueue {
  private val log: Logger
    get() = Logger.getInstance(AppInsightsActionQueue::class.java)

  override fun offer(e: Action): Boolean {
    log.debug("Queued action: $e")
    return queue.offer(e)
  }

  override fun poll(): Action? {
    return queue.poll().also { log.debug("Popped action: $it") }
  }

  override fun applyPendingNoteRequests(notes: List<Note>, issueId: IssueId): List<Note> {
    val pendingNotes =
      queue
        .filterIsInstance<Action.AddNote>()
        .filter { it.note.id.issueId == issueId }
        .map { it.note }
    val notesPendingDelete =
      queue
        .filterIsInstance<Action.DeleteNote>()
        .filter { it.noteId.issueId == issueId }
        .map { it.noteId.noteId }
        .toSet()
    return pendingNotes
      .reversed()
      .plus(
        notes.map { note ->
          if (note.id.noteId in notesPendingDelete) note.copy(state = NoteState.DELETING) else note
        }
      )
  }

  override fun getPendingIssueIds(): List<IssueId> =
    queue.filterIsInstance<Action.IssueAction>().map { it.id }.distinct()
}
