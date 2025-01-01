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
package com.google.idea.blaze.base.command.buildresult.bepparser;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.stream.Collectors.groupingBy;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.idea.blaze.common.artifact.OutputArtifact;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.jetbrains.annotations.TestOnly;

/** A data class representing blaze's build event protocol (BEP) output for a build. */
public interface ParsedBepOutput {
  /**
   * The exit code of the Bazel build.
   */
  int buildResult();

  /**
   * The total number of bytes in the build event protocol output.
   */
  long bepBytesConsumed();

  /**
   * An obscure ID that can be used to identify the build in the external environment.
   *
   * <p><em>DO NOT</em> attempt to interpret or compare.
   */
  String idForLogging();

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
   * A de-duplicated list of all artifacts produced by the build.
   */
  @TestOnly
  ImmutableList<OutputArtifact> getAllOutputArtifactsForTesting();

  class Legacy {

    @VisibleForTesting
    public static final ParsedBepOutput.Legacy EMPTY =
      new ParsedBepOutput.Legacy(
        "build-id",
        ImmutableMap.of(),
        0,
        0,
        0,
        ImmutableSet.of());

    @Nullable public final String buildId;

    /**
     * A map from file set ID to file set, with the same ordering as the BEP stream.
     */
    @VisibleForTesting
    public final ImmutableMap<String, FileSet> fileSets;

    final long syncStartTimeMillis;

    private final int buildResult;
    private final long bepBytesConsumed;
    private final ImmutableSet<String> targetsWithErrors;

    Legacy(
      @Nullable String buildId,
      ImmutableMap<String, FileSet> fileSets,
      long syncStartTimeMillis,
      int buildResult,
      long bepBytesConsumed,
      ImmutableSet<String> targetsWithErrors) {
      this.buildId = buildId;
      this.fileSets = fileSets;
      this.syncStartTimeMillis = syncStartTimeMillis;
      this.buildResult = buildResult;
      this.bepBytesConsumed = bepBytesConsumed;
      this.targetsWithErrors = targetsWithErrors;
    }

    /**
     * Returns the build result.
     */
    public int getBuildResult() {
      return buildResult;
    }

    public long getBepBytesConsumed() {
      return bepBytesConsumed;
    }

    /**
     * Returns all output artifacts of the build.
     */
    @TestOnly
    public ImmutableSet<OutputArtifact> getAllOutputArtifactsForTesting() {
      return fileSets.values().stream()
        .map(s -> s.parsedOutputs)
        .flatMap(List::stream)
        .collect(toImmutableSet());
    }

    /**
     * Returns a map from artifact key to {@link BepArtifactData} for all artifacts reported during
     * the build.
     */
    public ImmutableMap<String, BepArtifactData> getFullArtifactData() {
      return ImmutableMap.copyOf(
        Maps.transformValues(
          fileSets.values().stream()
            .flatMap(FileSet::toPerArtifactData)
            .collect(groupingBy(d -> d.artifact.getBazelOutRelativePath(), toImmutableSet())),
          BepArtifactData::combine));
    }

    /**
     * Returns the set of build targets that had an error.
     */
    public ImmutableSet<String> getTargetsWithErrors() {
      return targetsWithErrors;
    }

    public static class FileSet {
      @VisibleForTesting
      public final ImmutableList<OutputArtifact> parsedOutputs;
      @VisibleForTesting
      public final ImmutableSet<String> outputGroups;
      @VisibleForTesting
      public final ImmutableSet<String> targets;

      FileSet(
        ImmutableList<OutputArtifact> parsedOutputs,
        Set<String> outputGroups,
        Set<String> targets) {
        this.parsedOutputs = parsedOutputs;
        this.outputGroups = ImmutableSet.copyOf(outputGroups);
        this.targets = ImmutableSet.copyOf(targets);
      }

      private Stream<BepArtifactData> toPerArtifactData() {
        return parsedOutputs.stream().map(a -> new BepArtifactData(a, outputGroups, targets));
      }
    }
  }
}
