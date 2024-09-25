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
package com.google.idea.blaze.android.sync.model;

import com.google.devtools.intellij.model.ProjectData;
import com.google.idea.blaze.base.ideinfo.ProtoWrapper;
import com.google.idea.blaze.base.model.SyncData;
import java.util.Objects;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/** Sync data for the Android plugin. */
@Immutable
public final class BlazeAndroidSyncData implements SyncData<ProjectData.BlazeAndroidSyncData> {
  public final BlazeAndroidImportResult importResult;
  @Nullable public final AndroidSdkPlatform androidSdkPlatform;

  public BlazeAndroidSyncData(
      BlazeAndroidImportResult importResult, @Nullable AndroidSdkPlatform androidSdkPlatform) {
    this.importResult = importResult;
    this.androidSdkPlatform = androidSdkPlatform;
  }

  private static BlazeAndroidSyncData fromProto(ProjectData.BlazeAndroidSyncData proto) {
    return new BlazeAndroidSyncData(
        BlazeAndroidImportResult.fromProto(proto.getImportResult()),
        proto.hasAndroidSdkPlatform()
            ? AndroidSdkPlatform.fromProto(proto.getAndroidSdkPlatform())
            : null);
  }

  @Override
  public ProjectData.BlazeAndroidSyncData toProto() {
    ProjectData.BlazeAndroidSyncData.Builder builder =
        ProjectData.BlazeAndroidSyncData.newBuilder().setImportResult(importResult.toProto());
    ProtoWrapper.unwrapAndSetIfNotNull(builder::setAndroidSdkPlatform, androidSdkPlatform);
    return builder.build();
  }

  @Override
  public void insert(ProjectData.SyncState.Builder builder) {
    builder.setBlazeAndroidSyncData(toProto());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    BlazeAndroidSyncData that = (BlazeAndroidSyncData) o;
    return Objects.equals(importResult, that.importResult)
        && Objects.equals(androidSdkPlatform, that.androidSdkPlatform);
  }

  @Override
  public int hashCode() {
    return Objects.hash(importResult, androidSdkPlatform);
  }

  static class Extractor implements SyncData.Extractor<BlazeAndroidSyncData> {
    @Nullable
    @Override
    public BlazeAndroidSyncData extract(ProjectData.SyncState syncState) {
      return syncState.hasBlazeAndroidSyncData()
          ? BlazeAndroidSyncData.fromProto(syncState.getBlazeAndroidSyncData())
          : null;
    }
  }
}
