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
package com.google.idea.blaze.qsync.deps;

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.qsync.artifacts.BuildArtifact;
import com.google.idea.blaze.qsync.project.ProjectPath;
import com.google.idea.blaze.qsync.project.ProjectProto;
import com.google.idea.blaze.qsync.project.ProjectProto.ArtifactDirectoryContents;
import com.google.idea.blaze.qsync.project.ProjectProto.ProjectArtifact.ArtifactTransform;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/** Populates a {@link ProjectProto.ArtifactDirectoryContents} proto from build artifacts. */
public class ArtifactDirectoryBuilder {

  @AutoValue
  abstract static class Entry {

    abstract String destination();

    abstract BuildArtifact source();

    abstract DependencyBuildContext fromBuild();

    abstract ArtifactTransform transform();

    static Entry create(
        Path destination,
        BuildArtifact source,
        DependencyBuildContext fromBuild,
        ArtifactTransform transform) {
      return new AutoValue_ArtifactDirectoryBuilder_Entry(
          destination.toString(), source, fromBuild, transform);
    }

    ProjectProto.ProjectArtifact toProto() {
      return ProjectProto.ProjectArtifact.newBuilder()
          .setTarget(source().target().toString())
          .setBuildArtifact(
              ProjectProto.BuildArtifact.newBuilder().setDigest(source().digest()).build())
          .setTransform(transform())
          .build();
    }
  }

  private final Path path;
  private final Map<Path, Entry> contents = Maps.newHashMap();

  /**
   * @param relativePath Project directory relative path of the destination directory.
   */
  public ArtifactDirectoryBuilder(Path relativePath) {
    Preconditions.checkState(
        !relativePath.isAbsolute(), "Expected a relative path: %s", relativePath);
    this.path = relativePath;
  }

  /** Returns the project directory relative path of the destination directory. */
  public Path path() {
    return path;
  }

  /** Returns this directories root as a project path. */
  public ProjectPath root() {
    return ProjectPath.projectRelative(path());
  }

  /**
   * Adds a new artifact to the directory if it is not already present, or was produced by a more
   * recent build that an existing artifact at the same location.
   *
   * @param relativePath Path to place the artifact at, relative to {@link #root()}.
   * @param source The artifact to put there.
   * @param fromBuild The build that produced the artifact.
   * @return The path to the final artifact, if it was added.
   */
  @CanIgnoreReturnValue
  public Optional<ProjectPath> addIfNewer(
      Path relativePath, BuildArtifact source, DependencyBuildContext fromBuild) {
    return addIfNewer(relativePath, source, fromBuild, ArtifactTransform.COPY);
  }

  @CanIgnoreReturnValue
  public Optional<ProjectPath> addIfNewer(
      Path relativePath,
      BuildArtifact source,
      DependencyBuildContext fromBuild,
      ArtifactTransform transform) {
    Entry existing = contents.get(relativePath);
    if (existing != null && existing.fromBuild().startTime().isAfter(fromBuild.startTime())) {
      // we already have the same artifact from a more recent build.
      return Optional.empty();
    }
    Entry e = Entry.create(relativePath, source, fromBuild, transform);
    contents.put(relativePath, e);
    return Optional.of(ProjectPath.projectRelative(path.resolve(relativePath)));
  }

  public boolean isEmpty() {
    return contents.isEmpty();
  }

  private ProjectProto.ArtifactDirectoryContents addToProto(
      ProjectProto.ArtifactDirectoryContents existing) {
    return existing.toBuilder()
        .putAllContents(
            contents.values().stream().collect(toImmutableMap(Entry::destination, Entry::toProto)))
        .build();
  }

  public void addTo(ProjectProto.ArtifactDirectories.Builder directories) {
    directories.putDirectories(
        path.toString(),
        addToProto(
            directories.getDirectoriesOrDefault(
                path.toString(), ArtifactDirectoryContents.getDefaultInstance())));
  }
}
