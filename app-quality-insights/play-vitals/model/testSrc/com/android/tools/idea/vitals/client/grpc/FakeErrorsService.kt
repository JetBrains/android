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
package com.android.tools.idea.vitals.client.grpc

import com.android.tools.idea.io.grpc.stub.StreamObserver
import com.android.tools.idea.protobuf.GeneratedMessageV3
import com.android.tools.idea.protobuf.TextFormat
import com.android.tools.idea.vitals.datamodel.DimensionType
import com.android.tools.idea.vitals.datamodel.VitalsConnection
import com.google.play.developer.reporting.AggregationPeriod
import com.google.play.developer.reporting.ErrorCountMetricSet
import com.google.play.developer.reporting.ErrorReport
import com.google.play.developer.reporting.FreshnessInfo
import com.google.play.developer.reporting.GetErrorCountMetricSetRequest
import com.google.play.developer.reporting.QueryErrorCountMetricSetRequest
import com.google.play.developer.reporting.QueryErrorCountMetricSetResponse
import com.google.play.developer.reporting.SearchErrorIssuesRequest
import com.google.play.developer.reporting.SearchErrorIssuesResponse
import com.google.play.developer.reporting.SearchErrorReportsRequest
import com.google.play.developer.reporting.SearchErrorReportsResponse
import com.google.play.developer.reporting.VitalsErrorsServiceGrpc
import java.time.Clock
import java.time.ZoneId
import kotlinx.coroutines.channels.SendChannel

const val MOST_AFFECTED_OS = "Android 13"

private val SAMPLE_OS_LEVEL_DISTRIBUTION =
  """
      rows {
        aggregation_period: FULL_RANGE
        start_time {
          year: 2023
          month: 5
          day: 3
          time_zone {
            id: "America/Los_Angeles"
          }
        }
        dimensions {
          dimension: "reportType"
          string_value: "CRASH"
        }
        dimensions {
          dimension: "apiLevel"
          string_value: "28"
          value_label: "Android 9"
        }
        metrics {
          metric: "___METRIC_TYPE___"
          decimal_value {
            value: "2"
          }
        }
      }
      rows {
        aggregation_period: FULL_RANGE
        start_time {
          year: 2023
          month: 5
          day: 3
          time_zone {
            id: "America/Los_Angeles"
          }
        }
        dimensions {
          dimension: "reportType"
          string_value: "CRASH"
        }
        dimensions {
          dimension: "apiLevel"
          string_value: "33"
          value_label: "$MOST_AFFECTED_OS"
        }
        metrics {
          metric: "___METRIC_TYPE___"
          decimal_value {
            value: "3"
          }
        }
      }
    """
    .trimIndent()

private val SAMPLE_DEVICE_DISTRIBUTION =
  """
  rows {
    aggregation_period: FULL_RANGE
    start_time {
      year: 2023
      month: 5
      day: 3
      time_zone {
        id: "America/Los_Angeles"
      }
    }
    dimensions {
      dimension: "reportType"
      string_value: "CRASH"
    }
    dimensions {
      dimension: "deviceBrand"
      string_value: "samsung"
      value_label: "samsung"
    }
    dimensions {
      dimension: "deviceModel"
      string_value: "samsung/a32"
      value_label: "samsung a32 (Galaxy A32)"
    }
    dimensions {
      dimension: "deviceType"
      string_value: "PHONE"
      value_label: "Phone"
    }
    metrics {
      metric: "___METRIC_TYPE___"
      decimal_value {
        value: "3"
      }
    }
  }
  rows {
    aggregation_period: FULL_RANGE
    start_time {
      year: 2023
      month: 5
      day: 3
      time_zone {
        id: "America/Los_Angeles"
      }
    }
    dimensions {
      dimension: "reportType"
      string_value: "CRASH"
    }
    dimensions {
      dimension: "deviceBrand"
      string_value: "samsung"
      value_label: "samsung"
    }
    dimensions {
      dimension: "deviceModel"
      string_value: "samsung/greatlte"
      value_label: "samsung greatlte (Galaxy Note8)"
    }
    dimensions {
      dimension: "deviceType"
      string_value: "TABLET"
      value_label: "Tablet"
    }
    metrics {
      metric: "___METRIC_TYPE___"
      decimal_value {
        value: "2"
      }
    }
  }
"""
    .trimIndent()

