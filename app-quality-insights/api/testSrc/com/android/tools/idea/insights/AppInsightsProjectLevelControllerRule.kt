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

import com.android.testutils.time.FakeClock
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.insights.analytics.AppInsightsTracker
import com.android.tools.idea.insights.analytics.IssueSelectionSource
import com.android.tools.idea.insights.client.AppConnection
import com.android.tools.idea.insights.client.AppInsightsCache
import com.android.tools.idea.insights.client.AppInsightsCacheImpl
import com.android.tools.idea.insights.client.AppInsightsClient
import com.android.tools.idea.insights.client.IssueRequest
import com.android.tools.idea.insights.client.IssueResponse
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.NamedExternalResource
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent.AppQualityInsightsFetchDetails.FetchSource
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.runInEdtAndWait
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.event.HyperlinkListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.junit.runner.Description
import org.mockito.Mockito
import org.mockito.Mockito.mock

private suspend fun <T> ReceiveChannel<T>.receiveWithTimeout(): T = withTimeout(5000) { receive() }

class AppInsightsProjectLevelControllerRule(
  private val projectProvider: () -> Project,
  private val key: InsightsProviderKey,
  private val onErrorAction: (String, HyperlinkListener?) -> Unit = { _, _ -> },
) : NamedExternalResource() {
  constructor(
    projectRule: ProjectRule,
    key: InsightsProviderKey = TEST_KEY,
    onErrorAction: (String, HyperlinkListener?) -> Unit = { _, _ -> },
  ) : this({ projectRule.project }, key, onErrorAction)

  constructor(
    androidProjectRule: AndroidProjectRule,
    key: InsightsProviderKey = TEST_KEY,
    onErrorAction: (String, HyperlinkListener?) -> Unit = { _, _ -> },
  ) : this({ androidProjectRule.project }, key, onErrorAction)

  private val disposableRule = DisposableRule()
  val disposable: Disposable
    get() = disposableRule.disposable

  private lateinit var scope: CoroutineScope
  lateinit var clock: FakeClock
  lateinit var client: TestAppInsightsClient
  lateinit var controller: AppInsightsProjectLevelController
  private lateinit var connections: MutableSharedFlow<List<Connection>>
  private lateinit var internalState: Channel<AppInsightsState>
  lateinit var tracker: AppInsightsTracker
  private lateinit var cache: AppInsightsCache

  override fun before(description: Description) {
    val offlineStatusManager = OfflineStatusManagerImpl()
    scope = AndroidCoroutineScope(disposable, AndroidDispatchers.uiThread)
    clock = FakeClock(NOW)
    cache = AppInsightsCacheImpl()
    client = Mockito.spy(TestAppInsightsClient(cache))
    connections = MutableSharedFlow(replay = 1)
    tracker = mock(AppInsightsTracker::class.java)
    controller =
      AppInsightsProjectLevelControllerImpl(
        key,
        scope,
        AndroidDispatchers.workerThread,
        client,
        appConnection = connections,
        offlineStatusManager,
        flowStart = SharingStarted.Lazily,
        tracker = tracker,
        clock = clock,
        project = projectProvider(),
        onErrorAction = onErrorAction,
        defaultFilters = TEST_FILTERS,
        cache = cache,
      )
    internalState = Channel(capacity = 3)
    scope.launch { controller.state.collect { internalState.send(it) } }
  }

  override fun after(description: Description) {
    runInEdtAndWait { disposableRule.after() }
    internalState.close()
  }

  suspend fun consumeFetchState(
    state: LoadingState.Ready<IssueResponse> =
      LoadingState.Ready(
        IssueResponse(
          emptyList(),
          listOf(DEFAULT_FETCHED_VERSIONS),
          listOf(DEFAULT_FETCHED_DEVICES),
          listOf(DEFAULT_FETCHED_OSES),
          DEFAULT_FETCHED_PERMISSIONS,
        )
      ),
    issueVariantsState: LoadingState.Done<List<IssueVariant>> = LoadingState.Ready(emptyList()),
    eventsState: LoadingState.Done<EventPage> = LoadingState.Ready(EventPage.EMPTY),
    detailsState: LoadingState.Done<DetailedIssueStats?> = LoadingState.Ready(null),
    notesState: LoadingState.Done<List<Note>> = LoadingState.Ready(emptyList()),
    isTransitionToOnlineMode: Boolean = false,
  ): AppInsightsState {
    client.completeIssuesCallWith(state)
    if (isTransitionToOnlineMode) {
      assertThat(consumeNext().mode == ConnectionMode.ONLINE).isTrue()
    }
    var resultState = consumeNext()
    if (state.value.issues.isNotEmpty()) {
      if (resultState.mode == ConnectionMode.ONLINE) {
        client.completeDetailsCallWith(detailsState)
        if (key != VITALS_KEY) {
          client.completeIssueVariantsCallWith(issueVariantsState)
          client.completeListEvents(eventsState)
        }
      }
      if (key != VITALS_KEY) {
        consumeNext()
        consumeNext()
        consumeNext()
        client.completeListNotesCallWith(notesState)
      }
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
          DEFAULT_FETCHED_PERMISSIONS,
        )
      ),
    issueVariantsState: LoadingState.Done<List<IssueVariant>> = LoadingState.Ready(emptyList()),
    eventsState: LoadingState.Done<EventPage> = LoadingState.Ready(EventPage.EMPTY),
    detailsState: LoadingState.Done<DetailedIssueStats?> = LoadingState.Ready(null),
    notesState: LoadingState.Done<List<Note>> = LoadingState.Ready(emptyList()),
    connectionsState: List<Connection> = listOf(CONNECTION1, CONNECTION2, PLACEHOLDER_CONNECTION),
  ): AppInsightsState {
    connections.emit(connectionsState)
    val loadingState = consumeNext()
    assertThat(loadingState.connections)
      .isEqualTo(Selection(connectionsState.firstOrNull(), connectionsState))
    assertThat(loadingState.issues).isInstanceOf(LoadingState.Loading::class.java)
    assertThat(loadingState.currentIssueVariants).isEqualTo(LoadingState.Ready(null))
    assertThat(loadingState.currentIssueDetails).isEqualTo(LoadingState.Ready(null))
    assertThat(loadingState.currentNotes).isEqualTo(LoadingState.Ready(null))
    return consumeFetchState(state, issueVariantsState, eventsState, detailsState, notesState)
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

  fun selectOsVersion(value: Set<OperatingSystemInfo>) = controller.selectOperatingSystems(value)

  fun selectDevices(values: Set<Device>) = controller.selectDevices(values)

  fun selectFirebaseConnection(value: Connection) = controller.selectConnection(value)

  fun toggleFatality(value: FailureType) = controller.toggleFailureType(value)

  fun updateConnections(connections: List<Connection>) = this.connections.tryEmit(connections)

  fun enterOfflineMode() = controller.enterOfflineMode()

  fun selectVisibilityType(value: VisibilityType) = controller.selectVisibilityType(value)
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
class TestAppInsightsClient(private val cache: AppInsightsCache) : AppInsightsClient {
  private val listConnections = CallInProgress<LoadingState.Done<List<AppConnection>>>()
  private val topIssuesCall = CallInProgress<LoadingState.Done<IssueResponse>>()
  private val issueVariantsCall = CallInProgress<LoadingState.Done<List<IssueVariant>>>()
  private val detailsCall = CallInProgress<LoadingState.Done<DetailedIssueStats?>>()
  private val setIssueStateCall = CallInProgress<LoadingState.Done<Unit>>()
  private val listNotesCall = CallInProgress<LoadingState.Done<List<Note>>>()
  private val createNoteCall = CallInProgress<LoadingState.Done<Note>>()
  private val deleteNoteCall = CallInProgress<LoadingState.Done<Unit>>()
  private val listEventsCall = CallInProgress<LoadingState.Done<EventPage>>()
  private val fetchInsightCall = CallInProgress<LoadingState.Done<AiInsight>>()

  override suspend fun listConnections(): LoadingState.Done<List<AppConnection>> =
    listConnections.initiateCall()

  suspend fun completeConnectionsCallWith(value: LoadingState.Done<List<AppConnection>>) =
    listConnections.completeWith(value)

  override suspend fun listTopOpenIssues(
    request: IssueRequest,
    fetchSource: FetchSource?,
    mode: ConnectionMode,
    permission: Permission,
  ): LoadingState.Done<IssueResponse> =
    topIssuesCall.initiateCall().also {
      if (it is LoadingState.Ready) {
        cache.populateIssues(request.connection, it.value.issues)
      }
    }

  suspend fun completeIssuesCallWith(value: LoadingState.Done<IssueResponse>) {
    topIssuesCall.completeWith(value)
  }

  override suspend fun getIssueVariants(request: IssueRequest, issueId: IssueId) =
    issueVariantsCall.initiateCall()

  suspend fun completeIssueVariantsCallWith(value: LoadingState.Done<List<IssueVariant>>) {
    issueVariantsCall.completeWith(value)
  }

  override suspend fun getIssueDetails(
    issueId: IssueId,
    request: IssueRequest,
    variantId: String?,
  ): LoadingState.Done<DetailedIssueStats?> = detailsCall.initiateCall()

  override suspend fun listEvents(
    issueId: IssueId,
    variantId: String?,
    request: IssueRequest,
    failureType: FailureType,
    token: String?,
  ): LoadingState.Done<EventPage> = listEventsCall.initiateCall()

  suspend fun completeListEvents(value: LoadingState.Done<EventPage>) =
    listEventsCall.completeWith(value)

  suspend fun completeDetailsCallWith(value: LoadingState.Done<DetailedIssueStats?>) {
    detailsCall.completeWith(value)
  }

  override suspend fun updateIssueState(
    connection: Connection,
    issueId: IssueId,
    state: IssueState,
  ): LoadingState.Done<Unit> = setIssueStateCall.initiateCall()

  suspend fun completeUpdateIssueStateCallWith(value: LoadingState.Done<Unit>) {
    setIssueStateCall.completeWith(value)
  }

  override suspend fun listNotes(
    connection: Connection,
    issueId: IssueId,
    mode: ConnectionMode,
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
    message: String,
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

  override suspend fun fetchInsight(
    connection: Connection,
    issue: AppInsightsIssue,
    state: AppInsightsState,
  ) = fetchInsightCall.initiateCall()
}
