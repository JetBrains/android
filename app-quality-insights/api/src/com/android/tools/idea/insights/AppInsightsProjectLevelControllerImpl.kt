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

import com.android.tools.idea.insights.analytics.AppInsightsTracker
import com.android.tools.idea.insights.analytics.IssueSelectionSource
import com.android.tools.idea.insights.client.AppInsightsCache
import com.android.tools.idea.insights.client.AppInsightsClient
import com.android.tools.idea.insights.events.ActiveConnectionChanged
import com.android.tools.idea.insights.events.AddNoteRequested
import com.android.tools.idea.insights.events.ChangeEvent
import com.android.tools.idea.insights.events.ConnectionsChanged
import com.android.tools.idea.insights.events.DeleteNoteRequested
import com.android.tools.idea.insights.events.DevicesChanged
import com.android.tools.idea.insights.events.EnterOfflineMode
import com.android.tools.idea.insights.events.ExplicitRefresh
import com.android.tools.idea.insights.events.FatalityToggleChanged
import com.android.tools.idea.insights.events.IntervalChanged
import com.android.tools.idea.insights.events.IssueToggled
import com.android.tools.idea.insights.events.OSesChanged
import com.android.tools.idea.insights.events.PersistSettingsAdapter
import com.android.tools.idea.insights.events.ResetSnapshot
import com.android.tools.idea.insights.events.RestoreFilterFromSettings
import com.android.tools.idea.insights.events.SafeFiltersAdapter
import com.android.tools.idea.insights.events.SelectedEventChanged
import com.android.tools.idea.insights.events.SelectedIssueChanged
import com.android.tools.idea.insights.events.SelectedIssueVariantChanged
import com.android.tools.idea.insights.events.SignalChanged
import com.android.tools.idea.insights.events.VersionsChanged
import com.android.tools.idea.insights.events.VisibilityChanged
import com.android.tools.idea.insights.events.actions.ActionContext
import com.android.tools.idea.insights.events.actions.ActionDispatcher
import com.android.tools.idea.insights.persistence.AppInsightsSettings
import com.android.tools.idea.insights.persistence.InsightsFilterSettings
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import java.time.Clock
import javax.swing.event.HyperlinkListener
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly

private val LOG = Logger.getInstance(AppInsightsProjectLevelControllerImpl::class.java)

private data class ProjectState(
  val currentState: AppInsightsState,
  val lastGoodState: AppInsightsState?,
)

