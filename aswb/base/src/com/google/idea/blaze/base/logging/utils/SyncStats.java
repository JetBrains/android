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
package com.google.idea.blaze.base.logging.utils;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.scope.scopes.TimingScopeListener.TimedEvent;
import com.google.idea.blaze.base.settings.BuildBinaryType;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.SyncResult;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Sync stats covering all phases of sync. */
@AutoValue
public abstract class SyncStats {
  public abstract SyncMode syncMode();

  public abstract String syncTitle();

  /** A string describing what triggered the sync (e.g. on startup, auto-sync, etc.). */
  public abstract String syncOrigin();

  public abstract BuildBinaryType syncBinaryType();

  public abstract SyncResult syncResult();

  public abstract ImmutableList<TimedEvent> timedEvents();

  public abstract ImmutableMap<String, Long> networkUsage();

  public abstract Instant startTime();

  public abstract Duration totalClockTime();

  public abstract Duration blazeExecTime();

  public abstract WorkspaceType workspaceType();

  public abstract ImmutableList<LanguageClass> languagesActive();

  public abstract ImmutableList<WorkspacePath> blazeProjectFiles();

  public abstract ImmutableList<BuildPhaseSyncStats> buildPhaseStats();

  public abstract int targetMapSize();

  public abstract int libraryCount();

  public static Builder builder() {
    return new AutoValue_SyncStats.Builder()
        .setBlazeExecTime(Duration.ZERO)
        .setWorkspaceType(WorkspaceType.JAVA)
        .setTargetMapSize(0)
        .setLanguagesActive(ImmutableList.of())
        .setBlazeProjectFiles(ImmutableList.of())
        .setLibraryCount(0)
        .setSyncBinaryType(BuildBinaryType.NONE);
  }

  /** Auto value builder for SyncStats. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setSyncMode(SyncMode syncMode);

    public abstract Builder setSyncTitle(String syncTitle);

    public abstract Builder setSyncOrigin(String syncOrigin);

    public abstract Builder setSyncBinaryType(BuildBinaryType binaryType);

    public abstract Builder setSyncResult(SyncResult syncResult);

    abstract ImmutableList.Builder<TimedEvent> timedEventsBuilder();

    @CanIgnoreReturnValue
    public Builder addTimedEvents(List<TimedEvent> timedEvents) {
      timedEventsBuilder().addAll(timedEvents);
      return this;
    }

    public ImmutableList<TimedEvent> getCurrentTimedEvents() {
      return timedEventsBuilder().build();
    }

    abstract ImmutableMap.Builder<String, Long> networkUsageBuilder();

    @CanIgnoreReturnValue
    public Builder addNetworkUsage(Map<String, Long> stats) {
      networkUsageBuilder().putAll(stats);
      return this;
    }

    public abstract Builder setStartTime(Instant instant);

    public abstract Builder setTotalClockTime(Duration totalTime);

    public abstract Builder setBlazeExecTime(Duration blazeExecTime);

    public abstract Builder setWorkspaceType(WorkspaceType workspaceType);

    public abstract Builder setLanguagesActive(Iterable<LanguageClass> languagesActive);

    public abstract Builder setBlazeProjectFiles(List<WorkspacePath> blazeProjectFiles);

    public abstract Builder setTargetMapSize(int targetMapSize);

    abstract ImmutableList.Builder<BuildPhaseSyncStats> buildPhaseStatsBuilder();

    @CanIgnoreReturnValue
    public Builder addBuildPhaseStats(BuildPhaseSyncStats buildPhaseStats) {
      buildPhaseStatsBuilder().add(buildPhaseStats);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder addAllBuildPhaseStats(Iterable<BuildPhaseSyncStats> buildPhaseStats) {
      buildPhaseStatsBuilder().addAll(buildPhaseStats);
      return this;
    }

    public abstract Builder setLibraryCount(int librariesCount);

    public abstract SyncStats build();
  }
}
