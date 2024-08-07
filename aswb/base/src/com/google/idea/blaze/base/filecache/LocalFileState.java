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
package com.google.idea.blaze.base.filecache;

import com.google.devtools.intellij.model.ProjectData.LocalFile;
import com.google.devtools.intellij.model.ProjectData.LocalFileOrOutputArtifact;
import com.google.idea.blaze.common.artifact.ArtifactState;

/** Serialization state related to local files. */
public class LocalFileState implements ArtifactState, SerializableArtifactState {
  private final String blazeOutPath;
  private final long timestamp;

  public LocalFileState(LocalFile localFile) {
    this.blazeOutPath =
        !localFile.getRelativePath().isEmpty() ? localFile.getRelativePath() : localFile.getPath();
    this.timestamp = localFile.getTimestamp();
  }

  public LocalFileState(String blazeOutPath, long timestamp) {
    this.blazeOutPath = blazeOutPath;
    this.timestamp = timestamp;
  }

  @Override
  public String getKey() {
    return blazeOutPath;
  }

  @Override
  public boolean isMoreRecent(ArtifactState output) {
    return !(output instanceof LocalFileState) || timestamp < ((LocalFileState) output).timestamp;
  }

  @Override
  public LocalFileOrOutputArtifact serializeToProto() {
    return LocalFileOrOutputArtifact.newBuilder()
        .setLocalFile(LocalFile.newBuilder().setRelativePath(blazeOutPath).setTimestamp(timestamp))
        .build();
  }

  @Override
  public int hashCode() {
    return blazeOutPath.hashCode();
  }

  /**
   * Returns true for {@link LocalFileState} with the same key, as described in {@link #getKey()}
   * See {@link ArtifactState#getKey()} for caveats abouts versioning.
   */
  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof LocalFileState)) {
      return false;
    }
    return blazeOutPath.equals(((LocalFileState) obj).blazeOutPath);
  }
}
