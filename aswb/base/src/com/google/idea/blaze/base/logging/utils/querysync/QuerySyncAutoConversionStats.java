/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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

/** Stores the Query Sync auto-conversion stats. */
@AutoValue
public abstract class QuerySyncAutoConversionStats {
  private static final QuerySyncAutoConversionStats EMPTY =
    new AutoValue_QuerySyncAutoConversionStats.Builder()
      .setStatus(Status.UNKNOWN)
      .build();

  /** The status of auto-conversion. */
  public enum Status {
    UNKNOWN,
    NOT_CONVERTED,
    CONVERTED,
    REVERTED
  }

  public abstract Status status();

  public abstract Builder toBuilder();

  public static Builder builder() {
    return EMPTY.toBuilder();
  }

  /** Auto value builder for QuerySyncAutoConversionStats. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setStatus(Status value);

    public abstract QuerySyncAutoConversionStats build();
  }
}
