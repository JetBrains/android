/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync.sharding;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.lang.Math.min;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker;
import com.google.idea.blaze.base.bazel.BuildSystem.SyncStrategy;
import com.google.idea.blaze.base.logging.utils.ShardStats;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WildcardTargetPattern;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.sections.ShardBlazeBuildsSection;
import com.google.idea.blaze.base.projectview.section.sections.TargetShardSizeSection;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.output.StatusOutput;
import com.google.idea.blaze.base.scope.scopes.TimingScope;
import com.google.idea.blaze.base.scope.scopes.TimingScope.EventType;
import com.google.idea.blaze.base.sync.aspects.BuildResult;
import com.google.idea.blaze.base.sync.projectview.TargetExpressionList;
import com.google.idea.blaze.base.sync.sharding.WildcardTargetExpander.ExpandedTargetsResult;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.common.experiments.IntExperiment;
import com.intellij.openapi.project.Project;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Utility methods for sharding blaze build invocations. */
public class BlazeBuildTargetSharder {

  /**
   * Max number of individual targets per blaze build shard. Can be overridden by the user for local
   * syncs.
   */
  private static final IntExperiment maxTargetShardSize =
      new IntExperiment("blaze.max.target.shard.size", 10000);

  /**
   * Default target shard size when sharding is requested but no shard size is specified. Purpose is
   * to avoid OOMEs.
   */
  private static final IntExperiment defaultTargetShardSize =
      new IntExperiment("blaze.default.target.shard.size", 1000);

  // number of packages per blaze query shard
  static final int PACKAGE_SHARD_SIZE = 500;

  /** Result of expanding then sharding wildcard target patterns */
  public static class ShardedTargetsResult {
    public final ShardedTargetList shardedTargets;
    public final BuildResult buildResult;

    private ShardedTargetsResult(ShardedTargetList shardedTargets, BuildResult buildResult) {
      this.shardedTargets = shardedTargets;
      this.buildResult = buildResult;
    }
  }

  /** Returns true if sharding is requested via the project view file. */
  static boolean shardingRequested(Project project) {
    ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
    return projectViewSet != null && shardingRequested(projectViewSet);
  }

  private static boolean shardingRequested(ProjectViewSet projectViewSet) {
    return projectViewSet.getScalarValue(ShardBlazeBuildsSection.KEY).orElse(false);
  }

  /** Number of individual targets per blaze build shard. */
  private static int getTargetShardSize(ProjectViewSet projectViewSet) {
    int defaultLimit =
        shardingRequested(projectViewSet)
            ? defaultTargetShardSize.getValue()
            : maxTargetShardSize.getValue();
    int userSpecified =
        projectViewSet.getScalarValue(TargetShardSizeSection.KEY).orElse(defaultLimit);
    return min(userSpecified, TargetShardSizeLimit.getMaxTargetsPerShard().orElse(userSpecified));
  }

  private enum ShardingApproach {
    EXPAND_AND_SHARD, // first expand wildcard targets, then split into batches
    SHARD_WITHOUT_EXPANDING, // split unexpanded wildcard targets into batches
  }

  private static ShardingApproach getShardingApproach(
      SyncStrategy parallelStrategy, ProjectViewSet viewSet) {
    if (shardingRequested(viewSet)) {
      return ShardingApproach.EXPAND_AND_SHARD;
    }
    if (parallelStrategy == SyncStrategy.SERIAL) {
      return ShardingApproach.SHARD_WITHOUT_EXPANDING;
    }
    return ShardingApproach.EXPAND_AND_SHARD;
  }

  /** Expand wildcard target patterns and partition the resulting target list. */
  public static ShardedTargetsResult expandAndShardTargets(
      Project project,
      BlazeContext context,
      ProjectViewSet viewSet,
      WorkspacePathResolver pathResolver,
      List<TargetExpression> targets,
      BuildInvoker queryInvoker,
      SyncStrategy parallelStrategy) {
    ShardingApproach approach = getShardingApproach(parallelStrategy, viewSet);
    switch (approach) {
      case SHARD_WITHOUT_EXPANDING:
        int suggestedSize = getTargetShardSize(viewSet);
        return new ShardedTargetsResult(
            new ShardedTargetList(
                shardTargetsRetainingOrdering(targets, suggestedSize),
                ShardStats.ShardingApproach.PARTITION_WITHOUT_EXPANDING,
                suggestedSize),
            BuildResult.SUCCESS);
      case EXPAND_AND_SHARD:
        ExpandedTargetsResult expandedTargets =
            expandWildcardTargets(project, context, queryInvoker, viewSet, pathResolver, targets);
        if (expandedTargets.buildResult.status == BuildResult.Status.FATAL_ERROR) {
          return new ShardedTargetsResult(
              new ShardedTargetList(ImmutableList.of(), ShardStats.ShardingApproach.ERROR, 0),
              expandedTargets.buildResult);
        }

        return new ShardedTargetsResult(
            shardSingleTargets(
                expandedTargets.singleTargets, parallelStrategy, getTargetShardSize(viewSet)),
            expandedTargets.buildResult);
      default:
        throw new IllegalStateException("Unhandled sharding approach: " + approach);
    }
  }

