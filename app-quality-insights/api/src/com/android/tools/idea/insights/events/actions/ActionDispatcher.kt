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

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.insights.AppInsightsIssue
import com.android.tools.idea.insights.AppInsightsState
import com.android.tools.idea.insights.CancellableTimeoutException
import com.android.tools.idea.insights.Connection
import com.android.tools.idea.insights.ConnectionMode
import com.android.tools.idea.insights.Filters
import com.android.tools.idea.insights.IssueState
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.Permission
import com.android.tools.idea.insights.RevertibleException
import com.android.tools.idea.insights.Selection
import com.android.tools.idea.insights.client.AppInsightsCache
import com.android.tools.idea.insights.client.AppInsightsClient
import com.android.tools.idea.insights.client.IssueResponse
import com.android.tools.idea.insights.events.ChangeEvent
import com.android.tools.idea.insights.events.EnterOfflineMode
import com.android.tools.idea.insights.events.EnterOnlineMode
import com.android.tools.idea.insights.events.ErrorThrown
import com.android.tools.idea.insights.events.IssueDetailsChanged
import com.android.tools.idea.insights.events.IssueToggled
import com.android.tools.idea.insights.events.IssuesChanged
import com.android.tools.idea.insights.events.NoteAdded
import com.android.tools.idea.insights.events.NoteDeleted
import com.android.tools.idea.insights.events.NotesFetched
import com.android.tools.idea.insights.events.RollbackAddNoteRequest
import com.android.tools.idea.insights.events.RollbackDeleteNoteRequest
import com.android.tools.idea.insights.toIssueRequest
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent.AppQualityInsightsFetchDetails.FetchSource
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.time.Clock
import javax.swing.event.HyperlinkListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select

data class ActionContext(
  val action: Action,
  val currentState: AppInsightsState,
  val lastGoodState: AppInsightsState?
) {
  companion object {
    fun getDefaultState(defaultFilters: Filters) =
      ActionContext(
        Action.NONE,
        AppInsightsState(
          Selection.emptySelection(),
          defaultFilters,
          LoadingState.Loading,
          LoadingState.Ready(null)
        ),
        null
      )
  }
}

private val LOG = Logger.getInstance(ActionDispatcher::class.java)

