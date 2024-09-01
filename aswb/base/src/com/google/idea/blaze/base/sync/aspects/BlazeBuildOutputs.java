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
package com.google.idea.blaze.base.sync.aspects;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Sets;
import com.google.idea.blaze.base.command.buildresult.BepArtifactData;
import com.google.idea.blaze.base.command.buildresult.ParsedBepOutput;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.sync.aspects.BuildResult.Status;
import com.google.idea.blaze.common.artifact.OutputArtifact;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

/** The result of a (potentially sharded) blaze build. */
public class BlazeBuildOutputs {

  public static BlazeBuildOutputs noOutputs(BuildResult buildResult) {
    return new BlazeBuildOutputs(
        buildResult, ImmutableMap.of(), ImmutableMap.of(), ImmutableSet.of(), 0L, Optional.empty());
  }

  @VisibleForTesting
  public static BlazeBuildOutputs noOutputs(String buildId, BuildResult buildResult) {
    return new BlazeBuildOutputs(
        buildResult,
        ImmutableMap.of(),
        ImmutableMap.of(buildId, buildResult),
        ImmutableSet.of(),
        0L,
        Optional.empty());
  }

  public static BlazeBuildOutputs fromParsedBepOutput(
      BuildResult result, ParsedBepOutput parsedOutput) {
    ImmutableMap<String, BuildResult> buildIdWithResult =
        parsedOutput.buildId != null
            ? ImmutableMap.of(parsedOutput.buildId, result)
            : ImmutableMap.of();
    return new BlazeBuildOutputs(
        result,
        result.status == Status.FATAL_ERROR
            ? ImmutableMap.of()
            : parsedOutput.getFullArtifactData(),
        buildIdWithResult,
        parsedOutput.getTargetsWithErrors(),
        parsedOutput.getBepBytesConsumed(),
        parsedOutput.getSourceUri());
  }

  public final BuildResult buildResult;
  // Maps build id to the build result of individual shards
  private final ImmutableMap<String, BuildResult> buildShardResults;
  private final ImmutableSet<Label> targetsWithErrors;
  public final long bepBytesConsumed;

  public final Optional<String> sourceUri;

  /**
   * {@link BepArtifactData} by {@link OutputArtifact#getBazelOutRelativePath()} for all artifacts from a
   * build.
   */
  public final ImmutableMap<String, BepArtifactData> artifacts;

  /** The artifacts transitively associated with each top-level target. */
  private final ImmutableSetMultimap<String, OutputArtifact> perTargetArtifacts;

  private BlazeBuildOutputs(
      BuildResult buildResult,
      Map<String, BepArtifactData> artifacts,
      ImmutableMap<String, BuildResult> buildShardResults,
      ImmutableSet<Label> targetsWithErrors,
      long bepBytesConsumed,
      Optional<String> sourceUri) {
    this.buildResult = buildResult;
    this.artifacts = ImmutableMap.copyOf(artifacts);
    this.buildShardResults = buildShardResults;
    this.targetsWithErrors = targetsWithErrors;
    this.bepBytesConsumed = bepBytesConsumed;
    this.sourceUri = sourceUri;

    ImmutableSetMultimap.Builder<String, OutputArtifact> perTarget = ImmutableSetMultimap.builder();
    artifacts.values().forEach(a -> a.topLevelTargets.forEach(t -> perTarget.put(t, a.artifact)));
    this.perTargetArtifacts = perTarget.build();
  }

  /** Returns the output artifacts generated for target with given label. */
  public ImmutableSet<OutputArtifact> artifactsForTarget(String label) {
    return perTargetArtifacts.get(label);
  }

  @VisibleForTesting
  public ImmutableList<OutputArtifact> getOutputGroupArtifacts(
      Predicate<String> outputGroupFilter) {
    return artifacts.values().stream()
        .filter(a -> a.outputGroups.stream().anyMatch(outputGroupFilter))
        .map(a -> a.artifact)
        .collect(toImmutableList());
  }

  public ImmutableSet<Label> getTargetsWithErrors() {
    return targetsWithErrors;
  }

  /** Merges this {@link BlazeBuildOutputs} with a newer set of outputs. */
  public BlazeBuildOutputs updateOutputs(BlazeBuildOutputs nextOutputs) {

    // first combine common artifacts
    Map<String, BepArtifactData> combined = new LinkedHashMap<>(artifacts);
    for (Map.Entry<String, BepArtifactData> e : nextOutputs.artifacts.entrySet()) {
      BepArtifactData a = e.getValue();
      combined.compute(e.getKey(), (k, v) -> v == null ? a : v.update(a));
    }

    // then iterate over targets, throwing away old data for rebuilt targets and updating output
    // data accordingly
    for (String target : perTargetArtifacts.keySet()) {
      if (!nextOutputs.perTargetArtifacts.containsKey(target)) {
        continue;
      }
      Set<OutputArtifact> oldOutputs = perTargetArtifacts.get(target);
      Set<OutputArtifact> newOutputs = nextOutputs.perTargetArtifacts.get(target);

      // remove out of date target associations
      for (OutputArtifact old : oldOutputs) {
        if (newOutputs.contains(old)) {
          continue;
        }
        // no longer output by this target; need to update target associations
        BepArtifactData data = combined.get(old.getBazelOutRelativePath());
        if (data != null) {
          data = data.removeTargetAssociation(target);
        }
        if (data == null) {
          combined.remove(old.getBazelOutRelativePath());
        } else {
          combined.put(old.getBazelOutRelativePath(), data);
        }
      }
    }
    return new BlazeBuildOutputs(
        BuildResult.combine(buildResult, nextOutputs.buildResult),
        combined,
        Stream.concat(
                nextOutputs.buildShardResults.entrySet().stream(),
                buildShardResults.entrySet().stream())
            .collect(
                // On duplicate buildIds, preserve most recent result
                toImmutableMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1)),
        Sets.union(targetsWithErrors, nextOutputs.targetsWithErrors).immutableCopy(),
        bepBytesConsumed + nextOutputs.bepBytesConsumed,
        sourceUri);
  }

  public ImmutableList<String> getBuildIds() {
    return buildShardResults.keySet().asList();
  }

  /** Returns true if all component builds had fatal errors. */
  public boolean allBuildsFailed() {
    return !buildShardResults.isEmpty()
        && buildShardResults.values().stream()
            .allMatch(result -> result.status == Status.FATAL_ERROR);
  }
}
