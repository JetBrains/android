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
package com.google.idea.blaze.qsync.artifacts;

import com.google.idea.blaze.common.artifact.CachedArtifact;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.java.ArtifactTrackerProto.Metadata;
import javax.annotation.Nullable;

/**
 * Metadata associated with a {@link BuildArtifact}.
 *
 * <p>Metadata is used to derive information from a build artifact that is required by a {@link
 * com.google.idea.blaze.qsync.ProjectProtoTransform}. Projects transforms cannot directly access
 * build artifacts from {@link com.google.idea.blaze.common.artifact.BuildArtifactCache} since there
 * is no guarantee that such artifacts will always be present when it is run. Instead, a project
 * proto transform can supply the identities of build artifacts together with the a {@link
 * Extractor} to extract metadata, to ensure that the derived information is always available at
 * project proto transform application time.
 */
public interface ArtifactMetadata {

  /** Convert this metadata to a proto for storing within the artifatc tracker state. */
  Metadata toProto();

  /** Extracts metadata from a build artifact. */
  interface Extractor<T extends ArtifactMetadata> {

    /**
     * @param buildArtifact An artifact from the build cache.
     * @param nameForLogs Provides a {@code toString} implementation to provide useful context about
     *     {@code buildArtifact} in IDE logs.
     */
    T extractFrom(CachedArtifact buildArtifact, Object nameForLogs) throws BuildException;

    Class<T> metadataClass();
  }

  /**
   * Converts a metadata proto back to its internal representation.
   *
   * <p>This performs the inverse operation to {@link #toProto()}.
   */
  interface Factory {

    @Nullable
    ArtifactMetadata create(Metadata fromProto);

    static Factory compound(Factory... factories) {
      return proto -> {
        for (Factory f : factories) {
          ArtifactMetadata md = f.create(proto);
          if (md != null) {
            return md;
          }
        }
        return null;
      };
    }
  }
}
