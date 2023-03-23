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
package com.android.tools.idea.insights

import com.android.testutils.MockitoKt
import com.android.testutils.time.FakeClock
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.insights.analytics.AppInsightsTracker
import com.android.tools.idea.insights.analytics.IssueSelectionSource
import com.android.tools.idea.insights.client.AppInsightsCache
import com.android.tools.idea.insights.client.AppInsightsCacheImpl
import com.android.tools.idea.insights.client.AppInsightsClient
import com.android.tools.idea.insights.client.IssueRequest
import com.android.tools.idea.insights.client.IssueResponse
import com.android.tools.idea.insights.events.actions.AppInsightsActionQueueImpl
import com.android.tools.idea.testing.NamedExternalResource
import com.google.common.truth.Truth.assertThat
import com.google.gct.login.GoogleLogin
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent.AppQualityInsightsFetchDetails.FetchSource
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.registerOrReplaceServiceInstance
import com.intellij.testFramework.runInEdtAndWait
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.event.HyperlinkListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.junit.runner.Description
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

private suspend fun <T> ReceiveChannel<T>.receiveWithTimeout(): T = withTimeout(5000) { receive() }

class AppInsightsProjectLevelControllerRule(
  private val projectRule: ProjectRule,
  private val onErrorAction: (String, HyperlinkListener?) -> Unit = { _, _ -> }
) : NamedExternalResource() {
  private val disposableRule = DisposableRule()
  val disposable: Disposable
    get() = disposableRule.disposable
  private lateinit var scope: CoroutineScope
  lateinit var clock: FakeClock
  lateinit var client: TestCrashlyticsClient
  lateinit var controller: AppInsightsProjectLevelController
  private lateinit var connections: MutableStateFlow<List<VariantConnection>>
  private lateinit var internalState: Channel<AppInsightsState>
  lateinit var tracker: AppInsightsTracker
  lateinit var connectionInferrer: ActiveConnectionInferrer
  private lateinit var cache: AppInsightsCache

  override fun before(description: Description) {
    val offlineStateFlow = MutableSharedFlow<ConnectionMode>(replay = 1)
    scope = AndroidCoroutineScope(disposable, AndroidDispatchers.uiThread)
    clock = FakeClock(NOW)
    cache = AppInsightsCacheImpl()
    client = Mockito.spy(TestCrashlyticsClient(cache))
    connections = MutableStateFlow(listOf(VARIANT1, VARIANT2, PLACEHOLDER_CONNECTION))
    tracker = mock(AppInsightsTracker::class.java)
    connectionInferrer = mock(ActiveConnectionInferrer::class.java)
    ApplicationManager.getApplication()
      .registerOrReplaceServiceInstance(
        GoogleLogin::class.java,
        MockitoKt.mock<GoogleLogin>().apply {
          `when`(this.getEmail()).thenReturn("testuser@gmail.com")
        },
        disposable
      )
    controller =
      AppInsightsProjectLevelControllerImpl(
        scope,
        AndroidDispatchers.workerThread,
        client,
        appConnection = connections,
        offlineStatus = offlineStateFlow,
        setOfflineMode = { mode -> scope.launch { offlineStateFlow.emit(mode) } },
        flowStart = SharingStarted.Lazily,
        tracker = tracker,
        clock = clock,
        project = projectRule.project,
        queue = AppInsightsActionQueueImpl(ConcurrentLinkedQueue()),
        onErrorAction = onErrorAction,
        connectionInferrer = connectionInferrer,
        defaultFilters = TEST_FILTERS,
        cache = cache
      )
    internalState = Channel(capacity = 3)
    scope.launch { controller.state.collect { internalState.send(it) } }
  }

  override fun after(description: Description) {
    internalState.close()
    runInEdtAndWait { disposableRule.after() }
  }

  suspend fun consumeFetchState(
    state: LoadingState.Ready<IssueResponse> =
      LoadingState.Ready(
        IssueResponse(
          emptyList(),
          listOf(DEFAULT_FETCHED_VERSIONS),
          listOf(DEFAULT_FETCHED_DEVICES),
          listOf(DEFAULT_FETCHED_OSES),
          DEFAULT_FETCHED_PERMISSIONS
        )
      ),
    detailsState: LoadingState.Done<DetailedIssueStats?> = LoadingState.Ready(null),
    notesState: LoadingState.Done<List<Note>> = LoadingState.Ready(emptyList()),
    isTransitionToOnlineMode: Boolean = false
  ): AppInsightsState {
    client.completeIssuesCallWith(state)
    if (isTransitionToOnlineMode) {
      assertThat(consumeNext().mode == ConnectionMode.ONLINE).isTrue()
    }
    var resultState = consumeNext()
    assertThat(resultState.filters.versions)
      .isEqualTo(MultiSelection(state.value.versions.toSet(), state.value.versions))
    if (state.value.issues.isNotEmpty()) {
      if (resultState.mode == ConnectionMode.ONLINE) {
        client.completeDetailsCallWith(detailsState)
      }
      consumeNext()
      client.completeListNotesCallWith(notesState)
      resultState = consumeNext()
    }
    return resultState
  }

  suspend fun consumeInitialState(
    state: LoadingState.Ready<IssueResponse> =
      LoadingState.Ready(
        IssueResponse(
          emptyList(),
          listOf(DEFAULT_FETCHED_VERSIONS),
          listOf(DEFAULT_FETCHED_DEVICES),
          listOf(DEFAULT_FETCHED_OSES),
          DEFAULT_FETCHED_PERMISSIONS
        )
      ),
    detailsState: LoadingState.Done<DetailedIssueStats?> = LoadingState.Ready(null),
    notesState: LoadingState.Done<List<Note>> = LoadingState.Ready(emptyList())
  ): AppInsightsState {
    val loadingState = consumeNext()
    assertThat(loadingState.connections)
      .isEqualTo(Selection(VARIANT1, listOf(VARIANT1, VARIANT2, PLACEHOLDER_CONNECTION)))
    assertThat(loadingState.issues).isInstanceOf(LoadingState.Loading::class.java)
    assertThat(loadingState.currentIssueDetails).isEqualTo(LoadingState.Ready(null))
    assertThat(loadingState.currentNotes).isEqualTo(LoadingState.Ready(null))
    return consumeFetchState(state, detailsState, notesState)
  }

  suspend fun consumeNext() = internalState.receiveWithTimeout()
  private suspend fun consumeLoading(): AppInsightsState {
    return internalState.receiveWithTimeout().also {
      assertThat(it.issues).isInstanceOf(LoadingState.Loading::class.java)
    }
  }
  suspend fun refreshAndConsumeLoadingState(): AppInsightsState {
    controller.refresh()
    return consumeLoading()
  }
  fun revertToSnapshot(state: AppInsightsState) = controller.revertToSnapshot(state)
  fun selectIssue(value: AppInsightsIssue?, source: IssueSelectionSource) =
    controller.selectIssue(value, source)

  fun selectVersions(values: Set<Version>) = controller.selectVersions(values)
  fun selectTimeInterval(value: TimeIntervalFilter) = controller.selectTimeInterval(value)
  fun selectSignal(value: SignalType) = controller.selectSignal(value)

  fun selectDevices(values: Set<Device>) = controller.selectDevices(values)
  fun selectFirebaseConnection(value: VariantConnection) = controller.selectConnection(value)
  fun toggleFatality(value: FailureType) = controller.toggleFailureType(value)
  fun updateConnections(variantConnections: List<VariantConnection>) =
    connections.update { variantConnections }
  fun enterOfflineMode() = controller.enterOfflineMode()
}

