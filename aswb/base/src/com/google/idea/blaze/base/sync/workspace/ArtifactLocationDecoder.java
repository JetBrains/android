/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync.workspace;

import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.common.artifact.BlazeArtifact;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** Decodes intellij_ide_info.proto ArtifactLocation file paths */
public interface ArtifactLocationDecoder {

  /** @deprecated use {@link BlazeArtifact} instead of {@link File}. */
  @Deprecated
  File decode(ArtifactLocation artifactLocation);

  /**
   * Returns the local file associated with this {@link ArtifactLocation}, or null if it's not a
   * main workspace source artifact.
   */
  @Nullable
  File resolveSource(ArtifactLocation artifact);

  /** Returns a {@link BlazeArtifact} corresponding to the given {@link ArtifactLocation}. */
  BlazeArtifact resolveOutput(ArtifactLocation artifact);

  default List<BlazeArtifact> resolveOutputs(Collection<ArtifactLocation> artifactLocations) {
    return artifactLocations.stream()
        .map(this::resolveOutput)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  default List<File> decodeAll(Collection<ArtifactLocation> artifactLocations) {
    return artifactLocations.stream().map(this::decode).collect(Collectors.toList());
  }
}
