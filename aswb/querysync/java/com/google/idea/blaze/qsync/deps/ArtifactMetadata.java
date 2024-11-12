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

import com.google.idea.blaze.common.artifact.CachedArtifact;
import com.google.idea.blaze.exception.BuildException;

/**
 * Metadata that can be extracted from a build artifact.
 *
 * <p>This is used to derive information from a build artifact that is required by a {@link
 * com.google.idea.blaze.qsync.ProjectProtoTransform}. Projects transforms cannot directly access
 * build artifacts from {@link com.google.idea.blaze.common.artifact.BuildArtifactCache} since there
 * is no guarantee that such artifacts will always be present when it is run. Instead, a project
 * proto transform can supply the identities of build artifacts together with the metadata to be
 * extracted (in the form of instances of this interface), to ensure that the derived information
 * is always available at project proto transform application time.
 */
public interface ArtifactMetadata {

  /**
   * A unique key for this transform. This key is persisted in the artifact tracker state so must
   * not change.
   *
   * <p>This Key must not contain a ":".
   */
  String key();

  /**
   * Extracts this metadata from a build artifact.
   *
   * @param buildArtifact An artifact from the build cache.
   * @param nameForLogs Provides a {@code toString} implementation to provide useful context
   *     about {@code buildArtifact} in IDE logs.
   * @return The extracted metadata. The result must be small enough to be stored inside the project
   *     proto, in the order of hundreds of bytes or less.
   */
  String extract(CachedArtifact buildArtifact, Object nameForLogs) throws BuildException;
}
