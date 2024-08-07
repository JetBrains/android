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
package com.google.idea.blaze.base.model;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoderImpl;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import java.io.File;

/**
 * Use to build mock project data for tests.
 *
 * <p>For any data you don't supply, the builder makes a best-effort attempt to create default
 * objects using whatever data you have supplied if applicable.
 */
public class MockBlazeProjectDataBuilder {
  private final WorkspaceRoot workspaceRoot;

  private TargetMap targetMap;
  private String outputBase;
  private BlazeInfo blazeInfo;
  private BlazeVersionData blazeVersionData;
  private WorkspacePathResolver workspacePathResolver;
  private ArtifactLocationDecoder artifactLocationDecoder;
  private WorkspaceLanguageSettings workspaceLanguageSettings;
  private SyncState syncState;

  private MockBlazeProjectDataBuilder(WorkspaceRoot workspaceRoot) {
    this.workspaceRoot = workspaceRoot;
  }

  public static MockBlazeProjectDataBuilder builder() {
    return builder(new WorkspaceRoot(new File("/")));
  }

  public static MockBlazeProjectDataBuilder builder(WorkspaceRoot workspaceRoot) {
    return new MockBlazeProjectDataBuilder(workspaceRoot);
  }

  @CanIgnoreReturnValue
  public MockBlazeProjectDataBuilder setTargetMap(TargetMap targetMap) {
    this.targetMap = targetMap;
    return this;
  }

  @CanIgnoreReturnValue
  public MockBlazeProjectDataBuilder setOutputBase(String outputBase) {
    this.outputBase = outputBase;
    return this;
  }

  @CanIgnoreReturnValue
  public MockBlazeProjectDataBuilder setBlazeInfo(BlazeInfo blazeInfo) {
    this.blazeInfo = blazeInfo;
    return this;
  }

  @CanIgnoreReturnValue
  public MockBlazeProjectDataBuilder setBlazeVersionData(BlazeVersionData blazeVersionData) {
    this.blazeVersionData = blazeVersionData;
    return this;
  }

  @CanIgnoreReturnValue
  public MockBlazeProjectDataBuilder setWorkspacePathResolver(
      WorkspacePathResolver workspacePathResolver) {
    this.workspacePathResolver = workspacePathResolver;
    return this;
  }

  @CanIgnoreReturnValue
  public MockBlazeProjectDataBuilder setArtifactLocationDecoder(
      ArtifactLocationDecoder artifactLocationDecoder) {
    this.artifactLocationDecoder = artifactLocationDecoder;
    return this;
  }

  @CanIgnoreReturnValue
  public MockBlazeProjectDataBuilder setWorkspaceLanguageSettings(
      WorkspaceLanguageSettings workspaceLanguageSettings) {
    this.workspaceLanguageSettings = workspaceLanguageSettings;
    return this;
  }

  @CanIgnoreReturnValue
  public MockBlazeProjectDataBuilder setSyncState(SyncState syncState) {
    this.syncState = syncState;
    return this;
  }

  public BlazeProjectData build() {
    TargetMap targetMap =
        this.targetMap != null ? this.targetMap : new TargetMap(ImmutableMap.of());
    BlazeInfo blazeInfo = this.blazeInfo;
    if (blazeInfo == null) {
      String outputBase = this.outputBase != null ? this.outputBase : "/usr/workspace/1234";
      blazeInfo =
          BlazeInfo.createMockBlazeInfo(
              outputBase,
              outputBase + "/execroot",
              outputBase + "/execroot/bin",
              outputBase + "/execroot/gen",
              outputBase + "/execroot/testlogs");
    }
    BlazeVersionData blazeVersionData =
        this.blazeVersionData != null ? this.blazeVersionData : BlazeVersionData.builder().build();
    WorkspacePathResolver workspacePathResolver =
        this.workspacePathResolver != null
            ? this.workspacePathResolver
            : new WorkspacePathResolverImpl(workspaceRoot);
    ArtifactLocationDecoder artifactLocationDecoder =
        this.artifactLocationDecoder != null
            ? this.artifactLocationDecoder
            : new ArtifactLocationDecoderImpl(
                blazeInfo, workspacePathResolver, RemoteOutputArtifacts.EMPTY);
    WorkspaceLanguageSettings workspaceLanguageSettings =
        this.workspaceLanguageSettings != null
            ? this.workspaceLanguageSettings
            : new WorkspaceLanguageSettings(WorkspaceType.JAVA, ImmutableSet.of());
    SyncState syncState =
        this.syncState != null ? this.syncState : new SyncState(ImmutableMap.of());

    return new AspectSyncProjectData(
        new ProjectTargetData(
            targetMap, /* ideInterfaceState= */ null, RemoteOutputArtifacts.EMPTY),
        blazeInfo,
        blazeVersionData,
        workspacePathResolver,
        artifactLocationDecoder,
        workspaceLanguageSettings,
        syncState);
  }
}
