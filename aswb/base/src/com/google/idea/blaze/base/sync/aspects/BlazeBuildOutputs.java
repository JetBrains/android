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
import static com.google.common.collect.Iterables.getOnlyElement;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Sets;
import com.google.idea.blaze.base.command.buildresult.BuildResult;
import com.google.idea.blaze.base.command.buildresult.BuildResult.Status;
import com.google.idea.blaze.base.command.buildresult.bepparser.BepArtifactData;
import com.google.idea.blaze.base.command.buildresult.bepparser.ParsedBepOutput;
import com.google.idea.blaze.common.artifact.OutputArtifact;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The result of a Bazel build.
 */
public interface BlazeBuildOutputs {

  /**
   * A list of the artifacts outputted by the given target to the given output group.
   *
   * <p>Note that the same artifact may be outputted by multiple targets and into multiple output groups.
   */
  ImmutableList<OutputArtifact> getOutputGroupTargetArtifacts(String outputGroup, String label);

  /**
   * A de-duplicated list of the artifacts outputted to the given output group.
   *
   * <p>Note that the same artifact may be outputted by multiple targets and into multiple output groups. Such artifacts are included in the
   * resulting list only once.
   */
  ImmutableList<OutputArtifact> getOutputGroupArtifacts(String outputGroup);

  /**
   * Label of any targets that were not build because of build errors.
   */
  ImmutableSet<String> targetsWithErrors();

  /**
   * The final status of the Bazel build.
   */
  BuildResult buildResult();

  /**
   * An obscure ID that can be used to identify the build in the external environment.
   *
   * <p><em>DO NOT</em> attempt to interpret or compare.
   */
  String idForLogging();

  String buildId();

  /**
   * @return true if the build outputs are empty.
   */
  boolean isEmpty();

  /** The result of a (potentially sharded) blaze build.
   *
   *<p><em>NOTE</em>:The implementation supporting sharded builds is slow and memory hungry. It should only be used with the legacy sync.
   **/
  interface Legacy extends BlazeBuildOutputs {

    /**
     * {@link BepArtifactData} by {@link OutputArtifact#getBazelOutRelativePath()} for all artifacts from a
     * build.
     */
    ImmutableMap<String, BepArtifactData> artifacts();

    /**
     * The artifacts transitively associated with each top-level target.
     */
    ImmutableSetMultimap<String, OutputArtifact> perTargetArtifacts();

    ImmutableMap<String, BuildResult> buildShardResults();

    ImmutableList<OutputArtifact> getOutputGroupArtifactsLegacySyncOnly(
      Predicate<String> outputGroupFilter);

    Legacy updateOutputs(Legacy nextOutputs);

    ImmutableList<String> getBuildIds();

    long bepBytesConsumed();

    boolean allBuildsFailed();
  }


  static BlazeBuildOutputs noOutputs(BuildResult buildResult) {
    return new BlazeBuildOutputsImpl(
      buildResult, ImmutableMap.of(), ImmutableMap.of(), ImmutableSet.of(), 0L);
  }

  static BlazeBuildOutputs.Legacy noOutputsForLegacy(BuildResult buildResult) {
    return new BlazeBuildOutputsImpl(
      buildResult, ImmutableMap.of(), ImmutableMap.of(), ImmutableSet.of(), 0L);
  }

  @VisibleForTesting
  static BlazeBuildOutputs noOutputsForTesting(String buildId, BuildResult buildResult) {
    return new BlazeBuildOutputsImpl(
      buildResult,
      ImmutableMap.of(),
      ImmutableMap.of(buildId, buildResult),
      ImmutableSet.of(),
      0L);
  }


  static BlazeBuildOutputs fromParsedBepOutput(ParsedBepOutput parsedOutput) {
    return new BlazeBuildOutputs() {
      @Override
      public ImmutableList<OutputArtifact> getOutputGroupTargetArtifacts(String outputGroup, String label) {
        return parsedOutput.getOutputGroupTargetArtifacts(outputGroup, label);
      }

      @Override
      public ImmutableList<OutputArtifact> getOutputGroupArtifacts(String outputGroup) {
        return parsedOutput.getOutputGroupArtifacts(outputGroup);
      }

      @Override
      public ImmutableSet<String> targetsWithErrors() {
        return parsedOutput.targetsWithErrors();
      }

      @Override
      public BuildResult buildResult() {
        return BuildResult.fromExitCode(parsedOutput.buildResult());
      }

      @Override
      public String idForLogging() {
        return parsedOutput.idForLogging();
      }

      @Override
      public String buildId() {
        return "";
      }

      @Override
      public boolean isEmpty() {
        return false;
      }
    };
  }

  static BlazeBuildOutputs.Legacy fromParsedBepOutputForLegacy(
    ParsedBepOutput.Legacy parsedOutput) {
    final var result = BuildResult.fromExitCode(parsedOutput.getBuildResult());
    ImmutableMap<String, BuildResult> buildIdWithResult =
      parsedOutput.buildId != null
      ? ImmutableMap.of(parsedOutput.buildId, result)
      : ImmutableMap.of();
    return new BlazeBuildOutputsImpl(
      result,
      result.status == Status.FATAL_ERROR
      ? ImmutableMap.of()
      : parsedOutput.getFullArtifactData(),
      buildIdWithResult,
      parsedOutput.getTargetsWithErrors(),
      parsedOutput.getBepBytesConsumed());
  }

  /**
   * The result of a (potentially sharded) blaze build.
   */
  class BlazeBuildOutputsImpl implements BlazeBuildOutputs, Legacy {
    private final BuildResult buildResult;
    // Maps build id to the build result of individual shards
    private final ImmutableMap<String, BuildResult> buildShardResults;
    private final ImmutableSet<String> targetsWithErrors;
    public final long bepBytesConsumed;

