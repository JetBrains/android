/*
 * Copyright 2021 The Bazel Authors. All rights reserved.
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.blaze.base.sync.sharding.LexicographicTargetSharder.computeParallelShardSize;
import static com.google.idea.blaze.base.sync.sharding.LexicographicTargetSharder.maximumRemoteShardSize;
import static com.google.idea.blaze.base.sync.sharding.LexicographicTargetSharder.minimumRemoteShardSize;
import static com.google.idea.blaze.base.sync.sharding.LexicographicTargetSharder.parallelThreshold;
import static com.google.idea.blaze.base.sync.sharding.LexicographicTargetSharder.useLegacySharding;
import static com.google.idea.blaze.base.sync.sharding.ShardedTargetList.remoteConcurrentSyncs;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.bazel.BuildSystem.SyncStrategy;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link LexicographicTargetSharder}. */
@RunWith(JUnit4.class)
public class LexicographicTargetSharderTest extends BlazeTestCase {

  private static final LexicographicTargetSharder lexicographicTargetSharder =
      new LexicographicTargetSharder();
  private static final Label LABEL_ONE = Label.create("//java/com/google:one");
  private static final Label LABEL_TWO = Label.create("//java/com/google:two");
  private static final Label LABEL_THREE = Label.create("//java/com/google:three");
  private static final Label LABEL_FOUR = Label.create("//java/com/google:four");
  private final MockExperimentService mockExperimentService = new MockExperimentService();

  @Override
  protected void initTest(Container applicationServices, Container projectServices) {
    applicationServices.register(ExperimentService.class, mockExperimentService);
  }

  private void setParallelThreshold(int value) {
    mockExperimentService.setExperimentInt(parallelThreshold, value);
  }

  private void setRemoteConcurrentSyncs(int value) {
    mockExperimentService.setExperimentInt(remoteConcurrentSyncs, value);
  }

  private void setMaximumRemoteShardSize(int value) {
    mockExperimentService.setExperimentInt(maximumRemoteShardSize, value);
  }

  private void setMinimumRemoteShardSize(int value) {
    mockExperimentService.setExperimentInt(minimumRemoteShardSize, value);
  }

  private void setLegacySharding(boolean value) {
    mockExperimentService.setExperiment(useLegacySharding, value);
  }

  @Before
  public void setUp() {
    setMaximumRemoteShardSize(1000);
    setMinimumRemoteShardSize(1);
    setLegacySharding(false);
  }

  @Test
  public void calculateTargetBatches_testLocalBuildType_suggestedSizeIsUsed() {
    setParallelThreshold(1000);
    setRemoteConcurrentSyncs(10);
    Set<Label> targets = ImmutableSet.of(LABEL_ONE, LABEL_TWO, LABEL_THREE, LABEL_FOUR);
    ImmutableList<ImmutableList<Label>> shardedTargets =
        lexicographicTargetSharder.calculateTargetBatches(targets, SyncStrategy.SERIAL, 2);
    assertThat(shardedTargets).hasSize(2);
    assertThat(shardedTargets.get(0)).containsExactly(LABEL_FOUR, LABEL_ONE).inOrder();
    assertThat(shardedTargets.get(1)).containsExactly(LABEL_THREE, LABEL_TWO).inOrder();
  }

  @Test
  public void
      calculateTargetBatches_testRemoteBuildTypeAndSuggestedSizeIsSmaller_suggestedSizeIsUsed() {
    Set<Label> targets = ImmutableSet.of(LABEL_ONE, LABEL_TWO, LABEL_THREE, LABEL_FOUR);
    setParallelThreshold(4);
    setRemoteConcurrentSyncs(1);
    ImmutableList<ImmutableList<Label>> shardedTargets =
        lexicographicTargetSharder.calculateTargetBatches(targets, SyncStrategy.PARALLEL, 2);
    assertThat(shardedTargets).hasSize(2);
    assertThat(shardedTargets.get(0)).containsExactly(LABEL_FOUR, LABEL_ONE).inOrder();
    assertThat(shardedTargets.get(1)).containsExactly(LABEL_THREE, LABEL_TWO).inOrder();
  }

  @Test
  public void
      calculateTargetBatches_testRemoteBuildTypeAndCalculatedSizeIsSmaller_calculatedSizeIsUsed() {
    Set<Label> targets = ImmutableSet.of(LABEL_ONE, LABEL_TWO, LABEL_THREE, LABEL_FOUR);
    setParallelThreshold(4);
    setRemoteConcurrentSyncs(10);
    ImmutableList<ImmutableList<Label>> shardedTargets =
        lexicographicTargetSharder.calculateTargetBatches(targets, SyncStrategy.PARALLEL, 2);
    assertThat(shardedTargets).hasSize(4);
    assertThat(shardedTargets.get(0)).containsExactly(LABEL_FOUR);
    assertThat(shardedTargets.get(1)).containsExactly(LABEL_ONE);
    assertThat(shardedTargets.get(2)).containsExactly(LABEL_THREE);
    assertThat(shardedTargets.get(3)).containsExactly(LABEL_TWO);
  }

  @Test
  public void
      calculateTargetBatches_testRemoteBuildTypeAndMaximumRemoteShardSizeIsSmaller_maximumRemoteShardSizeIsUsed() {
    Set<Label> targets = ImmutableSet.of(LABEL_ONE, LABEL_TWO, LABEL_THREE, LABEL_FOUR);
    setParallelThreshold(4);
    setRemoteConcurrentSyncs(1);
    setMaximumRemoteShardSize(3);
    ImmutableList<ImmutableList<Label>> shardedTargets =
        lexicographicTargetSharder.calculateTargetBatches(targets, SyncStrategy.PARALLEL, 100);
    assertThat(shardedTargets).hasSize(2);
    assertThat(shardedTargets.get(0)).containsExactly(LABEL_FOUR, LABEL_ONE, LABEL_THREE).inOrder();
    assertThat(shardedTargets.get(1)).containsExactly(LABEL_TWO);
  }

