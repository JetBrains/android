/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.logging.utils.querysync;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.base.settings.BuildBinaryType;
import com.google.idea.blaze.common.Label;
import java.time.Duration;
import java.util.Optional;
import javax.annotation.Nullable;

/** Sync stats covering build dependencies phase. */
@AutoValue
public abstract class BuildDepsStats implements QuerySyncOperationStats {
  private static final BuildDepsStats EMPTY =
      new AutoValue_BuildDepsStats.Builder()
          .setBuildTargets(ImmutableSet.of())
          .setRequestedTargets(ImmutableSet.of())
          .setBuildFlags(ImmutableList.of())
          .setBuildIds(ImmutableList.of())
          .setBlazeBinaryType(BuildBinaryType.NONE)
          .build();

  public abstract Optional<Integer> bazelExitCode();

  public abstract BuildBinaryType blazeBinaryType();

  public abstract ImmutableList<String> buildFlags();

  public abstract ImmutableList<String> buildIds();

  public abstract ImmutableSet<Label> requestedTargets();

  public abstract ImmutableSet<Label> buildTargets();

  public abstract Optional<Long> bepByteConsumed();

  public abstract Optional<Long> artifactBytesConsumed();

  @Override
  public abstract Optional<Duration> totalClockTime();

  public abstract BuildDepsStats.Builder toBuilder();

  public static BuildDepsStats.Builder builder() {
    return EMPTY.toBuilder();
  }

  /** Auto value builder for BuildDepsStats. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setBazelExitCode(@Nullable Integer value);

    public abstract Builder setBlazeBinaryType(BuildBinaryType value);

    public abstract Builder setRequestedTargets(ImmutableSet<Label> value);

    @CanIgnoreReturnValue
    public final Builder setRequestedTargets(Iterable<Label> value) {
      setRequestedTargets(ImmutableSet.copyOf(value));
      return this;
    }

    public abstract Builder setBuildTargets(ImmutableSet<Label> value);

    @CanIgnoreReturnValue
    public final Builder setBuildTargets(Iterable<Label> value) {
      setBuildTargets(ImmutableSet.copyOf(value));
      return this;
    }

    public abstract Builder setBuildFlags(ImmutableList<String> value);

    public abstract Builder setBuildIds(ImmutableList<String> value);

    public abstract Builder setBepByteConsumed(@Nullable Long value);

    public abstract Builder setArtifactBytesConsumed(@Nullable Long value);

    public abstract Builder setTotalClockTime(@Nullable Duration value);

    public abstract BuildDepsStats build();
  }
}