    /**
     * {@link BepArtifactData} by {@link OutputArtifact#getBazelOutRelativePath()} for all artifacts from a
     * build.
     */
    private final ImmutableMap<String, BepArtifactData> artifacts;

    /**
     * The artifacts transitively associated with each top-level target.
     */
    private final ImmutableSetMultimap<String, OutputArtifact> perTargetArtifacts;

    private BlazeBuildOutputsImpl(
      BuildResult buildResult,
      Map<String, BepArtifactData> artifacts,
      ImmutableMap<String, BuildResult> buildShardResults,
      ImmutableSet<String> targetsWithErrors,
      long bepBytesConsumed) {
      this.buildResult = buildResult;
      this.artifacts = ImmutableMap.copyOf(artifacts);
      this.buildShardResults = buildShardResults;
      this.targetsWithErrors = targetsWithErrors;
      this.bepBytesConsumed = bepBytesConsumed;

      ImmutableSetMultimap.Builder<String, OutputArtifact> perTarget = ImmutableSetMultimap.builder();
      artifacts.values().forEach(a -> a.topLevelTargets.forEach(t -> perTarget.put(t, a.artifact)));
      this.perTargetArtifacts = perTarget.build();
    }

    /** Returns the output artifacts generated for target with given label. */
    @Override
    public ImmutableList<OutputArtifact> getOutputGroupTargetArtifacts(String outputGroup, String label) {
      // TODO: solodkyy - This is slow although it is invoked at most two times.
      return artifacts.values().stream()
        .filter(a -> a.outputGroups.contains(outputGroup) && a.topLevelTargets.contains(label))
        .map(a -> a.artifact)
        .collect(toImmutableList());
    }

    @VisibleForTesting
    @Override
    public ImmutableList<OutputArtifact> getOutputGroupArtifacts(String outputGroup) {
      // TODO: solodkyy - This is slow although it is invoked at most two times.
      return artifacts.values().stream()
          .filter(a -> a.outputGroups.contains(outputGroup))
          .map(a -> a.artifact)
          .collect(toImmutableList());
    }

    @Override
    public ImmutableMap<String, BepArtifactData> artifacts() {
      return artifacts;
    }

    @Override
    public ImmutableSetMultimap<String, OutputArtifact> perTargetArtifacts() {
      return perTargetArtifacts;
    }

    @Override
    public ImmutableMap<String, BuildResult> buildShardResults() {
      return buildShardResults;
    }

    @VisibleForTesting
    @Override
    public ImmutableList<OutputArtifact> getOutputGroupArtifactsLegacySyncOnly(
      Predicate<String> outputGroupFilter) {
      return artifacts.values().stream()
        .filter(a -> a.outputGroups.stream().anyMatch(outputGroupFilter))
        .map(a -> a.artifact)
        .collect(toImmutableList());
    }

    @Override
    public ImmutableSet<String> targetsWithErrors() {
      return targetsWithErrors;
    }

    /**
     * Merges this {@link BlazeBuildOutputs} with a newer set of outputs.
     */
    @Override
    public BlazeBuildOutputs.Legacy updateOutputs(BlazeBuildOutputs.Legacy nextOutputs) {

      // first combine common artifacts
      Map<String, BepArtifactData> combined = new LinkedHashMap<>(artifacts);
      for (Map.Entry<String, BepArtifactData> e : nextOutputs.artifacts().entrySet()) {
        BepArtifactData a = e.getValue();
        combined.compute(e.getKey(), (k, v) -> v == null ? a : v.update(a));
      }

      // then iterate over targets, throwing away old data for rebuilt targets and updating output
      // data accordingly
      for (String target : perTargetArtifacts.keySet()) {
        if (!nextOutputs.perTargetArtifacts().containsKey(target)) {
          continue;
        }
        Set<OutputArtifact> oldOutputs = perTargetArtifacts.get(target);
        Set<OutputArtifact> newOutputs = nextOutputs.perTargetArtifacts().get(target);

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
          }
          else {
            combined.put(old.getBazelOutRelativePath(), data);
          }
        }
      }
      return new BlazeBuildOutputsImpl(
        BuildResult.combine(buildResult(), nextOutputs.buildResult()),
        combined,
        Stream.concat(
            nextOutputs.buildShardResults().entrySet().stream(),
            buildShardResults.entrySet().stream())
          .collect(
            // On duplicate buildIds, preserve most recent result
            toImmutableMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1)),
        Sets.union(targetsWithErrors, nextOutputs.targetsWithErrors()).immutableCopy(),
        bepBytesConsumed + nextOutputs.bepBytesConsumed());
    }

    @Override
    public ImmutableList<String> getBuildIds() {
      return buildShardResults.keySet().asList();
    }

    @Override
    public long bepBytesConsumed() {
      return bepBytesConsumed;
    }

    /**
     * Returns true if all component builds had fatal errors.
     */
    @Override
    public boolean allBuildsFailed() {
      return !buildShardResults.isEmpty()
             && buildShardResults.values().stream()
               .allMatch(result -> result.status == Status.FATAL_ERROR);
    }

    @Override
    public BuildResult buildResult() {
      return buildResult;
    }

    @Override
    public String idForLogging() {
      return getBuildIds().stream().collect(Collectors.joining(","));
    }

    @Override
    public String buildId() {
      return getOnlyElement(getBuildIds());
    }

    @Override
    public boolean isEmpty() {
      return artifacts.isEmpty();
    }
  }
}
