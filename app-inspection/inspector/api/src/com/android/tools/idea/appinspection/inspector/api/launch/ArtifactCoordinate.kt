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

/**
 * Contains information that uniquely identifies the library.
 *
 * This normally refers to the maven/gradle coordinate of the artifact.
 */
data class ArtifactCoordinate(
  val groupId: String,
  val artifactId: String,
  val version: String,
  val type: Type
) {
  enum class Type {
    JAR {
      override fun toString() = "jar"
    },
    AAR {
      override fun toString() = "aar"
    }
  }
  /**
   * The coordinate for this library, i.e. how it would appear in a Gradle dependencies block.
   */
  override fun toString() = "${groupId}:${artifactId}:${version}"

  /**
   * Returns true if the artifacts are the same without checking the version.
   */
  fun sameArtifact(other: ArtifactCoordinate): Boolean =
    groupId == other.groupId && artifactId == other.artifactId

  fun toArtifactCoordinateProto(): AppInspection.ArtifactCoordinate = AppInspection.ArtifactCoordinate.newBuilder()
    .setGroupId(groupId)
    .setArtifactId(artifactId)
    .setVersion(version)
    .build()
}