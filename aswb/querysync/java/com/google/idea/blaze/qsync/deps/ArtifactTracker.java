/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.exception.BuildException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/** A local cache of project dependencies. */
public interface ArtifactTracker<ContextT extends Context<?>> {

  /** Immutable artifact state at a point in time. */
  @AutoValue
  abstract class State {

    public static final State EMPTY = create(ImmutableMap.of(), ImmutableMap.of());

    public abstract ImmutableMap<Label, TargetBuildInfo> depsMap();

    public abstract ImmutableMap<String, CcToolchain> ccToolchainMap();

    public static State create(
        ImmutableMap<Label, TargetBuildInfo> map,
        ImmutableMap<String, CcToolchain> ccToolchainMap) {
      return new AutoValue_ArtifactTracker_State(map, ccToolchainMap);
    }

    @VisibleForTesting
    public static State forJavaArtifacts(ImmutableCollection<JavaArtifactInfo> infos) {
      return create(
          infos.stream()
              .collect(
                  toImmutableMap(
                      JavaArtifactInfo::label,
                      j -> TargetBuildInfo.forJavaTarget(j, DependencyBuildContext.NONE))),
          ImmutableMap.of());
    }

    @VisibleForTesting
    public static State forJavaArtifacts(JavaArtifactInfo... infos) {
      return create(
          Stream.of(infos)
              .collect(
                  toImmutableMap(
                      JavaArtifactInfo::label,
                      j -> TargetBuildInfo.forJavaTarget(j, DependencyBuildContext.NONE))),
          ImmutableMap.of());
    }

    @VisibleForTesting
    public static State forJavaLabels(Label... labels) {
      return forJavaArtifacts(
          Stream.of(labels).map(JavaArtifactInfo::empty).collect(ImmutableSet.toImmutableSet()));
    }

    @VisibleForTesting
    public static State forJavaLabels(Collection<Label> labels) {
      return forJavaArtifacts(
          labels.stream().map(JavaArtifactInfo::empty).collect(ImmutableSet.toImmutableSet()));
    }
  }

  /** Drops all artifacts. */
  void clear() throws IOException;

  /** Fetches, caches and sets up new artifacts. */
  void update(Set<Label> targets, OutputInfo outputInfo, ContextT context) throws BuildException;

  State getStateSnapshot();

  /**
   * Returns a list of local cache files that build by target provided. Returns Optional.empty() if
   * the target has not yet been built.
   */
  Optional<ImmutableSet<Path>> getCachedFiles(Label target);

  Iterable<Path> getBugreportFiles();
}
