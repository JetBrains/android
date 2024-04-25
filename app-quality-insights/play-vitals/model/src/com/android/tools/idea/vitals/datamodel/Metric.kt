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
package com.android.tools.idea.vitals.datamodel

import java.math.BigDecimal

data class DimensionsAndMetrics(val dimensions: List<Dimension>, val metrics: List<Metric>)

data class Metric(val type: MetricType, val value: MetricValue) {
  companion object {
    fun fromProto(proto: com.google.play.developer.reporting.MetricValue): Metric {
      val metricType = proto.metric.toEnumMetricType()
      val metricValue = MetricValue.BigDecimalValue(BigDecimal(proto.decimalValue.value))

      return Metric(type = metricType, value = metricValue)
    }
  }
}

enum class MetricType(val value: String) {
  ERROR_REPORT_COUNT("errorReportCount"),
  DISTINCT_USER_COUNT("distinctUsers"),
}

sealed class MetricValue {
  data class BigDecimalValue(val value: BigDecimal) : MetricValue()
}

internal fun String.toEnumMetricType(): MetricType {
  return MetricType.values().firstOrNull { it.value == this }
    ?: throw IllegalStateException("$this is not of a recognizable dimension type.")
}

internal fun List<Metric>.extractValue(type: MetricType): Long {
  return filter { it.type == type }
    .map { (it.value as MetricValue.BigDecimalValue).value.toLong() }
    .single()
}
