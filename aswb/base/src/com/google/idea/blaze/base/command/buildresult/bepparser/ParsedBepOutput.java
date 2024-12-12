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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.stream.Collectors.groupingBy;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.SetMultimap;
import com.google.idea.blaze.common.artifact.OutputArtifact;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/** A data class representing blaze's build event protocol (BEP) output for a build. */
public final class ParsedBepOutput {

  @VisibleForTesting
  public static final ParsedBepOutput EMPTY =
      new ParsedBepOutput(
          "build-id",
          null,
          ImmutableMap.of(),
          ImmutableSetMultimap.of(),
          0,
          0,
          0,
          ImmutableSet.of());

  @Nullable public final String buildId;

  /** A path to the local execroot */
  @Nullable private final String localExecRoot;

  /** A map from file set ID to file set, with the same ordering as the BEP stream. */
  private final ImmutableMap<String, FileSet> fileSets;

  /** The set of named file sets directly produced by each target. */
  private final SetMultimap<String, String> targetFileSets;

  final long syncStartTimeMillis;

  private final int buildResult;
  private final long bepBytesConsumed;
  private final ImmutableSet<String> targetsWithErrors;

  ParsedBepOutput(
    @Nullable String buildId,
    @Nullable String localExecRoot,
    ImmutableMap<String, FileSet> fileSets,
    ImmutableSetMultimap<String, String> targetFileSets,
    long syncStartTimeMillis,
    int buildResult,
    long bepBytesConsumed,
    ImmutableSet<String> targetsWithErrors) {
    this.buildId = buildId;
    this.localExecRoot = localExecRoot;
    this.fileSets = fileSets;
    this.targetFileSets = targetFileSets;
    this.syncStartTimeMillis = syncStartTimeMillis;
    this.buildResult = buildResult;
    this.bepBytesConsumed = bepBytesConsumed;
    this.targetsWithErrors = targetsWithErrors;
  }

  /** Returns the local execroot. */
  @Nullable
  public String getLocalExecRoot() {
    return localExecRoot;
  }

  /** Returns the build result. */
  public int getBuildResult() {
    return buildResult;
  }

  public long getBepBytesConsumed() {
    return bepBytesConsumed;
  }

  /** Returns all output artifacts of the build. */
  public ImmutableSet<OutputArtifact> getAllOutputArtifacts(Predicate<String> pathFilter) {
    return fileSets.values().stream()
        .map(s -> s.parsedOutputs)
        .flatMap(List::stream)
        .filter(o -> pathFilter.test(o.getBazelOutRelativePath()))
        .collect(toImmutableSet());
  }

  /** Returns the set of artifacts directly produced by the given target. */
  public ImmutableSet<OutputArtifact> getDirectArtifactsForTarget(
      String label, Predicate<String> pathFilter) {
    return targetFileSets.get(label).stream()
        .map(s -> fileSets.get(s).parsedOutputs)
        .flatMap(List::stream)
        .filter(o -> pathFilter.test(o.getBazelOutRelativePath()))
        .collect(toImmutableSet());
  }

  public ImmutableList<OutputArtifact> getOutputGroupArtifacts(
      String outputGroup, Predicate<String> pathFilter) {
    return fileSets.values().stream()
        .filter(f -> f.outputGroups.contains(outputGroup))
        .map(f -> f.parsedOutputs)
        .flatMap(List::stream)
        .filter(o -> pathFilter.test(o.getBazelOutRelativePath()))
        .distinct()
        .collect(toImmutableList());
  }

  public ImmutableList<OutputArtifact> getOutputGroupArtifacts(String outputGroup) {
    return getOutputGroupArtifacts(outputGroup, s -> true);
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

  /** Returns the set of build targets that had an error. */
  public ImmutableSet<String> getTargetsWithErrors() {
    return targetsWithErrors;
  }

  static class FileSet {
    private final ImmutableList<OutputArtifact> parsedOutputs;
    private final ImmutableSet<String> outputGroups;
    private final ImmutableSet<String> targets;

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
