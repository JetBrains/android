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

/**
 * Ensures the minimum number of [StatsGroup]s and [DataPoint]s within a group if applicable.
 *
 * Given a number N, the resulting [IssueStats] will have at least top N values plus the "Other"
 * value if applicable.
 */
const val MINIMUM_SUMMARY_GROUP_SIZE_TO_SHOW = 3

/**
 * Ensures we show distributions if the given manufacturer/model/OS accounts for more than this
 * percentage of the total.
 */
const val MINIMUM_PERCENTAGE_TO_SHOW = 10.0

private const val OTHER_GROUP = "Other"

/** Stats of an [Issue]. */
data class IssueStats<T : Number>(val topValue: String?, val groups: List<StatsGroup<T>>) {
  fun isEmpty() = topValue == null && groups.isEmpty()
}

/** A named group of [DataPoint]s. */
data class StatsGroup<T : Number>(
  val groupName: String,
  val percentage: T,
  val breakdown: List<DataPoint<T>>,
)

/** Represents a leaf named data point. */
data class DataPoint<T : Number>(val name: String, val percentage: T)

data class DetailedIssueStats(val deviceStats: IssueStats<Double>, val osStats: IssueStats<Double>)

fun <T : Number> T.percentOf(total: T): Double = ((this.toDouble() / total.toDouble()) * 100)

/**
 * Returns the count of elements from [this] whose values are greater than [threshold], meanwhile
 * [minElementCount] is ensured if not met.
 */
fun List<Double>.resolveElementCountBy(minElementCount: Int, threshold: Double): Int {
  return indexOfFirst { it <= threshold }.coerceAtLeast(minElementCount)
}

/**
 * Returns summarized distributions of Oses.
 *
 * Please ensure the input of the data points are sorted by [WithCount.count] in descending order.
 */
fun List<WithCount<OperatingSystemInfo>>.summarizeOsesFromRawDataPoints(
  minGroupSize: Int,
  minPercentage: Double,
): IssueStats<Double> {
  if (isEmpty()) return IssueStats(null, emptyList())

  val totalEvents = sumOf { it.count }
  val topOs = first().value.displayName

  val statsGroups = map {
    StatsGroup(it.value.displayName, it.count.percentOf(totalEvents), emptyList())
  }

  val resolvedGroupSize =
    statsGroups.map { it.percentage }.resolveElementCountBy(minGroupSize, minPercentage)

  val others = statsGroups.drop(resolvedGroupSize)
  return IssueStats(
    topOs,
    statsGroups.take(resolvedGroupSize) +
      if (others.isEmpty()) listOf()
      else
        listOf(
          StatsGroup(
            OTHER_GROUP,
            others.sumOf { it.percentage },
            others
              .map { DataPoint(it.groupName, it.percentage) }
              .let { points ->
                // Here we just show top N (minGroupSize) + "Other" in the sub "Other" group.
                points.take(minGroupSize) +
                  if (minGroupSize >= points.size) listOf()
                  else
                    listOf(
                      DataPoint(OTHER_GROUP, points.drop(minGroupSize).sumOf { it.percentage })
                    )
              },
          )
        ),
  )
}

/**
 * Returns summarized distributions of devices.
 *
 * Please ensure the input of the data points are sorted by [WithCount.count] in descending order.
 */
fun List<WithCount<Device>>.summarizeDevicesFromRawDataPoints(
  minGroupSize: Int,
  minPercentage: Double,
): IssueStats<Double> {
  if (isEmpty()) return IssueStats(null, emptyList())

  val topDevice = first().value
  val totalEvents = sumOf { it.count }

  val statsGroups =
    groupBy { it.value.manufacturer }
      .map { (manufacturer, reports) ->
        val groupEvents = reports.sumOf { it.count }

        val totalDataPoints =
          reports
            .sortedByDescending { it.count }
            .map { DataPoint(it.value.model.substringAfter("/"), it.count.percentOf(totalEvents)) }

        val resolvedGroupSize =
          totalDataPoints.map { it.percentage }.resolveElementCountBy(minGroupSize, minPercentage)

        val topGroupSizePercentages =
          totalDataPoints.take(resolvedGroupSize).sumOf { it.percentage }
        StatsGroup(
          manufacturer,
          groupEvents.percentOf(totalEvents),
          totalDataPoints.take(resolvedGroupSize) +
            if (resolvedGroupSize >= totalDataPoints.size) listOf()
            else
              listOf(
                DataPoint(OTHER_GROUP, groupEvents.percentOf(totalEvents) - topGroupSizePercentages)
              ),
        )
      }
      .sortedByDescending { it.percentage }

  val resolvedGroupSize =
    statsGroups.map { it.percentage }.resolveElementCountBy(minGroupSize, minPercentage)

  val others = statsGroups.drop(resolvedGroupSize)
  return IssueStats(
    topDevice.model,
    statsGroups.take(resolvedGroupSize) +
      if (others.isEmpty()) listOf()
      else
        listOf(
          StatsGroup(
            OTHER_GROUP,
            others.sumOf { it.percentage },
            others
              .map { DataPoint(it.groupName, it.percentage) }
              .let { points ->
                // Here we just show top N (minGroupSize) + "Other" in the sub "Other" group.
                points.take(minGroupSize) +
                  if (minGroupSize >= points.size) listOf()
                  else
                    listOf(
                      DataPoint(OTHER_GROUP, points.drop(minGroupSize).sumOf { it.percentage })
                    )
              },
          )
        ),
  )
}
