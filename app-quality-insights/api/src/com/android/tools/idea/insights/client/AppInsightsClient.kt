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
package com.android.tools.idea.insights.client

import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.model.connection.AppConnection
import com.android.tools.idea.insights.model.connection.Connection
import com.android.tools.idea.insights.model.connection.ConnectionMode
import com.android.tools.idea.insights.model.event.EventPage
import com.android.tools.idea.insights.model.issue.DetailedIssueStats
import com.android.tools.idea.insights.model.issue.FailureType
import com.android.tools.idea.insights.model.issue.IssueId
import com.android.tools.idea.insights.model.issue.IssueState
import com.android.tools.idea.insights.model.issue.IssueVariant
import com.android.tools.idea.insights.model.note.Note
import com.android.tools.idea.insights.model.note.NoteId

interface AppInsightsClient {
  suspend fun listConnections(): LoadingState.Done<List<AppConnection>>

  suspend fun listTopOpenIssues(
    request: IssueRequest,
    fetchSource: FetchSource? = null,
    mode: ConnectionMode = ConnectionMode.ONLINE,
    permission: Permission = Permission.NONE,
  ): LoadingState.Done<IssueResponse>

  suspend fun getIssueVariants(
    request: IssueRequest,
    issueId: IssueId,
  ): LoadingState.Done<List<IssueVariant>>

  suspend fun getIssueDetails(
    issueId: IssueId,
    request: IssueRequest,
    variantId: String? = null,
  ): LoadingState.Done<DetailedIssueStats?>

  suspend fun listEvents(
    issueId: IssueId,
    variantId: String?,
    request: IssueRequest,
    failureType: FailureType,
    token: String?,
  ): LoadingState.Done<EventPage>

  suspend fun updateIssueState(
    connection: Connection,
    issueId: IssueId,
    state: IssueState,
  ): LoadingState.Done<Unit>

  suspend fun listNotes(
    connection: Connection,
    issueId: IssueId,
    mode: ConnectionMode,
  ): LoadingState.Done<List<Note>>

  suspend fun createNote(
    connection: Connection,
    issueId: IssueId,
    message: String,
  ): LoadingState.Done<Note>

  suspend fun deleteNote(connection: Connection, id: NoteId): LoadingState.Done<Unit>
}
