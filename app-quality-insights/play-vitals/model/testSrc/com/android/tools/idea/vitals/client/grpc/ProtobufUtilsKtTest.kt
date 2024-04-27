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

import com.android.tools.idea.insights.FakeTimeProvider
import com.android.tools.idea.vitals.datamodel.TimeGranularity
import com.google.common.truth.Truth.assertThat
import com.google.play.developer.reporting.DateTime
import com.google.play.developer.reporting.TimeZone
import java.time.ZoneId
import org.junit.Test

class ProtobufUtilsKtTest {
  @Test
  fun `check instant to proto conversion`() {
    val utcNow = FakeTimeProvider.now
    val utcZoneId = ZoneId.of("UTC")

    assertThat(utcNow.toProtoDateTime(utcZoneId).toString().trim())
      .isEqualTo(
        """
          year: 2022
          month: 6
          day: 8
          hours: 10
          time_zone {
            id: "UTC"
          }
        """
          .trimIndent()
      )
  }

  @Test
  fun `check proto date time can be truncated based on time granularity`() {
    val proto =
      DateTime.newBuilder()
        .apply {
          year = 2023
          month = 4
          day = 12
          hours = 8
          minutes = 10
          seconds = 20
          nanos = 10
          timeZone = TimeZone.newBuilder().apply { id = "America/Los_Angeles" }.build()
        }
        .build()

    assertThat(proto.truncate(TimeGranularity.HOURLY).toString().trim())
      .isEqualTo(
        """
            year: 2023
            month: 4
            day: 12
            hours: 8
            time_zone {
              id: "America/Los_Angeles"
            }
        """
          .trimIndent()
      )

    assertThat(proto.truncate(TimeGranularity.DAILY).toString().trim())
      .isEqualTo(
        """
            year: 2023
            month: 4
            day: 12
            time_zone {
              id: "America/Los_Angeles"
            }
        """
          .trimIndent()
      )

    assertThat(proto.truncate(TimeGranularity.FULL_RANGE).toString().trim())
      .isEqualTo(
        """
            year: 2023
            month: 4
            day: 12
            hours: 8
            time_zone {
              id: "America/Los_Angeles"
            }
        """
          .trimIndent()
      )
  }
}
