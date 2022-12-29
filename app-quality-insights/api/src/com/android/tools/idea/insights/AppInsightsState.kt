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

import com.google.services.firebase.insights.datamodel.ConnectionMode
import com.google.services.firebase.insights.datamodel.DetailedIssueStats
import com.google.services.firebase.insights.datamodel.Device
import com.google.services.firebase.insights.datamodel.Fatality
import com.google.services.firebase.insights.datamodel.Issue
import com.google.services.firebase.insights.datamodel.Note
import com.google.services.firebase.insights.datamodel.OperatingSystemInfo
import com.google.services.firebase.insights.datamodel.Permission
import com.google.services.firebase.insights.datamodel.SignalType
import com.google.services.firebase.insights.datamodel.TimeIntervalFilter
import com.google.services.firebase.insights.datamodel.Version
import com.google.services.firebase.insights.datamodel.WithCount
import java.time.Instant

/** Represents the state of filters applied to a module. */
data class Filters(
  /** Selection of [Version]s. */
  val versions: MultiSelection<WithCount<Version>> = MultiSelection.emptySelection(),
  /** Selection of [TimeIntervalFilter]s. */
  val timeInterval: Selection<TimeIntervalFilter> = selectionOf(TimeIntervalFilter.THIRTY_DAYS),
  /**
   * A list of [Fatality] toggles.
   *
   * TODO(b/228076042): add ANR back in the toggle list
   */
  val fatalityToggles: MultiSelection<Fatality> =
    MultiSelection(
      setOf(Fatality.FATAL, Fatality.NON_FATAL),
      listOf(Fatality.FATAL, Fatality.NON_FATAL)
    ),
  val devices: MultiSelection<WithCount<Device>> = MultiSelection.emptySelection(),
  val operatingSystems: MultiSelection<WithCount<OperatingSystemInfo>> =
    MultiSelection.emptySelection(),
  val signal: Selection<SignalType> = selectionOf(SignalType.SIGNAL_UNSPECIFIED)
) {
  fun withVersions(value: Set<Version>): Filters =
    copy(versions = versions.selectMatching { it.value in value })
  fun withTimeInterval(value: TimeIntervalFilter?) = copy(timeInterval = timeInterval.select(value))
  fun withFatalityToggle(vararg toggles: Fatality) =
    copy(fatalityToggles = toggles.fold(fatalityToggles) { acc, fatality -> acc.toggle(fatality) })

  fun withDevices(value: Set<Device>): Filters =
    copy(devices = devices.selectMatching { it.value in value })

  fun withOperatingSystems(value: Set<OperatingSystemInfo>): Filters =
    copy(operatingSystems = operatingSystems.selectMatching { it.value in value })

  fun withSignal(value: SignalType) = copy(signal = signal.select(value))
}

data class Timed<out V>(val value: V, val time: Instant)

/** Represents the App Insights state model. */
data class AppInsightsState(
  /** Available Firebase Connections. */
  val connections: Selection<VariantConnection>,

  /** Available time interval filter values. */
  val filters: Filters,

  /**
   * Data whose state depends on the above selections and is loaded asynchronously over the network.
   */
  val issues: LoadingState<Timed<Selection<Issue>>>,

  /**
   * Issue details whose state depends on the above selection and is loaded asynchronously over the
   * network.
   */
  val currentIssueDetails: LoadingState<DetailedIssueStats?> = LoadingState.Ready(null),

  /**
   * Notes whose state depends on the issue selection and is loaded asynchronously over the network.
   */
  val currentNotes: LoadingState<List<Note>?> = LoadingState.Ready(null),

  /** Access level of the currently logged-in user has on Crashlytics */
  val permission: Permission = Permission.NONE,
  val mode: ConnectionMode = ConnectionMode.ONLINE
) {

  val selectedIssue: Issue?
    get() = if (issues is LoadingState.Ready) issues.value.value.selected else null

  /** Returns a new state with a new [TimeIntervalFilter] selected. */
  fun selectTimeInterval(value: TimeIntervalFilter?): AppInsightsState =
    copy(filters = filters.withTimeInterval(value))

  /** Returns a new state with the specified [Version]s selected. */
  fun selectVersions(value: Set<Version>): AppInsightsState =
    copy(filters = filters.withVersions(value))

  fun selectDevices(value: Set<Device>): AppInsightsState =
    copy(filters = filters.withDevices(value))

  fun selectOperatingSystems(value: Set<OperatingSystemInfo>): AppInsightsState =
    copy(filters = filters.withOperatingSystems(value))

  fun selectSignal(value: SignalType): AppInsightsState = copy(filters = filters.withSignal(value))

  /** Returns a new state with a new [Fatality] toggled. */
  fun toggleFatality(value: Fatality): AppInsightsState =
    copy(filters = filters.withFatalityToggle(value))

  /** Returns a new state with a new [FirebaseConnection] selected. */
  fun selectConnection(value: VariantConnection): AppInsightsState =
    copy(connections = connections.select(value))

  /** Returns a new state with a new [Issue] selected. */
  fun selectIssue(value: Issue?): AppInsightsState =
    if (issues !is LoadingState.Ready) this
    else
      copy(issues = LoadingState.Ready(Timed(issues.value.value.select(value), issues.value.time)))
}
