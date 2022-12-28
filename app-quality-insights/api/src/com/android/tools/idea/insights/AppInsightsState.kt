package com.android.tools.idea.insights

import java.time.Instant

interface Filters {
  /** Selection of [Version]s. */
  val versions: MultiSelection<WithCount<Version>>

  /** Selection of [TimeIntervalFilter]s. */
  val timeInterval: Selection<TimeIntervalFilter>

  /** A list of error types. */
  val failureTypeToggles: MultiSelection<FailureType>
  val devices: MultiSelection<WithCount<Device>>
  val operatingSystems: MultiSelection<WithCount<OperatingSystemInfo>>
}

data class Timed<out V>(val value: V, val time: Instant)

interface AppInsightsState<IssueT : Issue> {
  /** Available time interval filter values. */
  val filters: Filters

  /**
   * Data whose state depends on the above selections and is loaded asynchronously over the network.
   */
  val issues: LoadingState<Timed<Selection<IssueT>>>

  /**
   * Issue details whose state depends on the above selection and is loaded asynchronously over the
   * network.
   */
  val currentIssueDetails: LoadingState<DetailedIssueStats?>

  val mode: ConnectionMode
}
