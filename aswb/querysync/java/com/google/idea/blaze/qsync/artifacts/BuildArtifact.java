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
package com.google.idea.blaze.qsync.artifacts;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Streams;
import com.google.idea.blaze.common.Interners;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.qsync.java.artifacts.AspectProto.OutputArtifact;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * An artifact produced by a build.
 *
 * <p>This includes the digest of the artifact as indicated by bazel, its output path and the target
 * that produced it.
 */
@AutoValue
public abstract class BuildArtifact {

  /** Bazel generated digets of this artifacts contents. */
  public abstract String digest();

  /** Output path of the artifact. */
  public abstract Path artifactPath();

  /** Label of the target that built the artifact. */
  public abstract Label target();

  /** Metadata that has been extracted from the artifact by the IDE. */
  public abstract ImmutableMap<Class<? extends ArtifactMetadata>, ? extends ArtifactMetadata>
      metadata();

  public <T extends ArtifactMetadata> Optional<T> getMetadata(Class<T> ofType) {
    return Optional.ofNullable((T) metadata().get(ofType));
  }

  public BuildArtifact withMetadata(Iterable<ArtifactMetadata> metadata) {
    if (Iterables.isEmpty(metadata)) {
      return this;
    }
    return create(
        digest(),
        artifactPath(),
        target(),
        ImmutableMap.copyOf(Maps.uniqueIndex(metadata, ArtifactMetadata::getClass)));
  }

  @VisibleForTesting
  public BuildArtifact withMetadata(ArtifactMetadata... metadata) {
    return withMetadata(ImmutableList.copyOf(metadata));
  }

  public static BuildArtifact create(String digest, Path artifactPath, Label target) {
    return create(digest, artifactPath, target, ImmutableMap.of());
  }

  public static BuildArtifact create(
      String digest,
      Path artifactPath,
      Label target,
      ImmutableMap<Class<? extends ArtifactMetadata>, ? extends ArtifactMetadata> metadata) {
    return new AutoValue_BuildArtifact(digest, artifactPath, target, metadata);
  }

  public String getExtension() {
    String fileName = artifactPath().getFileName().toString();
    int lastDot = fileName.lastIndexOf('.');
    if (lastDot == -1) {
      return "";
    }
    return fileName.substring(lastDot + 1);
  }

  public static ImmutableList<BuildArtifact> fromProtos(
      List<OutputArtifact> paths, DigestMap digestMap, Label target) {
    return paths.stream()
        .map(p -> createFromProto(p, digestMap, target))
        .flatMap(Collection::stream)
        .collect(toImmutableList());
  }

  private static Collection<BuildArtifact> createFromProto(
      OutputArtifact artifact, DigestMap digestMap, Label target) {
    return switch (artifact.getPathCase()) {
      case DIRECTORY ->
          Streams.stream(digestMap.directoryContents(Interners.pathOf(artifact.getDirectory())))
              .map(p -> digestMap.createBuildArtifact(p, target))
              .flatMap(Optional::stream)
              .collect(toImmutableSet());
      case FILE ->
          digestMap
              .createBuildArtifact(Interners.pathOf(artifact.getFile()), target)
              .map(Collections::singleton)
              .orElse(Collections.emptySet());
      case PATH_NOT_SET -> Collections.emptySet();
    };
  }

  public static ImmutableList<BuildArtifact> addMetadata(
      Iterable<BuildArtifact> existing,
      ImmutableSetMultimap<BuildArtifact, ArtifactMetadata> toAdd) {
    return Streams.stream(existing)
        .map(a -> a.withMetadata(toAdd.get(a)))
        .collect(toImmutableList());
  }
}
