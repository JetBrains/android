/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.appinspection.inspector.api.launch

import com.android.tools.app.inspection.AppInspection
import org.jetbrains.annotations.TestOnly

/**
 * Contains information that uniquely identifies the library.
 *
 * This normally refers to the maven/gradle coordinate of the artifact.
 *
 * [version] can optionally be '+' to match any version of the artifact, when used in the context of
 * compatibility checks.
 */
interface ArtifactCoordinate {
  val groupId: String
  val artifactId: String
  val version: String

  fun sameArtifact(other: ArtifactCoordinate) =
    groupId == other.groupId && artifactId == other.artifactId

  fun toArtifactCoordinateProto(): AppInspection.ArtifactCoordinate =
    AppInspection.ArtifactCoordinate.newBuilder()
      .setGroupId(groupId)
      .setArtifactId(artifactId)
      .setVersion(version)
      .build()

  fun toCoordinateString() = "${groupId}:${artifactId}:${version}"

  fun toAny(): ArtifactCoordinate = AnyArtifactCoordinate(this)

  private class AnyArtifactCoordinate(private val coordinate: ArtifactCoordinate) :
    ArtifactCoordinate by coordinate {
    override val version = "0.0.0"
  }

  companion object {
    @TestOnly
    operator fun invoke(groupId: String, artifactId: String, version: String) =
      object : ArtifactCoordinate {
        override val groupId = groupId
        override val artifactId = artifactId
        override val version = version

        override fun toString() = toCoordinateString()
      }
  }
}
