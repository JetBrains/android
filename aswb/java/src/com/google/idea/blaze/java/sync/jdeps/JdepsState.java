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
package com.google.idea.blaze.java.sync.jdeps;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.intellij.model.ProjectData;
import com.google.idea.blaze.base.filecache.ArtifactStateProtoConverter;
import com.google.idea.blaze.base.ideinfo.ProtoWrapper;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.model.SyncData;
import com.google.idea.blaze.common.artifact.ArtifactState;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;

final class JdepsState implements SyncData<ProjectData.JdepsState> {

  @AutoValue
  abstract static class JdepsData {
    abstract TargetKey getTargetKey();

    abstract ImmutableList<String> getJdeps();

    abstract ArtifactState getFile();

    static JdepsData create(TargetKey targetKey, List<String> jdeps, ArtifactState file) {
      return new AutoValue_JdepsState_JdepsData(targetKey, ImmutableList.copyOf(jdeps), file);
    }
  }

  final ImmutableList<JdepsData> data;

  private JdepsState(List<JdepsData> data) {
    this.data = ImmutableList.copyOf(data);
  }

  Map<TargetKey, List<String>> getJdepsMap() {
    return data.stream().collect(toImmutableMap(JdepsData::getTargetKey, JdepsData::getJdeps));
  }

  ImmutableMap<String, ArtifactState> getArtifactState() {
    return data.stream()
        .collect(toImmutableMap(s -> s.getFile().getKey(), s -> s.getFile(), (a, b) -> a));
  }

  private static JdepsState fromNewProto(ProjectData.TargetToJdepsMap proto) {
    ImmutableList<JdepsData> data =
        proto.getEntriesList().stream()
            .map(
                e ->
                    JdepsData.create(
                        TargetKey.fromProto(e.getKey()),
                        ProtoWrapper.internStrings(e.getValueList()),
                        ArtifactStateProtoConverter.fromProto(e.getFile())))
            .collect(toImmutableList());
    return new JdepsState(data);
  }

  private static JdepsState fromProto(ProjectData.JdepsState proto) {
    if (proto.getFileToTargetCount() == 0) {
      return fromNewProto(proto.getTargetToJdeps());
    }

    // migrate from the old proto format
    ImmutableMap<TargetKey, String> targetToArtifactKey =
        proto.getFileToTargetMap().entrySet().stream()
            .collect(
                toImmutableMap(
                    e -> TargetKey.fromProto(e.getValue()), Map.Entry::getKey, (a, b) -> a));
    ImmutableMap<String, ArtifactState> artifacts =
        proto.getJdepsFilesList().stream()
            .map(ArtifactStateProtoConverter::fromProto)
            .filter(Objects::nonNull)
            .collect(toImmutableMap(ArtifactState::getKey, s -> s, (a, b) -> a));
    ImmutableList.Builder<JdepsData> data = ImmutableList.builder();
    for (ProjectData.TargetToJdepsMap.Entry e : proto.getTargetToJdeps().getEntriesList()) {
      TargetKey key = TargetKey.fromProto(e.getKey());
      ImmutableList<String> jdeps = ProtoWrapper.internStrings(e.getValueList());
      String artifactKey = targetToArtifactKey.get(key);
      ArtifactState file = artifactKey != null ? artifacts.get(artifactKey) : null;
      if (file != null) {
        data.add(JdepsData.create(key, jdeps, file));
      }
    }
    return new JdepsState(data.build());
  }

  @Override
  public ProjectData.JdepsState toProto() {
    ProjectData.TargetToJdepsMap.Builder proto =
        ProjectData.TargetToJdepsMap.newBuilder()
            .addAllEntries(
                data.stream()
                    .map(
                        s ->
                            ProjectData.TargetToJdepsMap.Entry.newBuilder()
                                .setKey(s.getTargetKey().toProto())
                                .setFile(ArtifactStateProtoConverter.toProto(s.getFile()))
                                .addAllValue(s.getJdeps())
                                .build())
                    .collect(toImmutableList()));
    return ProjectData.JdepsState.newBuilder().setTargetToJdeps(proto).build();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    JdepsState that = (JdepsState) o;
    return Objects.equals(data, that.data);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(data);
  }

  static Builder builder() {
    return new Builder();
  }

  static class Builder {
    final ArrayList<JdepsData> list = new ArrayList<>();

    JdepsState build() {
      return new JdepsState(list);
    }

    void removeArtifacts(Collection<ArtifactState> artifacts) {
      Set<String> toRemove = artifacts.stream().map(a -> a.getKey()).collect(toImmutableSet());
      list.removeIf(d -> toRemove.contains(d.getFile().getKey()));
    }
  }

  @Override
  public void insert(ProjectData.SyncState.Builder builder) {
    builder.setJdepsState(toProto());
  }

  static class Extractor implements SyncData.Extractor<JdepsState> {
    @Nullable
    @Override
    public JdepsState extract(ProjectData.SyncState syncState) {
      return syncState.hasJdepsState() ? JdepsState.fromProto(syncState.getJdepsState()) : null;
    }
  }
}
