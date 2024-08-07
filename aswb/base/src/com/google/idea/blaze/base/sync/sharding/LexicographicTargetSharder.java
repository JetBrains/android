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
package com.google.idea.blaze.base.sync.sharding;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.idea.blaze.base.sync.sharding.ShardedTargetList.remoteConcurrentSyncs;
import static java.lang.Math.min;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import com.google.idea.blaze.base.bazel.BuildSystem.SyncStrategy;
import com.google.idea.blaze.base.logging.utils.ShardStats.ShardingApproach;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.common.experiments.BoolExperiment;
import com.google.idea.common.experiments.IntExperiment;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * A simple target batcher splitting based on the target strings. This will tend to split by
 * package, so is better than random batching.
 */
public class LexicographicTargetSharder implements BuildBatchingService {
  // The maximum number of targets per shard for remote builds to avoid potential OOM
  @VisibleForTesting
  static final IntExperiment maximumRemoteShardSize =
      new IntExperiment("lexicographic.sharder.maximum.remote.shard.size", 1000);

  // The minimum number of targets per shard for remote builds. If the user explicitly
  // sets a smaller target_shard_size, the user-specified value takes priority.
  @VisibleForTesting
  static final IntExperiment minimumRemoteShardSize =
      new IntExperiment("lexicographic.sharder.minimum.remote.shard.size", 500);

  // The minimum targets size requirement to use all idle workers. Splitting targets does not help
  // to reduce build time when their target size is too small. So set a threshold to avoid
  // over-split.
  @VisibleForTesting
  static final IntExperiment parallelThreshold =
      new IntExperiment("lexicographic.sharder.parallel.threshold", 1000);

  // If true, ensures that builds with remote sharding use at least LEGACY_CONCURRENT_SHARD_COUNT
  // concurrent shards. This ensures that remote builds do not change for mid-sized projects from
  // before there was an explicit minimum value for targets per remote shard.
  // Ignored if LEGACY_CONCURRENT_SHARD_COUNT is greater than the total number of concurrent build
  // shards.
  @VisibleForTesting
  static final BoolExperiment useLegacySharding =
      new BoolExperiment("lexicographic.sharder.use.legacy.shard.split", true);

  private static final int LEGACY_CONCURRENT_SHARD_COUNT = 10;

  @Override
  public ImmutableList<ImmutableList<Label>> calculateTargetBatches(
      Set<Label> targets, SyncStrategy syncStrategy, int suggestedShardSize) {
    List<Label> sorted = ImmutableList.sortedCopyOf(Comparator.comparing(Label::toString), targets);
    // When LexicographicTargetSharder is used for remote build, we may decide optimized shard size
    // for users even they have provided shard_size in project view. The size is decided according
    // to three aspects:
    // 1. take advantage of parallelization
    // 2. size specified by users
    // 3. avoid potential OOM (maximumRemoteShardSize)
    // We collect suggested size from these aspects and use the minimum one finally.
    // If user enable shard sync manually without remote build, LexicographicTargetSharder
    // will still be used. But use suggestedShardSize without further calculation since there's
    // only one worker in that case.

    // TODO(b/218800878) Perhaps we should treat PARALLEL and DECIDE_AUTOMATICALLY differently here?
    if (syncStrategy != SyncStrategy.SERIAL) {
      suggestedShardSize =
          computeParallelShardSize(
              targets.size(),
              parallelThreshold.getValue(),
              remoteConcurrentSyncs.getValue(),
              minimumRemoteShardSize.getValue(),
              maximumRemoteShardSize.getValue(),
              suggestedShardSize);
    }
    return Lists.partition(sorted, suggestedShardSize).stream()
        .map(ImmutableList::copyOf)
        .collect(toImmutableList());
  }

  /**
   * Calculates the number of targets to run on a single build shard along.
   *
   * <p>If the number of targets to shard meets the threshold for sharding, then the value is
   *
   * <p>clamp(min targets per shard, (# targets) / (# shards running in parallel), max targets per
   * shard)
   *
   * <p>If {@link LexicographicTargetSharder#useLegacySharding} is enabled, projects surpassing the
   * splitting threshold are split across at least {@link
   * LexicographicTargetSharder#LEGACY_CONCURRENT_SHARD_COUNT} shards, even if the individual shard
   * size is smaller than {@link LexicographicTargetSharder#minimumRemoteShardSize}.
   *
   * @param numTargets The number of targets to split into shards.
   * @param parallelThreshold The minimum number of targets required for splitting a build into
   *     shards. {@link LexicographicTargetSharder#parallelThreshold}
   * @param numConcurrentShards The maximum number of shards that can run in parallel. {@link
   *     ShardedTargetList#remoteConcurrentSyncs}
   * @param min The minimum number of targets per remote build shard. {@link
   *     LexicographicTargetSharder#minimumRemoteShardSize}
   * @param max The maximum number of targets per remote build shard. {@link
   *     LexicographicTargetSharder#maximumRemoteShardSize}
   * @param suggested The target_shard_size, if specified in the blazeproject file. Otherwise {@link
   *     BlazeBuildTargetSharder#defaultTargetShardSize} Takes precedence if smaller than min.
   * @return The number of targets per build shard.
   */
  @VisibleForTesting
  static int computeParallelShardSize(
      int numTargets,
      int parallelThreshold,
      int numConcurrentShards,
      int min,
      int max,
      int suggested) {
    if (numTargets < parallelThreshold) {
      return suggested;
    }
    int targetsPerShard;
    if (useLegacySharding.getValue()
        && LEGACY_CONCURRENT_SHARD_COUNT <= numConcurrentShards
        && numTargets < min * LEGACY_CONCURRENT_SHARD_COUNT) {
      targetsPerShard = (int) Math.ceil((double) numTargets / LEGACY_CONCURRENT_SHARD_COUNT);
    } else {
      targetsPerShard = (int) Math.ceil((double) numTargets / numConcurrentShards);
      targetsPerShard = Ints.constrainToRange(targetsPerShard, min, max);
    }
    return min(suggested, targetsPerShard);
  }

  @Override
  public ShardingApproach getShardingApproach() {
    return ShardingApproach.LEXICOGRAPHIC_TARGET_SHARDER;
  }
}
