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
package com.google.idea.blaze.java.sync.model;

import com.google.devtools.intellij.model.ProjectData;
import com.google.idea.blaze.base.model.SyncData;
import com.google.idea.blaze.base.projectview.section.Glob;
import java.util.Objects;
import javax.annotation.Nullable;

/** Sync data for the java plugin. */
public final class BlazeJavaSyncData implements SyncData<ProjectData.BlazeJavaSyncData> {
  private final BlazeJavaImportResult importResult;
  private final Glob.GlobSet excludedLibraries;

  public BlazeJavaSyncData(BlazeJavaImportResult importResult, Glob.GlobSet excludedLibraries) {
    this.importResult = importResult;
    this.excludedLibraries = excludedLibraries;
  }

  public static BlazeJavaSyncData fromProto(ProjectData.BlazeJavaSyncData proto) {
    return new BlazeJavaSyncData(
        BlazeJavaImportResult.fromProto(proto.getImportResult()),
        Glob.GlobSet.fromProto(proto.getExcludedLibrariesList()));
  }

  @Override
  public ProjectData.BlazeJavaSyncData toProto() {
    return ProjectData.BlazeJavaSyncData.newBuilder()
        .setImportResult(importResult.toProto())
        .addAllExcludedLibraries(excludedLibraries.toProto())
        .build();
  }

  public BlazeJavaImportResult getImportResult() {
    return importResult;
  }

  public Glob.GlobSet getExcludedLibraries() {
    return excludedLibraries;
  }

  @Override
  public void insert(ProjectData.SyncState.Builder builder) {
    builder.setBlazeJavaSyncData(toProto());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    BlazeJavaSyncData that = (BlazeJavaSyncData) o;
    return Objects.equals(importResult, that.importResult)
        && Objects.equals(excludedLibraries, that.excludedLibraries);
  }

  @Override
  public int hashCode() {
    return Objects.hash(importResult, excludedLibraries);
  }

  static class Extractor implements SyncData.Extractor<BlazeJavaSyncData> {
    @Nullable
    @Override
    public BlazeJavaSyncData extract(ProjectData.SyncState syncState) {
      return syncState.hasBlazeJavaSyncData()
          ? BlazeJavaSyncData.fromProto(syncState.getBlazeJavaSyncData())
          : null;
    }
  }
}
