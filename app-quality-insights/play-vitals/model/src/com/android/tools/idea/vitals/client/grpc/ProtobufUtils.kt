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

import com.android.tools.idea.insights.client.Interval
import com.android.tools.idea.vitals.datamodel.TimeGranularity
import com.google.play.developer.reporting.DateTime
import com.google.play.developer.reporting.DateTimeInterval
import com.google.play.developer.reporting.TimeZone
import java.time.Instant
import java.time.ZoneId

/** Returns [DateTime] by the given [Instant], [zoneId]. */
internal fun Instant.toProtoDateTime(zoneId: ZoneId): DateTime {
  val zonedDateTime = atZone(zoneId)
  val timeZoneBuilder = TimeZone.newBuilder().apply { id = zoneId.id }

  return DateTime.newBuilder()
    .apply {
      year = zonedDateTime.year
      month = zonedDateTime.monthValue
      day = zonedDateTime.dayOfMonth
      hours = zonedDateTime.hour
      minutes = zonedDateTime.minute
      seconds = zonedDateTime.second
      nanos = zonedDateTime.nano
      timeZone = timeZoneBuilder.build()
    }
    .build()
}

internal fun Interval.toProtoDateTime(timeGranularity: TimeGranularity): DateTimeInterval {
  val interval = this
  val zoneId = timeGranularity.inferZoneId()

  return DateTimeInterval.newBuilder()
    .apply {
      startTime = interval.startTime.toProtoDateTime(zoneId).truncate(timeGranularity)
      endTime = interval.endTime.toProtoDateTime(zoneId).truncate(timeGranularity)
    }
    .build()
}

/** Returns truncated [DateTime] by the given time granularity. */
internal fun DateTime.truncate(timeGranularity: TimeGranularity): DateTime {
  val original = this
  val dateTimeBuilder = DateTime.newBuilder(original)

  when (timeGranularity) {
    TimeGranularity.PER_NANO -> {
      return original
    }
    TimeGranularity.PER_SECOND -> {
      dateTimeBuilder.apply { nanos = 0 }
    }
    TimeGranularity.PER_MINUTE -> {
      dateTimeBuilder.apply {
        seconds = 0
        nanos = 0
      }
    }
    TimeGranularity.HOURLY -> {
      dateTimeBuilder.apply {
        minutes = 0
        seconds = 0
        nanos = 0
      }
    }
    TimeGranularity.DAILY -> {
      dateTimeBuilder.apply {
        hours = 0
        minutes = 0
        seconds = 0
        nanos = 0
      }
    }
    TimeGranularity.FULL_RANGE -> {
      dateTimeBuilder.apply {
        minutes = 0
        seconds = 0
        nanos = 0
      }
    }
  }

  return dateTimeBuilder.build()
}

/**
 * Returns [ZoneId] based on the time granularity.
 *
 * The reason we do the hard mapping is we have a very strict validator on the server side:
 * ```
 *   private static final ImmutableList<String> HOURLY_DEFAULT_TIME_ZONE_IDS = ImmutableList.of("UTC");
 *   private static final ImmutableList<String> DAILY_DEFAULT_TIME_ZONE_IDS = ImmutableList.of("America/Los_Angeles");
 * ```
 *
 * (https://source.corp.google.com/piper///depot/google3/java/com/google/play/console/reportingapi/v1main/domain/common/TimeRangeValidator.java;l=40;rcl=523007627)
 *
 * In order to avoid unpleasant errors when querying, we can only respect the restrictions.
 */
internal fun TimeGranularity.inferZoneId(): ZoneId {
  return when (this) {
    TimeGranularity.DAILY -> ZoneId.of("America/Los_Angeles")
    TimeGranularity.HOURLY -> ZoneId.of("UTC")
    else -> throw IllegalStateException("$this is not supported.")
  }
}
