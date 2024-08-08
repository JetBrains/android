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
import com.google.idea.blaze.base.qsync.QuerySyncManager.TaskOrigin;
import com.intellij.openapi.actionSystem.AnActionEvent;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import javax.annotation.Nullable;

/** Sync stats covering all phases of query sync and build dependencies. */
@AutoValue
public abstract class QuerySyncActionStats {
  private static final QuerySyncActionStats EMPTY =
      new AutoValue_QuerySyncActionStats.Builder()
          .setStartTime(Instant.EPOCH)
          .setResult(Result.UNKNOWN)
          .setRequestedFiles(ImmutableSet.of())
          .setProjectInfo(ProjectInfoStats.builder().build())
          .setDependenciesInfo(DependenciesInfoStats.builder().build())
          .setBuildWorkingSetEnabled(false)
          .setTaskOrigin(TaskOrigin.UNKNOWN)
          .build();

  /** The result of query sync operations. */
  public enum Result {
    UNKNOWN,
    SUCCESS,
    FAILURE,
    SUCCESS_WITH_WARNING,
    CANCELLED
  }

  public abstract Instant startTime();

  public abstract Optional<Duration> totalClockTime();

  public abstract Result result();

  // The method current action get triggered. If it's null but trigger action is not, it may be
  // triggered by system e.g. startup activity
  public abstract Optional<String> triggerMethod();

  public abstract Optional<String> triggerActionName();

  public abstract ImmutableSet<Path> requestedFiles();

  public abstract ImmutableList<QuerySyncOperationStats> operationStats();

  public abstract ProjectInfoStats projectInfo();

  public abstract DependenciesInfoStats dependenciesInfo();

  public abstract boolean buildWorkingSetEnabled();

  public abstract TaskOrigin taskOrigin();

  public abstract Builder toBuilder();

  public static Builder builder() {
    return EMPTY.toBuilder();
  }

  /** Auto value builder for QuerySyncStats. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Instant startTime();

    public abstract Builder setStartTime(Instant value);

    public abstract Builder setTotalClockTime(@Nullable Duration value);

    public abstract Builder setResult(Result value);

    public abstract Builder setTriggerMethod(@Nullable String value);

    public abstract Builder setTriggerActionName(String value);

    public abstract Builder setRequestedFiles(ImmutableSet<Path> value);

    public abstract Builder setProjectInfo(ProjectInfoStats value);

    public abstract Builder setDependenciesInfo(DependenciesInfoStats value);

    public abstract Builder setBuildWorkingSetEnabled(boolean value);

    public abstract Builder setTaskOrigin(TaskOrigin value);

    abstract ImmutableList.Builder<QuerySyncOperationStats> operationStatsBuilder();

    @CanIgnoreReturnValue
    public final Builder addOperationStats(QuerySyncOperationStats querySyncOperationStats) {
      operationStatsBuilder().add(querySyncOperationStats);
      return this;
    }

    public final Builder handleActionClass(Class<?> value) {
      setTriggerActionName(value.getName());
      return this;
    }

    public final Builder handleActionEvent(@Nullable AnActionEvent value) {
      setTriggerMethod(value == null ? null : value.getPlace());
      return this;
    }

    public abstract QuerySyncActionStats build();
  }
}
