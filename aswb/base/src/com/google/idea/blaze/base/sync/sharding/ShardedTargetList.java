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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.logging.utils.ShardStats;
import com.google.idea.blaze.base.logging.utils.ShardStats.ShardingApproach;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.common.experiments.IntExperiment;
import java.util.List;

/** Partitioned list of blaze targets. */
public class ShardedTargetList {

  /** Number of concurrent syncs which can be run in parallel remotely. */
  public static final IntExperiment remoteConcurrentSyncs =
      new IntExperiment("number.concurrent.remote.syncs.2", 10);

  @VisibleForTesting
  final ImmutableList<? extends ImmutableList<? extends TargetExpression>> shardedTargets;

  final ShardStats shardStats;

  public ShardedTargetList(
      ImmutableList<? extends ImmutableList<? extends TargetExpression>> shardedTargets,
      ShardingApproach shardingApproach,
      int suggestedSize) {
    this.shardedTargets = shardedTargets;
    this.shardStats =
        ShardStats.create(
            suggestedSize,
            shardedTargets.stream().map(List::size).collect(toImmutableList()),
            shardingApproach);
  }

  public boolean isEmpty() {
    return shardedTargets.stream().flatMap(List::stream).findFirst().orElse(null) == null;
  }

  public int shardCount() {
    return shardedTargets.size();
  }

  public int getTotalTargets() {
    return shardedTargets.stream().mapToInt(List::size).sum();
  }

  public ShardStats shardStats() {
    return shardStats;
  }
}