class ActionDispatcher(
  private val scope: CoroutineScope,
  private val clock: Clock,
  private val appInsightsClient: AppInsightsClient,
  private val queue: AppInsightsActionQueue,
  private val defaultFilters: Filters,
  private val eventEmitter: suspend (ChangeEvent) -> Unit,
  private val onErrorAction: (String, HyperlinkListener?) -> Unit,
) {
  private val actions = Channel<ActionContext>()
  suspend fun dispatch(ctx: ActionContext) {
    actions.send(ctx)
  }

  init {
    scope.launch {
      var lastToken = CancellationToken.noop(Action.NONE)
      for (ctx in actions.batchWithTimeout(scope, 200)) {
        LOG.info("Dispatching actions ${ctx.action}")
        val newToken = doDispatch(ctx)
        // We keep holding on to not cancelled tokens since we may need to cancel them in the
        // future.
        val notCancelled = lastToken.cancel(newToken.action)
        lastToken = newToken and notCancelled
      }
    }
  }

  private fun doDispatch(ctx: ActionContext): CancellationToken {
    val (action, currentState, lastGoodState) = ctx
    val connection =
      currentState.connections.selected?.connection ?: return CancellationToken.noop(Action.NONE)

    return when (action) {
      is Action.Multiple ->
        CompositeCancellationToken(
          action.actions.map { doDispatch(ActionContext(it, currentState, lastGoodState)) }
        )
      is Action.Fetch -> fetchIssues(currentState, lastGoodState, action.reason, action)
      is Action.FetchDetails -> fetchDetails(currentState, action)
      is Action.Refresh -> fetchIssues(currentState, lastGoodState, FetchSource.REFRESH, action)
      is Action.OpenIssue -> openIssue(connection, action)
      is Action.CloseIssue -> closeIssue(connection, action)
      is Action.CancelFetches -> CancellationToken.noop(action)
      is Action.FetchNotes -> fetchNotes(connection, currentState, action)
      is Action.AddNote -> addNote(connection, action, currentState.mode)
      is Action.DeleteNote -> deleteNote(connection, action, currentState.mode)
      is Action.RetryPendingActions -> retryPendingActions(action, ctx)
    }
  }

  private fun openIssue(connection: Connection, action: Action.OpenIssue): CancellationToken {
    return scope
      .launch {
        when (
          val result = appInsightsClient.updateIssueState(connection, action.id, IssueState.OPEN)
        ) {
          is LoadingState.Failure -> {
            onErrorAction("Unable to open issue: ${result.cause?.message ?: ""}", null)
            eventEmitter(IssueToggled(action.id, IssueState.CLOSED, isUndo = true))
          }
          else -> {
            eventEmitter(IssueToggled(action.id, IssueState.OPEN))
          }
        }
      }
      .toToken(action)
  }

  private fun closeIssue(connection: Connection, action: Action.CloseIssue): CancellationToken {
    return scope
      .launch {
        when (
          val result = appInsightsClient.updateIssueState(connection, action.id, IssueState.CLOSED)
        ) {
          is LoadingState.Failure -> {
            onErrorAction("Unable to close issue: ${result.cause?.message ?: ""}", null)
            eventEmitter(IssueToggled(action.id, IssueState.OPEN, isUndo = true))
          }
          else -> {
            eventEmitter(IssueToggled(action.id, IssueState.CLOSED))
          }
        }
      }
      .toToken(action)
  }

  private fun fetchNotes(
    connection: Connection,
    state: AppInsightsState,
    action: Action.FetchNotes
  ): CancellationToken {
    return scope
      .launch {
        val fetchedNotes = appInsightsClient.listNotes(connection, action.id, state.mode)
        val result =
          fetchedNotes.map { notes -> queue.applyPendingNoteRequests(notes, action.id) }
            as LoadingState.Done
        eventEmitter(
          NotesFetched(
            action.id,
            result,
            state.mode == ConnectionMode.ONLINE &&
              state.permission == Permission.FULL &&
              StudioFlags.OFFLINE_MODE_SUPPORT_ENABLED.get() &&
              queue.size > 0
          )
        )
      }
      .toToken(action)
  }

  private fun addNote(
    connection: Connection,
    action: Action.AddNote,
    mode: ConnectionMode
  ): CancellationToken {
    return scope
      .launch {
        when (
          val result =
            appInsightsClient.createNote(connection, action.note.id.issueId, action.note.body)
        ) {
          is LoadingState.Ready -> {
            eventEmitter(NoteAdded(result.value, action.note.id.sessionId!!))
          }
          is LoadingState.Failure -> {
            onErrorAction(
              "Unable to post this note: ${result.cause?.message ?: result.message ?: "Unknown failure."}" +
                "<br><a href=\"copy\">Copy note to clipboard</a>",
              HyperlinkListener {
                val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                clipboard.setContents(StringSelection(action.note.body), null)
              }
            )
            eventEmitter(RollbackAddNoteRequest(action.note.id, result))
            if (result is LoadingState.NetworkFailure) {
              // b/260206459: disable queueing of actions
              // queue.offer(action)
              eventEmitter(EnterOfflineMode)
            }
          }
        }
      }
      .toToken(action)
  }

  private fun deleteNote(
    connection: Connection,
    action: Action.DeleteNote,
    mode: ConnectionMode
  ): CancellationToken {
    return scope
      .launch {
        when (val result = appInsightsClient.deleteNote(connection, action.noteId)) {
          is LoadingState.Ready -> {
            eventEmitter(NoteDeleted(action.noteId))
          }
          is LoadingState.Failure -> {
            onErrorAction(
              "Unable to delete this note: ${result.cause?.message ?: result.message ?: "Unknown failure."}",
              null
            )
            eventEmitter(RollbackDeleteNoteRequest(action.noteId, result))
            if (result is LoadingState.NetworkFailure) {
              eventEmitter(EnterOfflineMode)
              // b/260206459: disable queueing of actions
              // queue.offer(action)
            }
          }
        }
      }
      .toToken(action)
  }

  private fun fetchDetails(
    state: AppInsightsState,
    action: Action.FetchDetails
  ): CancellationToken {
    val issueRequest = state.toIssueRequest() ?: return CancellationToken.noop(Action.NONE)
    return scope
      .launch {
        if (state.mode == ConnectionMode.OFFLINE) {
          // TODO(peterx): fetch cached detailed if available.
          eventEmitter(
            IssueDetailsChanged(
              action.id,
              LoadingState.NetworkFailure("Distribution data is not available")
            )
          )
          return@launch
        }
        eventEmitter(
          IssueDetailsChanged(action.id, appInsightsClient.getIssueDetails(action.id, issueRequest))
        )
      }
      .toToken(action)
  }

  private fun fetchIssues(
    state: AppInsightsState,
    lastGoodState: AppInsightsState?,
    reason: FetchSource,
    action: Action.Single
  ): CancellationToken {
    val issueRequest = state.toIssueRequest() ?: return CancellationToken.noop(Action.NONE)
    val connectionMode = if (reason == FetchSource.REFRESH) ConnectionMode.ONLINE else state.mode
    return scope
      .launch {
        val timeoutJob = launch {
          delay(10_000L)
          eventEmitter(ErrorThrown(RevertibleException(lastGoodState, CancellableTimeoutException)))
        }
        val issuesWithPendingRequests = getIssuesWithPendingRequests(issueRequest.connection)
        val fetchResult =
          appInsightsClient.listTopOpenIssues(
            issueRequest,
            reason,
            connectionMode,
            state.permission
          )
        timeoutJob.cancelAndJoin()
        when (fetchResult) {
          is LoadingState.Ready -> {
            if (connectionMode == ConnectionMode.ONLINE && state.mode == ConnectionMode.OFFLINE) {
              eventEmitter(EnterOnlineMode)
            }
            eventEmitter(
              IssuesChanged(
                fetchResult.mergeWithPendingIssues(issuesWithPendingRequests),
                clock,
                lastGoodState
              )
            )
          }
          is LoadingState.Failure -> {
            eventEmitter(IssuesChanged(fetchResult, clock, lastGoodState))
          }
        }
      }
      .toToken(action)
  }

  private fun retryPendingActions(action: Action.Single, ctx: ActionContext): CancellationToken {
    return scope
      .launch {
        var pending = queue.poll()
        while (pending != null) {
          dispatch(ctx.copy(action = pending))
          pending = queue.poll()
        }
      }
      .toToken(action)
  }

  private fun LoadingState.Done<IssueResponse>.mergeWithPendingIssues(
    pendingIssues: List<AppInsightsIssue>
  ): LoadingState.Done<IssueResponse> =
    map { issueResponse ->
      val fetchedIssues = issueResponse.issues.associateBy { it.id }
      val pinnedIssues =
        pendingIssues.map { issue ->
          fetchedIssues[issue.id]?.copy(pendingRequests = issue.pendingRequests) ?: issue
        }
      val pendingIssueIds = pendingIssues.map { it.id }.toSet()
      val otherIssues = issueResponse.issues.filterNot { it.id in pendingIssueIds }
      issueResponse.copy(issues = pinnedIssues + otherIssues)
    }
      as LoadingState.Done

  // The cache does not know about pending requests, so the pending request counters are incremented
  // here.
  private fun getIssuesWithPendingRequests(connection: Connection): List<AppInsightsIssue> {
    val issues = service<AppInsightsCache>().getIssues(connection, queue.getPendingIssueIds())
    val pendingIssuesByCount = queue.filterIsInstance<Action.IssueAction>().groupBy { it.id }
    return issues.map { issue ->
      pendingIssuesByCount[issue.id]?.count()?.let { issue.copy(pendingRequests = it) } ?: issue
    }
  }

  private fun <T, U> ReceiveChannel<T>.batchFold(
    scope: CoroutineScope,
    timeout: Long,
    initialValue: U,
    fold: (U, T) -> U
  ): ReceiveChannel<U> {
    val batchedChannel = Channel<U>()
    scope.launch {
      var pendingValue = initialValue
      while (true) {
        select<Unit> {
          onReceive { pendingValue = fold(pendingValue, it) }
          onTimeout(timeout) {
            if (pendingValue == initialValue) return@onTimeout
            val newValue = pendingValue
            pendingValue = initialValue
            batchedChannel.send(newValue)
          }
        }
      }
    }
    return batchedChannel
  }

  private fun ReceiveChannel<ActionContext>.batchWithTimeout(scope: CoroutineScope, timeout: Long) =
    batchFold(scope, timeout, ActionContext.getDefaultState(defaultFilters)) { acc, actionContext ->
      ActionContext(
        acc.action and actionContext.action,
        actionContext.currentState,
        actionContext.lastGoodState
      )
    }
}