private val SAMPLE_VERSIONS =
  """
  rows {
    aggregation_period: FULL_RANGE
    start_time {
      year: 2023
      month: 5
      day: 3
      time_zone {
        id: "America/Los_Angeles"
      }
    }
    dimensions {
      dimension: "reportType"
      string_value: "CRASH"
    }
    dimensions {
      dimension: "versionCode"
      string_value: "5"
    }
    metrics {
      metric: "___METRIC_TYPE___"
      decimal_value {
        value: "5"
      }
    }
  }
  rows {
    aggregation_period: FULL_RANGE
    start_time {
      year: 2023
      month: 5
      day: 3
      time_zone {
        id: "America/Los_Angeles"
      }
    }
    dimensions {
      dimension: "reportType"
      string_value: "CRASH"
    }
    dimensions {
      dimension: "versionCode"
      string_value: "6"
    }
    metrics {
      metric: "___METRIC_TYPE___"
      decimal_value {
        value: "10"
      }
    }
  }
"""
    .trimIndent()

private const val METRIC_TYPE = "___METRIC_TYPE___"

/** A bare-bones test service for faking [VitalsErrorsServiceGrpc]. */
class FakeErrorsService(
  private val connection: VitalsConnection,
  private val database: FakeVitalsDatabase,
  private val clock: Clock,
  private val requestChannel: SendChannel<GeneratedMessageV3>? = null,
) : VitalsErrorsServiceGrpc.VitalsErrorsServiceImplBase() {
  override fun searchErrorReports(
    request: SearchErrorReportsRequest,
    responseObserver: StreamObserver<SearchErrorReportsResponse>,
  ) {
    requestChannel?.trySend(request)
    val regex = Regex(".*errorIssueId = (\\w+).*")
    val errorIssueId = regex.matchEntire(request.filter)!!.groupValues[1]
    responseObserver.onNext(
      SearchErrorReportsResponse.newBuilder()
        .apply {
          addErrorReports(
            ErrorReport.newBuilder().apply {
              database.getReportForIssue(errorIssueId)?.let { addErrorReports(it) }
            }
          )
        }
        .build()
    )
    responseObserver.onCompleted()
  }

  override fun searchErrorIssues(
    request: SearchErrorIssuesRequest,
    responseObserver: StreamObserver<SearchErrorIssuesResponse>,
  ) {
    requestChannel?.trySend(request)
    responseObserver.onNext(
      SearchErrorIssuesResponse.newBuilder()
        .apply { addAllErrorIssues(database.getIssues()) }
        .build()
    )
    responseObserver.onCompleted()
  }

  override fun getErrorCountMetricSet(
    request: GetErrorCountMetricSetRequest,
    responseObserver: StreamObserver<ErrorCountMetricSet>,
  ) {
    requestChannel?.trySend(request)
    responseObserver.onNext(
      ErrorCountMetricSet.newBuilder()
        .apply {
          name = "apps/${connection.appId}/errorCountMetricSet"
          freshnessInfo =
            FreshnessInfo.newBuilder()
              .apply {
                addFreshnesses(
                  FreshnessInfo.Freshness.newBuilder()
                    .apply {
                      aggregationPeriod = AggregationPeriod.FULL_RANGE
                      latestEndTime = clock.instant().toProtoDateTime(ZoneId.of("UTC"))
                    }
                    .build()
                )
              }
              .build()
        }
        .build()
    )
    responseObserver.onCompleted()
  }

  override fun queryErrorCountMetricSet(
    request: QueryErrorCountMetricSetRequest,
    responseObserver: StreamObserver<QueryErrorCountMetricSetResponse>,
  ) {
    requestChannel?.trySend(request)
    val responseText =
      if (request.dimensionsList.contains(DimensionType.API_LEVEL.value)) {
        SAMPLE_OS_LEVEL_DISTRIBUTION
      } else if (request.dimensionsList.contains(DimensionType.VERSION_CODE.value)) {
        SAMPLE_VERSIONS
      } else if (request.dimensionsList.contains(DimensionType.DEVICE_MODEL.value)) {
        SAMPLE_DEVICE_DISTRIBUTION
      } else ""
    val metricType = request.getMetrics(0)
    val response =
      TextFormat.parse(
        responseText.replace(METRIC_TYPE, metricType),
        QueryErrorCountMetricSetResponse::class.java,
      )

    responseObserver.onNext(response)
    responseObserver.onCompleted()
  }
}
