/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.filecache;

import com.google.common.base.Preconditions;
import com.google.devtools.intellij.model.ProjectData.LocalFileOrOutputArtifact;
import com.google.idea.blaze.common.artifact.ArtifactState;
import com.intellij.openapi.extensions.ExtensionPointName;
import javax.annotation.Nullable;

/** Parser to convert {@link LocalFileOrOutputArtifact} to {@link ArtifactState} */
public interface ArtifactStateProtoConverter {
  ExtensionPointName<ArtifactStateProtoConverter> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.ArtifactStateHelper");

  /**
   * Convert {@link LocalFileOrOutputArtifact} to {@link ArtifactState}. Return null if cannot
   * convert.
   */
  @Nullable
  ArtifactState parseProto(LocalFileOrOutputArtifact proto);

  /**
   * Find first {@link ArtifactStateProtoConverter} that can convert {@link
   * LocalFileOrOutputArtifact} to {@link ArtifactState}. Return null if no one can convert.
   */
  @Nullable
  static ArtifactState fromProto(LocalFileOrOutputArtifact proto) {
    for (ArtifactStateProtoConverter artifactStateProtoConverter : EP_NAME.getExtensions()) {
      ArtifactState artifactState = artifactStateProtoConverter.parseProto(proto);
      if (artifactState != null) {
        return artifactState;
      }
    }
    return null;
  }

  static LocalFileOrOutputArtifact toProto(ArtifactState artifactState) {
    Preconditions.checkState(artifactState instanceof SerializableArtifactState);
    return ((SerializableArtifactState) artifactState).serializeToProto();
  }
}
