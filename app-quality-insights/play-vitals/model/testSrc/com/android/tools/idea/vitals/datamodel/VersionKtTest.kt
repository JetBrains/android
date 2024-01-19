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

import com.android.tools.idea.insights.PlayTrack
import com.android.tools.idea.insights.Version
import com.google.common.truth.Truth.assertThat
import com.google.play.developer.reporting.Release
import com.google.play.developer.reporting.Track
import org.junit.Test

class VersionKtTest {
  @Test
  fun checkUnrecognizedTrack() {
    val tracks = listOf(TRACK_UNKNOWN, TRACK_PRODUCTION)

    val versions = tracks.extract()
    assertThat(versions)
      .containsExactly(Version(buildVersion = "1", tracks = setOf(PlayTrack.PRODUCTION)))
  }

  @Test
  fun checkExtractedVersions() {
    val tracks = listOf(TRACK_PRODUCTION, TRACK_INTERNAL, TRACK_OPEN_TESTING, TRACK_CLOSED_TESTING)

    val versions = tracks.extract()
    assertThat(versions)
      .containsExactly(
        Version(buildVersion = "1", tracks = setOf(PlayTrack.PRODUCTION, PlayTrack.INTERNAL)),
        Version(buildVersion = "2", tracks = setOf(PlayTrack.INTERNAL, PlayTrack.OPEN_TESTING)),
        Version(buildVersion = "3", tracks = setOf(PlayTrack.INTERNAL, PlayTrack.CLOSED_TESTING)),
        Version(buildVersion = "4", tracks = setOf(PlayTrack.INTERNAL, PlayTrack.CLOSED_TESTING)),
      )
  }
}

private val RELEASE_1 =
  Release.newBuilder()
    .apply {
      displayName = "1 (1.0)"
      addAllVersionCodes(listOf(1L))
    }
    .build()

private val RELEASE_2 =
  Release.newBuilder()
    .apply {
      displayName = "2 (2.0)"
      addAllVersionCodes(listOf(2L))
    }
    .build()

private val RELEASE_3and4 =
  Release.newBuilder()
    .apply {
      displayName = "3 and 4"
      addAllVersionCodes(listOf(3L, 4L))
    }
    .build()

private val TRACK_PRODUCTION =
  Track.newBuilder()
    .apply {
      displayName = "production"
      type = "Production"
      addAllServingReleases(listOf(RELEASE_1))
    }
    .build()

private val TRACK_INTERNAL =
  Track.newBuilder()
    .apply {
      displayName = "internal"
      type = "Internal"
      addAllServingReleases(listOf(RELEASE_1, RELEASE_2, RELEASE_3and4))
    }
    .build()

private val TRACK_CLOSED_TESTING =
  Track.newBuilder()
    .apply {
      displayName = "closed testing"
      type = "Closed testing"
      addAllServingReleases(listOf(RELEASE_3and4))
    }
    .build()

private val TRACK_OPEN_TESTING =
  Track.newBuilder()
    .apply {
      displayName = "open testing"
      type = "Open testing"
      addAllServingReleases(listOf(RELEASE_2))
    }
    .build()

private val TRACK_UNKNOWN =
  Track.newBuilder()
    .apply {
      displayName = "new track type"
      type = "New track type"
      addAllServingReleases(listOf(RELEASE_1))
    }
    .build()