  @Test
  public void
      calculateTargetBatches_testRemoteBuildTypeAndMinimumRemoteShardSizeIsLargerThanCalculated_minimumRemoteShardSizeIsUsed() {
    ImmutableSet<Label> targets = ImmutableSet.of(LABEL_ONE, LABEL_TWO, LABEL_THREE, LABEL_FOUR);
    setParallelThreshold(4);
    setRemoteConcurrentSyncs(10);
    setMinimumRemoteShardSize(2);
    ImmutableList<ImmutableList<Label>> shardedTargets =
        lexicographicTargetSharder.calculateTargetBatches(targets, SyncStrategy.PARALLEL, 100);
    assertThat(shardedTargets).hasSize(2);
    assertThat(shardedTargets.get(0)).containsExactly(LABEL_FOUR, LABEL_ONE).inOrder();
    assertThat(shardedTargets.get(1)).containsExactly(LABEL_THREE, LABEL_TWO).inOrder();
  }

  @Test
  public void computeParallelShardSize_legacyShardingEnabled() {
    setLegacySharding(true);

    // Minimum # of targets for splitting
    int sMin = 1000;
    // # shards running in parallel
    int nShards = 100;
    // Minimum # targets per shard
    int min = 500;
    // Maximum # targets per shard
    int max = 1000;
    // Suggested # targets per shard
    int sug = 2000;

    // nTargets less than splitting threshold sMin (1000) defaults to suggested size
    assertThat(computeParallelShardSize(900, sMin, nShards, min, max, sug)).isEqualTo(sug);

    // nTargets between splitting threshold sMin (1000) and min (500) *
    // LEGACY_CONCURRENT_SHARD_COUNT (10)
    // splits targets across LEGACY_CONCURRENT_SHARD_COUNT(10) shards.
    assertThat(computeParallelShardSize(1000, sMin, nShards, min, max, sug)).isEqualTo(100);
    assertThat(computeParallelShardSize(3000, sMin, nShards, min, max, sug)).isEqualTo(300);

    // After this point, shard size is min (500) until
    // nTargets > min (500) * nShards (100)
    assertThat(computeParallelShardSize(5000, sMin, nShards, min, max, sug)).isEqualTo(min);
    assertThat(computeParallelShardSize(6000, sMin, nShards, min, max, sug)).isEqualTo(min);
    assertThat(computeParallelShardSize(50000, sMin, nShards, min, max, sug)).isEqualTo(min);

    // Then shard size grows until max (1000) threshold reached
    assertThat(computeParallelShardSize(60000, sMin, nShards, min, max, sug)).isEqualTo(600);
    assertThat(computeParallelShardSize(100000, sMin, nShards, min, max, sug)).isEqualTo(1000);
    assertThat(computeParallelShardSize(110000, sMin, nShards, min, max, sug)).isEqualTo(max);
  }

  @Test
  public void computeParallelShardSize_legacyShardingDisabled() {
    setLegacySharding(false);
    // Minimum # of targets for splitting build
    int sMin = 1000;
    // # shards running in parallel
    int nShards = 100;
    // Minimum # targets per shard
    int min = 500;
    // Maximum # targets per shard
    int max = 1000;
    // Suggested # targets per shard
    int sug = 2000;

    // nTargets less than splitting threshold sMin (1000) defaults to suggested size
    assertThat(computeParallelShardSize(900, sMin, nShards, min, max, sug)).isEqualTo(sug);

    // Shard size remains min (500) until nTargets > min (500) *
    // nShards (100), at which point it scales up until max (1000) threshold
    assertThat(computeParallelShardSize(1000, sMin, nShards, min, max, sug)).isEqualTo(min);
    assertThat(computeParallelShardSize(3000, sMin, nShards, min, max, sug)).isEqualTo(min);
    assertThat(computeParallelShardSize(5000, sMin, nShards, min, max, sug)).isEqualTo(min);
    assertThat(computeParallelShardSize(6000, sMin, nShards, min, max, sug)).isEqualTo(min);
    assertThat(computeParallelShardSize(50000, sMin, nShards, min, max, sug)).isEqualTo(min);
    assertThat(computeParallelShardSize(60000, sMin, nShards, min, max, sug)).isEqualTo(600);
    assertThat(computeParallelShardSize(100000, sMin, nShards, min, max, sug)).isEqualTo(1000);
    assertThat(computeParallelShardSize(110000, sMin, nShards, min, max, sug)).isEqualTo(max);
  }

  @Test
  public void computeParallelShardSize_userSpecifiedSizeTakesPriorityIfSmallerThanMinimum() {
    int numTargets = 100000;
    int numConcurrentShards = 100;
    int minTargetsPerShard = 500;
    int maxTargetsPerShard = 1000;
    int suggestedTargetsPerShard = 20;
    int parallelThreshold = 1000;

    setLegacySharding(false);
    assertThat(
            computeParallelShardSize(
                numTargets,
                parallelThreshold,
                numConcurrentShards,
                minTargetsPerShard,
                maxTargetsPerShard,
                suggestedTargetsPerShard))
        .isEqualTo(suggestedTargetsPerShard);

    setLegacySharding(true);
    assertThat(
            computeParallelShardSize(
                numTargets,
                parallelThreshold,
                numConcurrentShards,
                minTargetsPerShard,
                maxTargetsPerShard,
                suggestedTargetsPerShard))
        .isEqualTo(suggestedTargetsPerShard);
  }
}
