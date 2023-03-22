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
package com.android.tools.idea.vitals.client

import com.android.tools.idea.insights.Connection
import com.android.tools.idea.insights.ConnectionMode
import com.android.tools.idea.insights.DetailedIssueStats
import com.android.tools.idea.insights.IssueId
import com.android.tools.idea.insights.IssueState
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.Note
import com.android.tools.idea.insights.NoteId
import com.android.tools.idea.insights.Permission
import com.android.tools.idea.insights.client.AppInsightsClient
import com.android.tools.idea.insights.client.IssueRequest
import com.android.tools.idea.insights.client.IssueResponse
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent

// TODO(b/265153845): implement vitals client.
class VitalsClient : AppInsightsClient {
  override suspend fun listTopOpenIssues(
    request: IssueRequest,
    fetchSource: AppQualityInsightsUsageEvent.AppQualityInsightsFetchDetails.FetchSource?,
    mode: ConnectionMode,
    permission: Permission
  ): LoadingState.Done<IssueResponse> {
    return LoadingState.Ready(
      IssueResponse(emptyList(), emptyList(), emptyList(), emptyList(), Permission.FULL)
    )
  }

  override suspend fun getIssueDetails(
    issueId: IssueId,
    request: IssueRequest
  ): LoadingState.Done<DetailedIssueStats?> {
    TODO("Not yet implemented")
  }

  override suspend fun updateIssueState(
    connection: Connection,
    issueId: IssueId,
    state: IssueState
  ): LoadingState.Done<Unit> {
    TODO("Not yet implemented")
  }

  override suspend fun listNotes(
    connection: Connection,
    issueId: IssueId,
    mode: ConnectionMode
  ): LoadingState.Done<List<Note>> {
    TODO("Not yet implemented")
  }

  override suspend fun createNote(
    connection: Connection,
    issueId: IssueId,
    message: String
  ): LoadingState.Done<Note> {
    TODO("Not yet implemented")
  }

  override suspend fun deleteNote(connection: Connection, id: NoteId): LoadingState.Done<Unit> {
    TODO("Not yet implemented")
  }
}
