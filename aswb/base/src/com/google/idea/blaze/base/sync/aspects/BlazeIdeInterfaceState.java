/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync.aspects;

import com.google.common.base.Functions;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.devtools.intellij.model.ProjectData;
import com.google.devtools.intellij.model.ProjectData.LocalFileOrOutputArtifact;
import com.google.idea.blaze.base.filecache.ArtifactStateProtoConverter;
import com.google.idea.blaze.base.ideinfo.ProtoWrapper;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.common.artifact.ArtifactState;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/** Sync state for aspect output files, and their mapping to targets. */
public final class BlazeIdeInterfaceState
    implements ProtoWrapper<ProjectData.BlazeIdeInterfaceState> {

  /** A mapping from artifact key to {@link ArtifactState} for all targets built during sync. */
  final ImmutableMap<String, ArtifactState> ideInfoFileState;

  /**
   * A mapping from artifact key to {@link TargetKey} for only those targets which were added to the
   * target map.
   *
   * <p>This excludes targets for unsupported languages, and duplicates (targets built against
   * multiple configurations).
   */
  final ImmutableBiMap<String, TargetKey> ideInfoFileToTargetKey;

  private BlazeIdeInterfaceState(
      Map<String, ArtifactState> ideInfoFileState,
      BiMap<String, TargetKey> ideInfoFileToTargetKey) {
    this.ideInfoFileState = ImmutableMap.copyOf(ideInfoFileState);
    this.ideInfoFileToTargetKey = ImmutableBiMap.copyOf(ideInfoFileToTargetKey);
  }

  public static BlazeIdeInterfaceState fromProto(ProjectData.BlazeIdeInterfaceState proto) {
    ImmutableMap<String, TargetKey> targets =
        ProtoWrapper.map(proto.getFileToTargetMap(), Functions.identity(), TargetKey::fromProto);
    ImmutableMap.Builder<String, ArtifactState> artifacts = ImmutableMap.builder();
    for (LocalFileOrOutputArtifact output : proto.getIdeInfoFilesList()) {
      ArtifactState state = ArtifactStateProtoConverter.fromProto(output);
      if (state == null) {
        continue;
      }
      artifacts.put(state.getKey(), state);
    }
    return new BlazeIdeInterfaceState(artifacts.build(), ImmutableBiMap.copyOf(targets));
  }

  @Override
  public ProjectData.BlazeIdeInterfaceState toProto() {
    ProjectData.BlazeIdeInterfaceState.Builder proto =
        ProjectData.BlazeIdeInterfaceState.newBuilder()
            .putAllFileToTarget(
                ProtoWrapper.map(ideInfoFileToTargetKey, Functions.identity(), TargetKey::toProto));
    for (String key : ideInfoFileState.keySet()) {
      proto.addIdeInfoFiles(ArtifactStateProtoConverter.toProto(ideInfoFileState.get(key)));
    }
    return proto.build();
  }

  public BlazeIdeInterfaceState filter(Predicate<TargetKey> targetsToKeep) {
    BiMap<String, TargetKey> filteredBiMap =
        Maps.filterValues(ideInfoFileToTargetKey, targetsToKeep::test);
    return new BlazeIdeInterfaceState(
        Maps.filterKeys(ideInfoFileState, filteredBiMap::containsKey), filteredBiMap);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    BlazeIdeInterfaceState that = (BlazeIdeInterfaceState) o;
    return Objects.equals(ideInfoFileState, that.ideInfoFileState)
        && Objects.equals(ideInfoFileToTargetKey, that.ideInfoFileToTargetKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(ideInfoFileState, ideInfoFileToTargetKey);
  }

  static Builder builder() {
    return new Builder();
  }

  static class Builder {
    ImmutableMap<String, ArtifactState> ideInfoFileState = null;
    BiMap<String, TargetKey> ideInfoToTargetKey = HashBiMap.create();

    BlazeIdeInterfaceState build() {
      return new BlazeIdeInterfaceState(ideInfoFileState, ideInfoToTargetKey);
    }
  }
}
