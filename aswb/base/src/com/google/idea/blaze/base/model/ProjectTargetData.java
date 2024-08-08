/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
import com.google.common.collect.Maps;
import com.google.devtools.intellij.model.ProjectData;
import com.google.devtools.intellij.model.ProjectData.TargetData;
import com.google.idea.blaze.base.ideinfo.ProtoWrapper;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.sync.aspects.BlazeIdeInterfaceState;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import java.util.Objects;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/** Project data relating to targets and their generated outputs. */
public final class ProjectTargetData implements ProtoWrapper<ProjectData.TargetData> {

  private final TargetMap targetMap;
  @Nullable public final BlazeIdeInterfaceState ideInterfaceState;
  public final RemoteOutputArtifacts remoteOutputs;

  public ProjectTargetData(
      TargetMap targetMap,
      @Nullable BlazeIdeInterfaceState ideInterfaceState,
      RemoteOutputArtifacts remoteOutputs) {
    this.targetMap = targetMap;
    this.ideInterfaceState = ideInterfaceState;
    this.remoteOutputs = remoteOutputs;
  }

  public TargetMap targetMap() {
    return targetMap;
  }

  public static ProjectTargetData fromProto(ProjectData.TargetData proto) {
    TargetMap targetMap = TargetMap.fromProto(proto.getTargetMap());
    BlazeIdeInterfaceState ideInterfaceState =
        proto.hasIdeInterfaceState()
            ? BlazeIdeInterfaceState.fromProto(proto.getIdeInterfaceState())
            : null;
    RemoteOutputArtifacts remoteOutputs = RemoteOutputArtifacts.fromProto(proto.getRemoteOutputs());
    return new ProjectTargetData(targetMap, ideInterfaceState, remoteOutputs);
  }

  @Override
  public TargetData toProto() {
    ProjectData.TargetData.Builder builder =
        ProjectData.TargetData.newBuilder()
            .setTargetMap(targetMap.toProto())
            .setRemoteOutputs(remoteOutputs.toProto());
    ProtoWrapper.unwrapAndSetIfNotNull(builder::setIdeInterfaceState, ideInterfaceState);
    return builder.build();
  }

  /**
   * Filters this {@link ProjectTargetData}, keeping only the targets matching the given predicate.
   */
  public ProjectTargetData filter(
      Predicate<TargetKey> targetsToKeep, WorkspaceLanguageSettings settings) {
    TargetMap newTargets =
        new TargetMap(ImmutableMap.copyOf(Maps.filterKeys(targetMap.map(), targetsToKeep::test)));
    BlazeIdeInterfaceState newState =
        ideInterfaceState != null ? ideInterfaceState.filter(targetsToKeep) : null;
    RemoteOutputArtifacts newOutputs = remoteOutputs.removeUntrackedOutputs(newTargets, settings);
    return new ProjectTargetData(newTargets, newState, newOutputs);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ProjectTargetData that = (ProjectTargetData) o;
    return targetMap.equals(that.targetMap)
        && Objects.equals(ideInterfaceState, that.ideInterfaceState)
        && remoteOutputs.equals(that.remoteOutputs);
  }

  @Override
  public int hashCode() {
    return Objects.hash(targetMap, ideInterfaceState, remoteOutputs);
  }
}
