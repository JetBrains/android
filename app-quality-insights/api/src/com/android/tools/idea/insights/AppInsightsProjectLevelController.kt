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
package com.android.tools.idea.insights

import com.android.tools.idea.insights.analysis.StackTraceAnalyzer
import com.android.tools.idea.insights.analytics.IssueSelectionSource
import com.intellij.psi.PsiFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

/** Provides lifecycle and App Insights state data. */
interface AppInsightsProjectLevelController {

  /**
   * This flow represents the App Insights state of a host Android app module.
   *
   * The state includes:
   * * Active and available [Connection]s of a project.
   * * Active and available issues of the app(crashes).
   * * Active and available filters used to fetch the above issues.
   *
   * It contains many pieces of data all of which can change independently resulting in a new value
   * produced, as a result it is more convenient to [map] this flow into multiple sub flows that
   * "focus" on a subset of the data you care about. e.g.
   *
   * ```kotlin
   * val connections: Flow<Selection<VariantConnection>> = ctrl.state.map { it.connections }.distinctUntilChanged()
   * val issues: Flow<Selection<Issue>> = ctrl.state.filters.map { it.issues }.distinctUntilChanged() }
   * val selectedIssue: Flow<Issue?> = issues.mapReady { it.selected }.readyOrNull()
   * ```
   */
  val state: Flow<AppInsightsState>

  /** [CoroutineScope] whose lifecycle is tied to current configuration of the host module. */
  val coroutineScope: CoroutineScope

  // events
  fun refresh()
  fun selectIssue(value: AppInsightsIssue?, selectionSource: IssueSelectionSource)
  fun selectVersions(values: Set<Version>)

  fun selectDevices(values: Set<Device>)
  fun selectOperatingSystems(values: Set<OperatingSystemInfo>)
  fun selectTimeInterval(value: TimeIntervalFilter)
  fun toggleFailureType(value: FailureType)

  fun enterOfflineMode()
  fun retrieveLineMatches(file: PsiFile): List<AppInsight>
  fun insightsInFile(
    file: PsiFile,
    analyzer: StackTraceAnalyzer,
  )

  fun revertToSnapshot(state: AppInsightsState)
  fun selectSignal(value: SignalType)
  fun selectConnection(value: VariantConnection)
  fun openIssue(issue: AppInsightsIssue)
  fun closeIssue(issue: AppInsightsIssue)
  fun addNote(issue: AppInsightsIssue, message: String)
  fun deleteNote(note: Note)
}
