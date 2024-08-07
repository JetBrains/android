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
import com.google.idea.blaze.base.settings.BuildBinaryType;
import java.time.Duration;
import java.util.Optional;
import javax.annotation.Nullable;

/** Stats covering syncing project phase. */
@AutoValue
public abstract class SyncQueryStats implements QuerySyncOperationStats {
  private static final SyncQueryStats EMPTY =
      new AutoValue_SyncQueryStats.Builder()
          .setBlazeBinaryType(BuildBinaryType.NONE)
          .setSyncMode(SyncMode.UNKNOWN)
          .setQueryFlags(ImmutableList.of())
          .build();

  /** The kind of sync. */
  public enum SyncMode {
    UNKNOWN,
    FULL,
    DELTA
  }

  public abstract Optional<Integer> bazelExitCode();

  public abstract BuildBinaryType blazeBinaryType();

  public abstract ImmutableList<String> queryFlags();

  public abstract SyncMode syncMode();

  public abstract Optional<Long> queryResultSizeBytes();

  @Override
  public abstract Optional<Duration> totalClockTime();

  public abstract Builder toBuilder();

  public static Builder builder() {
    return EMPTY.toBuilder();
  }

  /** Auto value builder for SyncStats. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setBazelExitCode(@Nullable Integer value);

    public abstract Builder setBlazeBinaryType(BuildBinaryType value);

    public abstract Builder setQueryFlags(ImmutableList<String> value);

    public abstract Builder setSyncMode(SyncMode value);

    public abstract Builder setQueryResultSizeBytes(@Nullable Long value);

    public abstract Builder setTotalClockTime(@Nullable Duration value);

    public abstract SyncQueryStats build();
  }
}
