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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.devtools.intellij.model.ProjectData;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.aspects.BlazeIdeInterfaceState;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoderImpl;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.common.BuildTarget;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import javax.annotation.concurrent.Immutable;
import org.jetbrains.annotations.Nullable;

/** The top-level object serialized to cache. */
@Immutable
public final class AspectSyncProjectData implements BlazeProjectData {
  private final ProjectTargetData targetData;
  private final BlazeInfo blazeInfo;
  private final BlazeVersionData blazeVersionData;
  private final WorkspacePathResolver workspacePathResolver;
  private final ArtifactLocationDecoder artifactLocationDecoder;
  private final WorkspaceLanguageSettings workspaceLanguageSettings;
  private final SyncState syncState;

  public AspectSyncProjectData(
      ProjectTargetData targetData,
      BlazeInfo blazeInfo,
      BlazeVersionData blazeVersionData,
      WorkspacePathResolver workspacePathResolver,
      ArtifactLocationDecoder artifactLocationDecoder,
      WorkspaceLanguageSettings workspaceLanguageSettings,
      SyncState syncState) {
    this.targetData = targetData;
    this.blazeInfo = blazeInfo;
    this.blazeVersionData = blazeVersionData;
    this.workspacePathResolver = workspacePathResolver;
    this.artifactLocationDecoder = artifactLocationDecoder;
    this.workspaceLanguageSettings = workspaceLanguageSettings;
    this.syncState = syncState;
  }

  @VisibleForTesting
  public static AspectSyncProjectData fromProto(
      BuildSystemName buildSystemName, ProjectData.BlazeProjectData proto) {
    BlazeInfo blazeInfo = BlazeInfo.fromProto(buildSystemName, proto.getBlazeInfo());
    WorkspacePathResolver workspacePathResolver =
        WorkspacePathResolver.fromProto(proto.getWorkspacePathResolver());
    ProjectTargetData targetData = parseTargetData(proto);
    return new AspectSyncProjectData(
        targetData,
        blazeInfo,
        BlazeVersionData.fromProto(proto.getBlazeVersionData()),
        workspacePathResolver,
        new ArtifactLocationDecoderImpl(blazeInfo, workspacePathResolver, targetData.remoteOutputs),
        WorkspaceLanguageSettings.fromProto(proto.getWorkspaceLanguageSettings()),
        SyncState.fromProto(proto.getSyncState()));
  }

  private static ProjectTargetData parseTargetData(ProjectData.BlazeProjectData proto) {
    if (proto.hasTargetData()) {
      return ProjectTargetData.fromProto(proto.getTargetData());
    }
    // handle older version of project data
    TargetMap map = TargetMap.fromProto(proto.getTargetMap());
    BlazeIdeInterfaceState ideInterfaceState =
        BlazeIdeInterfaceState.fromProto(proto.getSyncState().getBlazeIdeInterfaceState());
    RemoteOutputArtifacts remoteOutputs =
        proto.getSyncState().hasRemoteOutputArtifacts()
            ? RemoteOutputArtifacts.fromProto(proto.getSyncState().getRemoteOutputArtifacts())
            : RemoteOutputArtifacts.EMPTY;
    return new ProjectTargetData(map, ideInterfaceState, remoteOutputs);
  }

  @VisibleForTesting
  public ProjectData.BlazeProjectData toProto() {
    return ProjectData.BlazeProjectData.newBuilder()
        .setTargetData(targetData.toProto())
        .setBlazeInfo(blazeInfo.toProto())
        .setBlazeVersionData(blazeVersionData.toProto())
        .setWorkspacePathResolver(workspacePathResolver.toProto())
        .setWorkspaceLanguageSettings(workspaceLanguageSettings.toProto())
        .setSyncState(syncState.toProto())
        .build();
  }

  private TargetInfo getTargetInfo(Label label) {
    TargetMap map = getTargetMap();
    // look for a plain target first
    TargetIdeInfo target = map.get(TargetKey.forPlainTarget(label));
    if (target != null) {
      return target.toTargetInfo();
    }
    // otherwise just return any matching target
    return map.targets().stream()
        .filter(t -> Objects.equals(label, t.getKey().getLabel()))
        .findFirst()
        .map(TargetIdeInfo::toTargetInfo)
        .orElse(null);
  }

  @Nullable
  @Override
  public BuildTarget getBuildTarget(Label label) {
    TargetInfo targetInfo = getTargetInfo(label);
    if (targetInfo == null) {
      return null;
    }
    return BuildTarget.create(
        com.google.idea.blaze.common.Label.of(targetInfo.label.toString()), targetInfo.kindString);
  }

  @Override
  public ImmutableList<TargetInfo> targets() {
    return getTargetMap().targets().stream()
        .map(TargetIdeInfo::toTargetInfo)
        .collect(ImmutableList.toImmutableList());
  }

  public ProjectTargetData getTargetData() {
    return targetData;
  }

  @Override
  public TargetMap getTargetMap() {
    return targetData.targetMap();
  }

  @Override
  public BlazeInfo getBlazeInfo() {
    return blazeInfo;
  }

  @Override
  public BlazeVersionData getBlazeVersionData() {
    return blazeVersionData;
  }

  @Override
  public WorkspacePathResolver getWorkspacePathResolver() {
    return workspacePathResolver;
  }

  @Override
  public ArtifactLocationDecoder getArtifactLocationDecoder() {
    return artifactLocationDecoder;
  }

  @Override
  public WorkspaceLanguageSettings getWorkspaceLanguageSettings() {
    return workspaceLanguageSettings;
  }

  @Override
  public RemoteOutputArtifacts getRemoteOutputs() {
    return targetData.remoteOutputs;
  }

  @Override
  public SyncState getSyncState() {
    return syncState;
  }

  @Override
  public boolean isQuerySync() {
    return false;
  }

  public static AspectSyncProjectData loadFromDisk(BuildSystemName buildSystemName, File file)
      throws IOException {
    try (InputStream stream = new GZIPInputStream(new FileInputStream(file))) {
      return fromProto(buildSystemName, ProjectData.BlazeProjectData.parseFrom(stream));
    }
  }

  public void saveToDisk(File file) throws IOException {
    ProjectData.BlazeProjectData proto = toProto();
    try (OutputStream stream = new GZIPOutputStream(new FileOutputStream(file))) {
      proto.writeTo(stream);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof AspectSyncProjectData)) {
      return false;
    }
    AspectSyncProjectData other = (AspectSyncProjectData) o;
    return Objects.equals(targetData, other.targetData)
        && Objects.equals(blazeInfo, other.blazeInfo)
        && Objects.equals(blazeVersionData, other.blazeVersionData)
        && Objects.equals(workspacePathResolver, other.workspacePathResolver)
        && Objects.equals(artifactLocationDecoder, other.artifactLocationDecoder)
        && Objects.equals(workspaceLanguageSettings, other.workspaceLanguageSettings)
        && Objects.equals(syncState, other.syncState);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        targetData,
        blazeInfo,
        blazeVersionData,
        workspaceLanguageSettings,
        artifactLocationDecoder,
        workspaceLanguageSettings,
        syncState);
  }
}
