/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.qsync.deps

import com.google.common.base.Preconditions
import com.google.errorprone.annotations.CanIgnoreReturnValue
import com.google.idea.blaze.qsync.artifacts.BuildArtifact
import com.google.idea.blaze.qsync.project.ProjectPath
import com.google.idea.blaze.qsync.project.ProjectProto
import com.google.idea.blaze.qsync.project.ProjectProto.ArtifactDirectoryContents
import com.google.idea.blaze.qsync.project.ProjectProto.ProjectArtifact
import com.google.idea.blaze.qsync.project.ProjectProto.ProjectArtifact.ArtifactTransform
import java.nio.file.Path
import java.util.Optional

/** Populates a [ProjectProto.ArtifactDirectoryContents] proto from build artifacts.  */
class ArtifactDirectoryBuilder(val path: Path) {
  data class Entry(
    val destination: Path,
    val source: BuildArtifact,
    val fromBuild: DependencyBuildContext,
    val transform: ArtifactTransform,
  ) {
    fun toProto(): ProjectArtifact {
      return ProjectArtifact.newBuilder()
        .setTarget(source.target().toString())
        .setBuildArtifact(
          ProjectProto.BuildArtifact.newBuilder().setDigest(source.digest()).build()
        )
        .setTransform(transform)
        .build()
    }

    companion object {
      fun create(
        destination: Path,
        source: BuildArtifact,
        fromBuild: DependencyBuildContext,
        transform: ArtifactTransform
      ): Entry = Entry(destination, source, fromBuild, transform)
    }
  }

  private val contents: MutableMap<Path, Entry> = hashMapOf()

  /**
   * @param relativePath Project directory relative path of the destination directory.
   */
  init {
    Preconditions.checkState(
      !path.isAbsolute, "Expected a relative path: %s", path
    )
  }

  /** Returns the project directory relative path of the destination directory.  */
  fun path(): Path = path

  /** Returns this directories root as a project path.  */
  fun root(): ProjectPath {
    return ProjectPath.projectRelative(path())
  }

  /**
   * Adds a new artifact to the directory if it is not already present, or was produced by a more
   * recent build that an existing artifact at the same location.
   *
   * @param relativePath Path to place the artifact at, relative to [.root].
   * @param source The artifact to put there.
   * @param fromBuild The build that produced the artifact.
   * @return The path to the final artifact, if it was added.
   */
  @CanIgnoreReturnValue
  @JvmOverloads
  fun addIfNewer(
    relativePath: Path,
    source: BuildArtifact,
    fromBuild: DependencyBuildContext,
    transform: ArtifactTransform = ArtifactTransform.COPY
  ): Optional<ProjectPath> {
    val existing = contents.get(relativePath)
    if (existing != null && existing.fromBuild.startTime().isAfter(fromBuild.startTime())) {
      // we already have the same artifact from a more recent build.
      return Optional.empty()
    }
    contents[relativePath] = Entry(relativePath, source, fromBuild, transform)
    return Optional.of(ProjectPath.projectRelative(path.resolve(relativePath)))
  }

  val isEmpty: Boolean
    get() = contents.isEmpty()

  fun addToArtifactDirectories(artifactDirectoriesBuilder: ProjectProto.ArtifactDirectories.Builder) {
    val artifactDirectoryContentsBuilder =
      artifactDirectoriesBuilder.getDirectoriesOrDefault(path.toString(), ArtifactDirectoryContents.getDefaultInstance()).toBuilder()
    artifactDirectoriesBuilder.putDirectories(
      path.toString(),
      artifactDirectoryContentsBuilder
        .putAllContents(contents.values.associate { it.destination.toString() to it.toProto() })
        .build()
    )
  }
}
