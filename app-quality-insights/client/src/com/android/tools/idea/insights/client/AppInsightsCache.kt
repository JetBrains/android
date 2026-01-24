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
package com.android.tools.idea.insights.client

import com.android.tools.idea.insights.model.connection.Connection
import com.android.tools.idea.insights.model.event.Event
import com.android.tools.idea.insights.model.issue.AppInsightsIssue
import com.android.tools.idea.insights.model.issue.IssueId
import com.android.tools.idea.insights.model.note.Note
import com.android.tools.idea.insights.model.note.NoteId

/** Cache for App Insights data. */
interface AppInsightsCache {
  /**
   * Returns the most recently obtained connections.
   *
   * Used for Vitals only.
   */
  fun getRecentConnections(): List<Connection>

  /**
   * Sets the most recently obtained connections. Calling this has the side effect of evicting
   * values associated with connections not in [connections].
   *
   * Used for Vitals only.
   */
  fun populateConnections(connections: List<Connection>)

  /**
   * Returns the top reported [Issue]s stored in the cache. Returns null if no issues are cached for
   * this [FirebaseConnection].
   */
  fun getTopIssues(request: IssueRequest): List<AppInsightsIssue>?

  /** Returns the issues specified by [issueIds]. */
  fun getIssues(connection: Connection, issueIds: List<IssueId>): List<AppInsightsIssue>

  /** Populates the cache with recently fetched [Issue]s. */
  fun populateIssues(connection: Connection, issues: List<AppInsightsIssue>)

  /**
   * Returns an event that belongs to [issueId] and matches the filtering criteria. Null if such an
   * event does not exist.
   */
  fun getEvent(issueRequest: IssueRequest, issueId: IssueId): Event?

  /** Get cached notes based on [issueId]. Returns null if notes are not cached for this issue. */
  fun getNotes(connection: Connection, issueId: IssueId): List<Note>?

  /** Populate and overwrite the list of notes belonging to [issueId]. */
  fun populateNotes(connection: Connection, issueId: IssueId, notes: List<Note>)

  /** Adds a [Note] to the cache belonging to [issueId]. */
  fun addNote(connection: Connection, issueId: IssueId, note: Note)

  /**
   * Removes the note matching [com.android.tools.idea.insights.model.note.NoteId] from the cache.
   */
  fun removeNote(connection: Connection, noteId: NoteId)

  /** Removes the cached entry of an issue. */
  fun removeIssue(connection: Connection, issueId: IssueId)
}

class StubAppInsightsCache : AppInsightsCache {
  override fun getRecentConnections(): List<Connection> = emptyList()

  override fun populateConnections(connections: List<Connection>) = Unit

  override fun getTopIssues(request: IssueRequest): List<AppInsightsIssue>? = null

  override fun getIssues(connection: Connection, issueIds: List<IssueId>): List<AppInsightsIssue> =
    emptyList()

  override fun populateIssues(connection: Connection, issues: List<AppInsightsIssue>) = Unit

  override fun getEvent(issueRequest: IssueRequest, issueId: IssueId): Event? = null

  override fun getNotes(connection: Connection, issueId: IssueId): List<Note>? = null

  override fun populateNotes(connection: Connection, issueId: IssueId, notes: List<Note>) = Unit

  override fun addNote(connection: Connection, issueId: IssueId, note: Note) = Unit

  override fun removeNote(connection: Connection, noteId: NoteId) = Unit

  override fun removeIssue(connection: Connection, issueId: IssueId) = Unit
}
