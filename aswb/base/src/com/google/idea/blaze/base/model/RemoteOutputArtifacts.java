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
import com.google.common.collect.ImmutableSet;
import com.google.devtools.intellij.model.ProjectData;
import com.google.idea.blaze.base.command.buildresult.RemoteOutputArtifact;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.ProtoWrapper;
import com.google.idea.blaze.base.settings.BuildSystemName;
import java.util.Objects;
import javax.annotation.Nullable;

/** A set of {@link RemoteOutputArtifact}s we want to retain a reference to between syncs. */
public final class RemoteOutputArtifacts
    implements ProtoWrapper<ProjectData.RemoteOutputArtifacts> {

  public static RemoteOutputArtifacts EMPTY = new RemoteOutputArtifacts(ImmutableMap.of());

  public final ImmutableMap<String, RemoteOutputArtifact> remoteOutputArtifacts;

  private RemoteOutputArtifacts(ImmutableMap<String, RemoteOutputArtifact> remoteOutputArtifacts) {
    this.remoteOutputArtifacts = remoteOutputArtifacts;
  }

  @Override
  public ProjectData.RemoteOutputArtifacts toProto() {
    ProjectData.RemoteOutputArtifacts.Builder proto =
        ProjectData.RemoteOutputArtifacts.newBuilder();
    remoteOutputArtifacts.values().forEach(a -> proto.addArtifacts(a.toProto()));
    return proto.build();
  }

  public static RemoteOutputArtifacts fromProto(BuildSystemName buildSystemName, ProjectData.RemoteOutputArtifacts proto) {
    ImmutableMap.Builder<String, RemoteOutputArtifact> map = ImmutableMap.builder();
    proto.getArtifactsList().stream()
        .map(it -> RemoteOutputArtifact.fromProto(buildSystemName, it))
        .filter(Objects::nonNull)
        .forEach(a -> map.put(a.getBazelOutRelativePath(), a));
    return new RemoteOutputArtifacts(map.build());
  }

  public boolean isEmpty() {
    return remoteOutputArtifacts.isEmpty();
  }

  @Nullable
  public RemoteOutputArtifact findRemoteOutput(ArtifactLocation location) {
    if (location.isSource()) {
      return null;
    }
    String execRootPath = location.getExecutionRootRelativePath();
    if (!execRootPath.startsWith("blaze-out/")) {
      return null;
    }
    return findRemoteOutput(execRootPath.substring("blaze-out/".length()));
  }

  @Nullable
  public RemoteOutputArtifact findRemoteOutput(String blazeOutRelativePath) {
    // first try the exact path (forwards compatibility with a future BEP format)
    RemoteOutputArtifact file = remoteOutputArtifacts.get(blazeOutRelativePath);
    if (file != null) {
      return file;
    }
    return findAlternatePathFormat(blazeOutRelativePath);
  }

  private static final ImmutableSet<String> POSSIBLY_MISSING_PATH_COMPONENTS =
      ImmutableSet.of("bin", "genfiles", "testlogs");

  @Nullable
  private RemoteOutputArtifact findAlternatePathFormat(String path) {
    // temporary code until we can get the full blaze-out-relative path from BEP
    int index = path.indexOf('/');
    int nextIndex = path.indexOf('/', index + 1);
    if (nextIndex == -1) {
      return null;
    }
    String secondPathComponent = path.substring(index + 1, nextIndex);
    if (!POSSIBLY_MISSING_PATH_COMPONENTS.contains(secondPathComponent)) {
      return null;
    }
    String alternatePath =
        String.format("%s%s", path.substring(0, index), path.substring(nextIndex));
    return remoteOutputArtifacts.get(alternatePath);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    return Objects.equals(remoteOutputArtifacts, ((RemoteOutputArtifacts) o).remoteOutputArtifacts);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(remoteOutputArtifacts);
  }
}
