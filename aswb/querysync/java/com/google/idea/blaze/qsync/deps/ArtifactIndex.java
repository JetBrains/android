/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync.deps;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.qsync.project.ProjectProto.ArtifactDirectories;
import com.google.idea.blaze.qsync.project.ProjectProto.ArtifactDirectoryContents;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * An index of project artifacts in the artifact store. Allows the lookup of which target built any
 * artifact in the store.
 *
 * @see com.google.idea.blaze.base.qsync.artifacts.ProjectArtifactStore
 */
@AutoValue
public abstract class ArtifactIndex {

  abstract ImmutableMap<Label, TargetBuildInfo> builtDepsMap();

  abstract ArtifactDirectories projectArtifacts();

  public static ArtifactIndex create(
      ArtifactTracker.State artifactState, ArtifactDirectories artifacts) {
    return new AutoValue_ArtifactIndex(artifactState.depsMap(), artifacts);
  }

  /**
   * Returns the map of owners of build artifacts in the project directory.
   *
   * @return a map of (project directory relative path) to (target that built it).
   */
  @Memoized
  public ImmutableMap<Path, Label> artifactOwnerMap() {
    ImmutableMap.Builder<Path, Label> map = ImmutableMap.builder();
    for (Map.Entry<String, ArtifactDirectoryContents> e :
        projectArtifacts().getDirectoriesMap().entrySet()) {
      Path root = Path.of(e.getKey());
      for (var content : e.getValue().getContentsMap().entrySet()) {
        map.put(root.resolve(Path.of(content.getKey())), Label.of(content.getValue().getTarget()));
      }
    }
    return map.buildOrThrow();
  }

  public Optional<JavaArtifactInfo> getInfoForArtifact(Path projectRelativePath) {
    return Optional.ofNullable(artifactOwnerMap().get(projectRelativePath))
        .map(builtDepsMap()::get)
        .flatMap(TargetBuildInfo::javaInfo);
  }
}
