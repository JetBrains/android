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

  fun toWild(): ArtifactCoordinate = WildArtifactCoordinate(this)

  fun toAny(): ArtifactCoordinate = AnyArtifactCoordinate(this)

  private enum class Module(val groupId: String, val artifactId: String) {
    COMPOSE_UI("androidx.compose.ui", "ui"),
    COMPOSE_UI_ANDROID("androidx.compose.ui", "ui-android"),
    WORK_RUNTIME("androidx.work", "work-runtime"),
  }

  enum class MinimumArtifactCoordinate(module: Module, override val version: String) :
    ArtifactCoordinate {
    COMPOSE_UI(Module.COMPOSE_UI, "1.0.0-beta02"),
    COMPOSE_UI_ANDROID(Module.COMPOSE_UI_ANDROID, "1.5.0-beta01"),
    WORK_RUNTIME(Module.WORK_RUNTIME, "2.5.0"),
    ;

    override val groupId = module.groupId
    override val artifactId = module.artifactId

    override fun toString() = toCoordinateString()
  }

  class RunningArtifactCoordinate(minimum: ArtifactCoordinate, override val version: String) :
    ArtifactCoordinate {
    override val groupId = minimum.groupId
    override val artifactId = minimum.artifactId

    override fun toString() = toCoordinateString()
  }

  private class WildArtifactCoordinate(private val coordinate: ArtifactCoordinate) :
    ArtifactCoordinate by coordinate {
    override val version = "+"
  }

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
