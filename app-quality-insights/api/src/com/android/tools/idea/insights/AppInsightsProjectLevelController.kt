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
package com.google.services.firebase.insights

import com.google.services.firebase.insights.datamodel.Device
import com.google.services.firebase.insights.datamodel.Fatality
import com.google.services.firebase.insights.datamodel.Issue
import com.google.services.firebase.insights.datamodel.Note
import com.google.services.firebase.insights.datamodel.OperatingSystemInfo
import com.google.services.firebase.insights.datamodel.SignalType
import com.google.services.firebase.insights.datamodel.TimeIntervalFilter
import com.google.services.firebase.insights.datamodel.Version
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent.AppQualityInsightsCrashOpenDetails.CrashOpenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

/** Provides lifecycle and App Insights state data. */
interface AppInsightsProjectLevelController {

  /** [CoroutineScope] whose lifecycle is tied to current configuration of the host project. */
  val coroutineScope: CoroutineScope

  /**
   * This flow represents the App Insights state of a host Android app module.
   *
   * The state includes:
   *
   * * Active and available [FirebaseConnection]s of a project.
   * * Active and available issues of the app(crashes).
   * * Active and available filters used to fetch the above issues.
   *
   * It contains many pieces of data all of which can change independently resulting in a new value
   * produced, as a result it is more convenient to [map] this flow into multiple sub flows that
   * "focus" on a subset of the data you care about. e.g.
   *
   * ```kotlin
   * val connections: Flow<Selection<FirebaseConnection>> = ctrl.state.map { it.connections }.distinctUntilChanged()
   * val issues: Flow<Selection<Issue>> = ctrl.state.filters.map { it.issues }.distinctUntilChanged() }
   * val selectedIssue: Flow<Issue?> = issues.mapReady { it.selected }.readyOrNull()
   * ```
   */
  val state: Flow<AppInsightsState>

  // events
  fun refresh()
  fun revertToSnapshot(state: AppInsightsState)
  fun selectIssue(value: Issue?, source: CrashOpenSource)
  fun selectVersions(values: Set<Version>)

  fun selectDevices(values: Set<Device>)
  fun selectSignal(value: SignalType)
  fun selectOperatingSystems(values: Set<OperatingSystemInfo>)
  fun selectTimeInterval(value: TimeIntervalFilter)
  fun selectFirebaseConnection(value: VariantConnection)
  fun toggleFatality(value: Fatality)

  fun openIssue(issue: Issue)
  fun closeIssue(issue: Issue)

  fun addNote(issue: Issue, message: String)
  fun deleteNote(note: Note)

  fun enterOfflineMode()
}