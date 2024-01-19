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
import com.google.play.developer.reporting.Track
import com.intellij.openapi.diagnostic.Logger

private val LOG = Logger.getInstance("vitals.datamodel.Verion")

internal fun PlayTrack.Companion.fromProto(proto: Track): PlayTrack? {
  return when (proto.type) {
    "Production" -> PlayTrack.PRODUCTION
    "Internal" -> PlayTrack.INTERNAL
    "Open testing" -> PlayTrack.OPEN_TESTING
    "Closed testing" -> PlayTrack.CLOSED_TESTING
    else -> {
      LOG.warn("${proto.type} is not of a supported Play Track type.")
      null
    }
  }
}

internal fun List<Track>.extract(): List<Version> {
  val flattened = flatMap { track ->
    val trackType = PlayTrack.fromProto(track) ?: return@flatMap emptyList()

    track.servingReleasesList.flatMap { release ->
      release.versionCodesList.map { versionCode -> versionCode to trackType }
    }
  }

  return flattened
    .groupBy { it.first }
    .map { (versionCode, tracks) ->
      Version(
        buildVersion = versionCode.toString(),
        displayVersion = "",
        displayName = "",
        tracks = tracks.map { it.second }.toSet(),
      )
    }
}

fun Version.Companion.fromDimensions(dimensions: List<Dimension>): Version {
  return dimensions
    .filter { it.type == DimensionType.VERSION_CODE }
    .map { dimension ->
      val versionCode =
        when (
          dimension.value
        ) { // TODO: either we just pass string around or we know it's of a long type. ?
          is DimensionValue.LongValue -> dimension.value.value.toString()
          is DimensionValue.StringValue -> dimension.value.value
        }

      // It's likely to get empty string and since we might use this in our UI, we fall back
      // to version code here for now.
      val displayValue = dimension.displayValue.takeUnless { it.isEmpty() } ?: versionCode

      Version(
        buildVersion = versionCode,
        displayVersion = displayValue,
        displayName = displayValue,
        tracks = emptySet(),
      )
    }
    .single()
}
