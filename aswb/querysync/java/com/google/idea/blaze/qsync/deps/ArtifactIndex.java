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

import static autovalue.shaded.com.google.common.collect.ImmutableList.toImmutableList;

import autovalue.shaded.com.google.common.collect.ImmutableList;
import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.qsync.artifacts.BuildArtifact;
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

  public static ArtifactIndex create(
      ArtifactTracker.State artifactState) {
    return new AutoValue_ArtifactIndex(artifactState.depsMap());
  }

  /**
   * Returns the map of owners of build artifacts in the project directory.
   *
   * @return a map of (project directory relative path) to (target that built it).
   */
  @Memoized
  public ImmutableMultimap<Path, Label> jarOwnerMap() {
    ImmutableMultimap.Builder<Path, Label> map = ImmutableMultimap.builder();
    for (Map.Entry<Label, TargetBuildInfo> labelTargetBuildInfoEntry : builtDepsMap().entrySet()) {
      final var label = labelTargetBuildInfoEntry.getKey();
      labelTargetBuildInfoEntry.getValue().javaInfo().map(JavaArtifactInfo::jars).orElse(ImmutableSet.of()).stream().map(
        BuildArtifact::artifactPath).forEach(path -> map.put(path, label));
    }
    return map.build();
  }

  public ImmutableList<JavaArtifactInfo> getInfoForJarArtifact(Path projectRelativePath) {
    final var javaDepsPrefixPath = com.google.idea.blaze.qsync.deps.ArtifactDirectories.JAVADEPS.relativePath();
    if (!projectRelativePath.startsWith(javaDepsPrefixPath)) {
      return ImmutableList.of();
    }
    Path artifactPath = projectRelativePath.subpath(javaDepsPrefixPath.getNameCount(), projectRelativePath.getNameCount());
    final var labels = jarOwnerMap().get(artifactPath);
    return labels.stream().flatMap(it -> Optional.ofNullable(builtDepsMap().get(it)).flatMap(
      TargetBuildInfo::javaInfo).stream()).collect(toImmutableList());
  }
}
