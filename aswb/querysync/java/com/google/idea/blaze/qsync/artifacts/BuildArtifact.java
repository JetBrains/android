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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.google.common.util.concurrent.Uninterruptibles;
import com.google.idea.blaze.common.Interners;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.artifact.BuildArtifactCache;
import com.google.idea.blaze.common.artifact.CachedArtifact;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.java.artifacts.AspectProto.OutputArtifact;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

/**
 * An artifact produced by a build.
 *
 * <p>This includes the digest of the artifact as indicated by bazel, its output path and the target
 * that produced it.
 */
@AutoValue
public abstract class BuildArtifact {

  public abstract String digest();

  public abstract Path path();

  public abstract Label target();

  public static BuildArtifact create(String digest, Path path, Label target) {
    return new AutoValue_BuildArtifact(digest, path, target);
  }

  public CachedArtifact blockingGetFrom(BuildArtifactCache cache) throws BuildException {
    try {
      return Uninterruptibles.getUninterruptibly(
          cache
              .get(digest())
              .orElseThrow(() -> new BuildException("Artifact missing from the cache: " + this)));
    } catch (ExecutionException e) {
      throw new BuildException("Failed to get artifact " + this, e);
    }
  }

  public String getExtension() {
    String fileName = path().getFileName().toString();
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
}
