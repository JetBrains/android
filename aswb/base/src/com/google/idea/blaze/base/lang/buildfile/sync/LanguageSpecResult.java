/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.lang.buildfile.sync;

import com.google.devtools.intellij.model.ProjectData;
import com.google.idea.blaze.base.lang.buildfile.language.semantics.BuildLanguageSpec;
import com.google.idea.blaze.base.model.SyncData;
import java.util.Objects;
import javax.annotation.Nullable;

/** The BUILD language specifications, serialized along with the sync data. */
public final class LanguageSpecResult implements SyncData<ProjectData.LanguageSpecResult> {
  private static final long ONE_DAY_IN_MILLISECONDS = 1000 * 60 * 60 * 24;

  private final BuildLanguageSpec spec;
  private final long timestampMillis;

  LanguageSpecResult(BuildLanguageSpec spec, long timestampMillis) {
    this.spec = spec;
    this.timestampMillis = timestampMillis;
  }

  private static LanguageSpecResult fromProto(ProjectData.LanguageSpecResult proto) {
    return new LanguageSpecResult(
        BuildLanguageSpec.fromProto(proto.getSpec()), proto.getTimestampMillis());
  }

  @Override
  public ProjectData.LanguageSpecResult toProto() {
    return ProjectData.LanguageSpecResult.newBuilder()
        .setSpec(spec.toProto())
        .setTimestampMillis(timestampMillis)
        .build();
  }

  public BuildLanguageSpec getSpec() {
    return spec;
  }

  public long getTimestampMillis() {
    return timestampMillis;
  }

  public boolean shouldRecalculateSpec() {
    return System.currentTimeMillis() - getTimestampMillis() > ONE_DAY_IN_MILLISECONDS;
  }

  @Override
  public void insert(ProjectData.SyncState.Builder builder) {
    builder.setLanguageSpecResult(toProto());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    LanguageSpecResult that = (LanguageSpecResult) o;
    return timestampMillis == that.timestampMillis && Objects.equals(spec, that.spec);
  }

  @Override
  public int hashCode() {
    return Objects.hash(spec, timestampMillis);
  }

  static class Extractor implements SyncData.Extractor<LanguageSpecResult> {
    @Nullable
    @Override
    public LanguageSpecResult extract(ProjectData.SyncState syncState) {
      return syncState.hasLanguageSpecResult()
          ? LanguageSpecResult.fromProto(syncState.getLanguageSpecResult())
          : null;
    }
  }
}
