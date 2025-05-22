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
package com.google.idea.blaze.qsync.project;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.common.vcs.VcsState;
import com.google.idea.blaze.qsync.query.Query;
import com.google.idea.blaze.qsync.query.QuerySummary;
import com.google.idea.blaze.qsync.query.QuerySummaryImpl;
import java.util.Optional;

/**
 * Represents state of the project after the query invocation has been completed, but without any
 * state from dependencies builds and before the project structure has been derived from this.
 *
 * <p>This state is persisted to disk over an IDE restart. It is also the input to a partial query
 * update.
 */
@AutoValue
public abstract class PostQuerySyncData {

  public static final PostQuerySyncData EMPTY =
      builder()
          .setProjectDefinition(ProjectDefinition.EMPTY)
          .setVcsState(Optional.empty())
          .setBazelVersion(Optional.empty())
          .setQuerySummary(QuerySummary.EMPTY)
          .build();

  /** The definition that this project is based on. */
  public abstract ProjectDefinition projectDefinition();

  /** The VCS state at the time that the query was run. */
  public abstract Optional<VcsState> vcsState();

  /** The version of bazel that the query was run. */
  public abstract Optional<String> bazelVersion();

  /** The summarised output from the query. */
  public abstract QuerySummary querySummary();

  public static Builder builder() {
    return new AutoValue_PostQuerySyncData.Builder();
  }

  public abstract Builder toBuilder();

  /** Builder for {@link PostQuerySyncData}. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setProjectDefinition(ProjectDefinition value);

    public abstract Builder setVcsState(Optional<VcsState> value);

    public abstract Builder setBazelVersion(Optional<String> value);

    public abstract Builder setQuerySummary(QuerySummary value);

    @CanIgnoreReturnValue
    public Builder setQuerySummary(Query.Summary value) {
      return setQuerySummary(QuerySummaryImpl.create(value));
    }

    public abstract PostQuerySyncData build();
  }
}