  /** Expand wildcard target patterns into individual blaze targets. */
  private static ExpandedTargetsResult expandWildcardTargets(
      Project project,
      BlazeContext parentContext,
      BuildInvoker buildInvoker,
      ProjectViewSet projectViewSet,
      WorkspacePathResolver pathResolver,
      List<TargetExpression> targets) {
    return Scope.push(
        parentContext,
        context -> {
          context.push(new TimingScope("ShardSyncTargets", EventType.Other));
          context.output(new StatusOutput("Sharding: expanding wildcard target patterns..."));
          context.setPropagatesErrors(false);
          return doExpandWildcardTargets(
              project, context, buildInvoker, projectViewSet, pathResolver, targets);
        });
  }

  private static ExpandedTargetsResult doExpandWildcardTargets(
      Project project,
      BlazeContext context,
      BuildInvoker buildBinary,
      ProjectViewSet projectViewSet,
      WorkspacePathResolver pathResolver,
      List<TargetExpression> targets) {
    List<WildcardTargetPattern> includes = getWildcardPatterns(targets);
    if (includes.isEmpty()) {
      return new ExpandedTargetsResult(targets, BuildResult.SUCCESS);
    }
    Map<TargetExpression, List<TargetExpression>> expandedTargets =
        WildcardTargetExpander.expandToNonRecursiveWildcardTargets(
            project, context, pathResolver, includes);
    if (expandedTargets == null) {
      return new ExpandedTargetsResult(ImmutableList.of(), BuildResult.FATAL_ERROR);
    }

    // replace original recursive targets with expanded list, retaining relative ordering
    List<TargetExpression> fullList = new ArrayList<>();
    for (TargetExpression target : targets) {
      List<TargetExpression> expanded = expandedTargets.get(target);
      if (expanded == null) {
        fullList.add(target);
      } else {
        fullList.addAll(expanded);
      }
    }
    ExpandedTargetsResult result =
        WildcardTargetExpander.expandToSingleTargets(
            project, context, buildBinary, projectViewSet, fullList);

    // finally add back any explicitly-specified, unexcluded single targets which may have been
    // removed by the query (for example, because they have the 'manual' tag)
    TargetExpressionList helper = TargetExpressionList.create(targets);
    List<TargetExpression> singleTargets =
        targets.stream()
            .filter(t -> !t.isExcluded())
            .filter(t -> !isWildcardPattern(t))
            .filter(t -> t instanceof Label)
            .filter(t -> helper.includesTarget((Label) t))
            .collect(toImmutableList());
    return ExpandedTargetsResult.merge(
        result, new ExpandedTargetsResult(singleTargets, result.buildResult));
  }

  /**
   * Shards a list of individual blaze targets (with no wildcard expressions other than for excluded
   * target patterns).
   */
  @VisibleForTesting
  static ShardedTargetList shardSingleTargets(
      List<TargetExpression> targets, SyncStrategy syncStrategy, int shardSize) {
    return BuildBatchingService.batchTargets(
        canonicalizeSingleTargets(targets), syncStrategy, shardSize);
  }

  /**
   * Given an ordered list of individual blaze targets (with no wildcard expressions other than for
   * excluded target patterns), removes duplicates and excluded targets, returning an unordered set.
   */
  private static ImmutableSet<Label> canonicalizeSingleTargets(List<TargetExpression> targets) {
    return filterExcludedTargets(targets).stream()
        .filter(t -> !t.isExcluded())
        .filter(t -> t instanceof Label)
        .map(t -> (Label) t)
        .collect(toImmutableSet());
  }

  /**
   * Partition targets list. Because order is important with respect to excluded targets, original
   * relative ordering is retained, and each shard has all subsequent excluded targets appended to
   * it.
   */
  static ImmutableList<ImmutableList<TargetExpression>> shardTargetsRetainingOrdering(
      List<TargetExpression> targets, int shardSize) {
    targets = filterExcludedTargets(targets);
    if (targets.size() <= shardSize) {
      return ImmutableList.of(ImmutableList.copyOf(targets));
    }
    List<ImmutableList<TargetExpression>> output = new ArrayList<>();
    for (int index = 0; index < targets.size(); index += shardSize) {
      int endIndex = min(targets.size(), index + shardSize);
      List<TargetExpression> shard = new ArrayList<>(targets.subList(index, endIndex));
      if (shard.stream().filter(TargetExpression::isExcluded).count() == shard.size()) {
        continue;
      }
      List<TargetExpression> remainingExcludes =
          targets.subList(endIndex, targets.size()).stream()
              .filter(TargetExpression::isExcluded)
              .collect(Collectors.toList());
      shard.addAll(remainingExcludes);
      output.add(ImmutableList.copyOf(shard));
    }
    return ImmutableList.copyOf(output);
  }

  /**
   * Removes any trivially-excluded targets from an ordered list of target expressions. Handles
   * included and excluded wildcard target patterns.
   */
  private static ImmutableList<TargetExpression> filterExcludedTargets(
      List<TargetExpression> targets) {
    return TargetExpressionList.create(targets).getTargets();
  }

  /** Returns the wildcard target patterns, ignoring exclude patterns (those starting with '-') */
  private static List<WildcardTargetPattern> getWildcardPatterns(List<TargetExpression> targets) {
    return targets.stream()
        .filter(t -> !t.isExcluded())
        .map(WildcardTargetPattern::fromExpression)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  private static boolean isWildcardPattern(TargetExpression expr) {
    return WildcardTargetPattern.fromExpression(expr) != null;
  }

  private BlazeBuildTargetSharder() {}
}
