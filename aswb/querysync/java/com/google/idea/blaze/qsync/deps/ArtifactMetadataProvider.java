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

import com.google.common.collect.ImmutableSetMultimap;
import com.google.idea.blaze.qsync.artifacts.BuildArtifact;

/**
 * For a given {@link TargetBuildInfo}, provides the set of artifact metadata that are needed by
 * later stages of sync or build dependencies.
 *
 * <p>This interface us used during a dependency build, immediately after the dependency build is
 * complete. Any required metadata is immediately extracted from the build artifacts when the are
 * available, and then persisted in the {@link ArtifactTracker} for the lifetime of the {@link
 * TargetBuildInfo} itself.
 */
public interface ArtifactMetadataProvider {

  /**
   * Indicates which metadata are needed for the given target.
   *
   * @param forTarget The target in question, which has just been built.
   * @return A map of build artifacts to required metadata types. The keys in this map must
   *     correspond to build artifacts from {@code forTarget}.
   */
  ImmutableSetMultimap<BuildArtifact, ArtifactMetadata> getRequiredArtifactMetadata(
      TargetBuildInfo forTarget);
}