/** Utility class that allows suspending functions until `completeWith` is called. */
class CallInProgress<T> {
  private val channel = Channel<T>()
  private val inProgress = AtomicBoolean()
  suspend fun initiateCall(): T {
    if (!inProgress.compareAndSet(false, true)) {
      throw IllegalStateException("A call is already in progress")
    }
    return channel.receiveWithTimeout()
  }

  suspend fun completeWith(value: T) {
    channel.send(value)
    if (!inProgress.compareAndSet(true, false)) {
      throw IllegalStateException("No call is in progress")
    }
  }
}

/** Test client that gives precise control and synchronization useful in tests. */
class TestCrashlyticsClient(private val cache: AppInsightsCache) : AppInsightsClient {
  private val topIssuesCall = CallInProgress<LoadingState.Done<IssueResponse>>()
  private val detailsCall = CallInProgress<LoadingState.Done<DetailedIssueStats?>>()
  private val setIssueStateCall = CallInProgress<LoadingState.Done<Unit>>()
  private val listNotesCall = CallInProgress<LoadingState.Done<List<Note>>>()
  private val createNoteCall = CallInProgress<LoadingState.Done<Note>>()
  private val deleteNoteCall = CallInProgress<LoadingState.Done<Unit>>()

  override suspend fun listTopOpenIssues(
    request: IssueRequest,
    fetchSource: FetchSource?,
    mode: ConnectionMode,
    permission: Permission
  ): LoadingState.Done<IssueResponse> =
    topIssuesCall.initiateCall().also {
      if (it is LoadingState.Ready) {
        cache.populateIssues(request.connection, it.value.issues)
      }
    }

  suspend fun completeIssuesCallWith(value: LoadingState.Done<IssueResponse>) {
    topIssuesCall.completeWith(value)
  }

  override suspend fun getIssueDetails(
    issueId: IssueId,
    request: IssueRequest
  ): LoadingState.Done<DetailedIssueStats?> = detailsCall.initiateCall()

  suspend fun completeDetailsCallWith(value: LoadingState.Done<DetailedIssueStats?>) {
    detailsCall.completeWith(value)
  }

  override suspend fun updateIssueState(
    connection: Connection,
    issueId: IssueId,
    state: IssueState
  ): LoadingState.Done<Unit> = setIssueStateCall.initiateCall()

  suspend fun completeUpdateIssueStateCallWith(value: LoadingState.Done<Unit>) {
    setIssueStateCall.completeWith(value)
  }

  override suspend fun listNotes(
    connection: Connection,
    issueId: IssueId,
    mode: ConnectionMode
  ): LoadingState.Done<List<Note>> =
    listNotesCall.initiateCall().also {
      if (it is LoadingState.Ready) {
        cache.populateNotes(connection, issueId, it.value)
      }
    }

  suspend fun completeListNotesCallWith(value: LoadingState.Done<List<Note>>) {
    listNotesCall.completeWith(value)
  }

  override suspend fun createNote(
    connection: Connection,
    issueId: IssueId,
    message: String
  ): LoadingState.Done<Note> =
    createNoteCall.initiateCall().also {
      if (it is LoadingState.Ready) {
        cache.addNote(connection, issueId, it.value)
      }
    }

  suspend fun completeCreateNoteCallWith(value: LoadingState.Done<Note>) {
    createNoteCall.completeWith(value)
  }

  override suspend fun deleteNote(connection: Connection, id: NoteId): LoadingState.Done<Unit> =
    deleteNoteCall.initiateCall().also {
      if (it is LoadingState.Ready) {
        cache.removeNote(connection, id)
      }
    }

  suspend fun completeDeleteNoteCallWith(value: LoadingState.Done<Unit>) {
    deleteNoteCall.completeWith(value)
  }
}
