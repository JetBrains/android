/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.filecache;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.command.buildresult.LocalFileArtifact;
import com.google.idea.blaze.base.io.FileAttributeScanner;
import com.google.idea.blaze.base.prefetch.FetchExecutor;
import com.google.idea.blaze.common.artifact.ArtifactState;
import com.google.idea.blaze.common.artifact.OutputArtifactInfo;
import com.google.idea.blaze.common.artifact.OutputArtifactWithoutDigest;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;

/**
 * A data class representing the diff between two sets of output artifacts.
 *
 * <p>We serialize the last modified time for local files to avoid recomputing it when calculating
 * the diff.
 */
@AutoValue
public abstract class ArtifactsDiff {

  public abstract ImmutableMap<String, ArtifactState> getNewState();

  public abstract ImmutableList<OutputArtifactWithoutDigest> getUpdatedOutputs();

  public abstract ImmutableSet<ArtifactState> getRemovedOutputs();

  public static ArtifactsDiff diffArtifacts(
      @Nullable ImmutableMap<String, ArtifactState> oldState,
      Collection<? extends OutputArtifactWithoutDigest> newArtifacts)
      throws InterruptedException, ExecutionException {
    return diffArtifacts(
        oldState,
        newArtifacts.stream().collect(toImmutableMap(OutputArtifactInfo::getRelativePath, a -> a)));
  }

  public static ArtifactsDiff diffArtifacts(
      @Nullable ImmutableMap<String, ArtifactState> oldState,
      ImmutableMap<String, OutputArtifactWithoutDigest> newArtifacts)
      throws InterruptedException, ExecutionException {
    ImmutableMap<String, ArtifactState> newState = computeState(newArtifacts.values());
    // Find new/updated
    final ImmutableMap<String, ArtifactState> previous =
        oldState != null ? oldState : ImmutableMap.of();
    ImmutableList<OutputArtifactWithoutDigest> updated =
        newState.entrySet().stream()
            .filter(
                e -> {
                  ArtifactState old = previous.get(e.getKey());
                  return old == null || old.isMoreRecent(e.getValue());
                })
            .map(e -> newArtifacts.get(e.getKey()))
            .collect(toImmutableList());

    // Find removed
    Set<ArtifactState> removed = new HashSet<>(previous.values());
    newState.forEach((k, v) -> removed.remove(v));

    return new AutoValue_ArtifactsDiff(newState, updated, ImmutableSet.copyOf(removed));
  }

  private static ImmutableMap<String, ArtifactState> computeState(
      Collection<OutputArtifactWithoutDigest> artifacts)
      throws InterruptedException, ExecutionException {
    boolean hasLocalFiles = artifacts.stream().anyMatch(a -> a instanceof LocalFileArtifact);
    if (!hasLocalFiles) {
      return artifacts.stream()
          .collect(
              toImmutableMap(
                  OutputArtifactInfo::getRelativePath,
                  OutputArtifactWithoutDigest::toArtifactState));
    }
    // for local files, diffing requires checking the timestamps, which we multi-thread
    return FileAttributeScanner.readAttributes(artifacts, TO_ARTIFACT_STATE, FetchExecutor.EXECUTOR)
        .entrySet()
        .stream()
        .collect(toImmutableMap(e -> e.getKey().getRelativePath(), Map.Entry::getValue));
  }

  private static FileAttributeScanner.AttributeReader<OutputArtifactWithoutDigest, ArtifactState>
      TO_ARTIFACT_STATE =
          new FileAttributeScanner.AttributeReader<OutputArtifactWithoutDigest, ArtifactState>() {
            @Nullable
            @Override
            public ArtifactState getAttribute(OutputArtifactWithoutDigest file) {
              return file.toArtifactState();
            }

            @Override
            public boolean isValid(ArtifactState attribute) {
              return true;
            }
          };
}