class AppInsightsProjectLevelControllerImpl(
  override val key: InsightsProviderKey,
  override val coroutineScope: CoroutineScope,
  dispatcher: CoroutineDispatcher,
  appInsightsClient: AppInsightsClient,
  appConnection: Flow<List<Connection>>,
  private val offlineStatusManager: OfflineStatusManager,
  @TestOnly private val flowStart: SharingStarted = SharingStarted.Eagerly,
  private val tracker: AppInsightsTracker,
  private val clock: Clock,
  private val project: Project,
  onErrorAction: (String, HyperlinkListener?) -> Unit,
  private val defaultFilters: Filters,
  cache: AppInsightsCache,
) : AppInsightsProjectLevelController {

  override val state: SharedFlow<AppInsightsState>

  private val dispatcherScope = CoroutineScope(coroutineScope.coroutineContext + dispatcher)

  private val actionDispatcher =
    ActionDispatcher(
      dispatcherScope,
      clock,
      appInsightsClient,
      defaultFilters,
      cache,
      ::doEmit,
      onErrorAction,
    )

  /**
   * Represents a flow that is used by filter selectors and refresh to inject [ChangeEvent]s into
   * the main event flow(below).
   */
  val eventFlow: MutableSharedFlow<ChangeEvent> = MutableSharedFlow(extraBufferCapacity = 2)

  private val settings: InsightsFilterSettings?
    get() = project.service<AppInsightsSettings>().tabSettings[key.displayName]

  /** Restores persisted settings on the first non empty connections list. */
  private val connectionsFlow = flow {
    var shouldRestorePreviousConfig = true
    appConnection.collect { connections ->
      var event: ChangeEvent = ConnectionsChanged(connections, defaultFilters)
      if (shouldRestorePreviousConfig && connections.isNotEmpty()) {
        shouldRestorePreviousConfig = false
        settings?.let { event = RestoreFilterFromSettings(it, event) }
      }
      emit(wrapAdapters(event))
    }
  }

  init {
    val initialState =
      ProjectState(
        AppInsightsState(
          Selection.emptySelection(),
          defaultFilters,
          LoadingState.Loading,
          LoadingState.Ready(null),
          LoadingState.Ready(null),
          LoadingState.Ready(null),
          LoadingState.Ready(null),
          Permission.NONE,
          ConnectionMode.ONLINE,
        ),
        null,
      )

    @Suppress("RequiredOptIn")
    state =
      merge(
          eventFlow.map { wrapAdapters(it) },
          connectionsFlow,
          offlineStatusManager.offlineStatus.map { it.toEvent() },
        )
        .fold(initialState) { (currentState, lastGoodState), event ->
          LOG.debug("Got event $event for $project.")
          val (newState, action) = event.transition(currentState, tracker, key)
          if (currentState.issues != newState.issues) {
            project
              .service<IssuesPerFileIndex>()
              .updateIssueIndex(newState.issues.map { it.value }, key)
          }
          if (currentState.mode != newState.mode) {
            offlineStatusManager.enterMode(newState.mode)
          }
          ProjectState(
              currentState = newState,
              lastGoodState =
                if (
                  newState.issues is LoadingState.Ready &&
                    newState.currentNotes is LoadingState.Ready
                )
                  newState
                else lastGoodState,
            )
            .also { (currentState, lastGoodState) ->
              actionDispatcher.dispatch(ActionContext(action, currentState, lastGoodState))
            }
        }
        .map { it.currentState }
        .distinctUntilChanged()
        .shareIn(dispatcherScope, started = flowStart, replay = 1)
  }

  override fun selectVersions(values: Set<Version>) {
    emit(VersionsChanged(values))
  }

  override fun selectDevices(values: Set<Device>) {
    emit(DevicesChanged(values))
  }

  override fun selectOperatingSystems(values: Set<OperatingSystemInfo>) {
    emit(OSesChanged(values))
  }

  override fun selectSignal(value: SignalType) {
    emit(SignalChanged(value))
  }

  override fun selectConnection(value: Connection) {
    emit(ActiveConnectionChanged(value))
  }

  override fun nextEvent() {
    emit(SelectedEventChanged(EventMovement.NEXT))
  }

  override fun previousEvent() {
    emit(SelectedEventChanged(EventMovement.PREVIOUS))
  }

  override fun toggleFailureType(value: FailureType) {
    emit(FatalityToggleChanged(value))
  }

  override fun openIssue(issue: AppInsightsIssue) {
    emit(IssueToggled(issue.id, IssueState.OPENING))
  }

  override fun closeIssue(issue: AppInsightsIssue) {
    emit(IssueToggled(issue.id, IssueState.CLOSING))
  }

  override fun enterOfflineMode() {
    emit(EnterOfflineMode)
  }

  override fun addNote(issue: AppInsightsIssue, message: String) {
    emit(AddNoteRequested(issue.id, message, clock))
  }

  override fun deleteNote(note: Note) {
    emit(DeleteNoteRequested(note.id))
  }

  override fun selectVisibilityType(value: VisibilityType) {
    emit(VisibilityChanged(value))
  }

  override fun selectIssueVariant(variant: IssueVariant?) {
    emit(SelectedIssueVariantChanged(variant))
  }

  override fun selectTimeInterval(value: TimeIntervalFilter) {
    emit(IntervalChanged(value))
  }

  override fun selectIssue(value: AppInsightsIssue?, selectionSource: IssueSelectionSource) {
    emit(SelectedIssueChanged(value))
  }

  override fun refresh() {
    emit(ExplicitRefresh)
  }

  override fun revertToSnapshot(state: AppInsightsState) {
    emit(ResetSnapshot(state))
  }

  private fun emit(event: ChangeEvent) {
    dispatcherScope.launch { doEmit(event) }
  }

  private suspend fun doEmit(event: ChangeEvent) {
    eventFlow.emit(event)
  }

  override fun insightsInFile(file: PsiFile): List<AppInsight> {
    val issues =
      project
        .service<IssuesPerFileIndex>()
        .getIssuesPerFilename(key)
        .get(file.virtualFile.name)
        .toList()

    logIssues(issues, file)

    val selectIssueCallback = { issue: AppInsightsIssue ->
      selectIssue(issue, IssueSelectionSource.INSPECTION)
    }

    return issues.map { issueInFrame ->
      AppInsight(
        line = issueInFrame.crashFrame.frame.line.toInt() - 1,
        issue = issueInFrame.issue,
        stackFrame = issueInFrame.crashFrame.frame,
        cause = issueInFrame.crashFrame.cause,
        provider = key,
        markAsSelectedCallback = selectIssueCallback,
      )
    }
  }

  private fun logIssues(issues: List<IssueInFrame>, file: PsiFile) {
    if (issues.isEmpty()) return
    val formattedIssues =
      issues
        .map { it.issue.issueDetails.subtitle.ifEmpty { "<missingSubtitle>" } }
        .reduce { acc, value -> acc + "\n" + value }
    LOG.debug(
      "Found ${issues.size} issues related to ${file.name} [\n${formattedIssues}], analyzing..."
    )
  }

  private fun wrapAdapters(event: ChangeEvent) =
    PersistSettingsAdapter(SafeFiltersAdapter(event), project, key)
}
