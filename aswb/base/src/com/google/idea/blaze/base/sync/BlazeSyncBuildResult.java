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
package com.google.idea.blaze.base.sync;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.logging.utils.BuildPhaseSyncStats;
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs;
import javax.annotation.Nullable;

/**
 * All the information gathered during the build phase of sync, used as input to the project update
 * phase.
 */
@AutoValue
public abstract class BlazeSyncBuildResult {

  /**
   * Merges this {@link BlazeSyncBuildResult} with the results of a more recent build (w.r.t build
   * start time), prior to the project update phase.
   */
  public BlazeSyncBuildResult updateResult(BlazeSyncBuildResult nextResult) {
    return nextResult.toBuilder()
        .setBuildResult(getBuildResult().updateOutputs(nextResult.getBuildResult()))
        .setBuildPhaseStats(
            ImmutableList.<BuildPhaseSyncStats>builder()
                .addAll(getBuildPhaseStats())
                .addAll(nextResult.getBuildPhaseStats())
                .build())
        .build();
  }

  /** Returns true if at least one build shard does not have fatal errors */
  public boolean hasValidOutputs() {
    return getBuildResult() != null && !getBuildResult().allBuildsFailed();
  }

  public abstract BlazeInfo getBlazeInfo();

  @Nullable
  public abstract BlazeBuildOutputs getBuildResult();

  public abstract ImmutableList<BuildPhaseSyncStats> getBuildPhaseStats();

  public static Builder builder() {
    return new AutoValue_BlazeSyncBuildResult.Builder().setBuildPhaseStats(ImmutableList.of());
  }

  public abstract Builder toBuilder();

  /** A builder for {@link BlazeSyncBuildResult} objects. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setBlazeInfo(BlazeInfo info);

    public abstract Builder setBuildResult(BlazeBuildOutputs buildResult);

    public abstract Builder setBuildPhaseStats(Iterable<BuildPhaseSyncStats> stats);

    public abstract BlazeSyncBuildResult build();
  }
}
