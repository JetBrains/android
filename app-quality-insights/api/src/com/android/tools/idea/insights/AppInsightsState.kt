package com.android.tools.idea.insights

import com.android.tools.idea.insights.client.Interval
import com.android.tools.idea.insights.client.IssueRequest
import com.android.tools.idea.insights.client.QueryFilters
import java.time.Duration
import java.time.Instant

/** Represents the state of filters applied to a module. */
data class Filters(
  /** Selection of [Version]s. */
  val versions: MultiSelection<WithCount<Version>>,
  /** Selection of [TimeIntervalFilter]s. */
  val timeInterval: Selection<TimeIntervalFilter>,
  /**
   * A list of [Fatality] toggles.
   *
   * TODO(b/228076042): add ANR back in the toggle list
   */
  val failureTypeToggles: MultiSelection<FailureType>,
  val devices: MultiSelection<WithCount<Device>> = MultiSelection.emptySelection(),
  val operatingSystems: MultiSelection<WithCount<OperatingSystemInfo>> =
    MultiSelection.emptySelection(),
  val signal: Selection<SignalType> = selectionOf(SignalType.SIGNAL_UNSPECIFIED)
) {
  fun withVersions(value: Set<Version>) =
    copy(versions = versions.selectMatching { it.value in value })
  fun withTimeInterval(value: TimeIntervalFilter?) = copy(timeInterval = timeInterval.select(value))
  fun withFatalityToggle(vararg toggles: FailureType) =
    copy(
      failureTypeToggles =
        toggles.fold(failureTypeToggles) { acc, fatality -> acc.toggle(fatality) }
    )

  fun withDevices(value: Set<Device>) = copy(devices = devices.selectMatching { it.value in value })

  fun withOperatingSystems(value: Set<OperatingSystemInfo>) =
    copy(operatingSystems = operatingSystems.selectMatching { it.value in value })

  fun withSignal(value: SignalType) = copy(signal = signal.select(value))
}

data class Timed<out V>(val value: V, val time: Instant)

/** Represents the App Insights state model. */
data class AppInsightsState(
  /** Available Connections. */
  val connections: Selection<VariantConnection>,

  /** Available time interval filter values. */
  val filters: Filters,

  /**
   * Data whose state depends on the above selections and is loaded asynchronously over the network.
   */
  val issues: LoadingState<Timed<Selection<AppInsightsIssue>>>,

  /**
   * Issue details whose state depends on the above selection and is loaded asynchronously over the
   * network.
   */
  val currentIssueDetails: LoadingState<DetailedIssueStats?> = LoadingState.Ready(null),

  /**
   * Notes whose state depends on the issue selection and is loaded asynchronously over the network.
   */
  val currentNotes: LoadingState<List<Note>?> = LoadingState.Ready(null),

  /** Access level of the currently logged-in user has on the insights API */
  val permission: Permission = Permission.NONE,
  val mode: ConnectionMode = ConnectionMode.ONLINE
) {
  val selectedIssue: AppInsightsIssue?
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
  fun toggleFatality(value: FailureType): AppInsightsState =
    copy(filters = filters.withFatalityToggle(value))

  /** Returns a new state with a new [FirebaseConnection] selected. */
  fun selectConnection(value: VariantConnection): AppInsightsState =
    copy(connections = connections.select(value))
}

fun AppInsightsState.toIssueRequest(): IssueRequest? {
  if (connections.selected?.connection == null || filters.timeInterval.selected == null) {
    return null
  }
  return IssueRequest(
    connection = connections.selected?.connection!!,
    filters =
      QueryFilters(
        interval =
          Instant.now().let {
            Interval(
              startTime = it.minus(Duration.ofDays(filters.timeInterval.selected!!.numDays)),
              endTime = it
            )
          },
        versions =
          if (filters.versions.allSelected()) setOf(Version.ALL)
          else filters.versions.selected.asSequence().map { it.value }.toSet(),
        devices =
          if (filters.devices.allSelected()) setOf(Device.ALL)
          else filters.devices.selected.asSequence().map { it.value }.toSet(),
        operatingSystems =
          if (filters.operatingSystems.allSelected()) setOf(OperatingSystemInfo.ALL)
          else filters.operatingSystems.selected.asSequence().map { it.value }.toSet(),
        eventTypes = filters.failureTypeToggles.selected.toList(),
        signal = filters.signal.selected ?: SignalType.SIGNAL_UNSPECIFIED
      )
  )
}
