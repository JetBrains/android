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
package com.google.idea.blaze.base.command.buildresult;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.errorprone.annotations.MustBeClosed;
import com.google.idea.blaze.base.filecache.LocalFileState;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.common.artifact.ArtifactState;
import com.google.idea.blaze.common.artifact.OutputArtifactWithoutDigest;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import javax.annotation.Nullable;

/** A blaze output artifact which exists on the local file system. */
public class LocalFileOutputArtifactWithoutDigest
    implements OutputArtifactWithoutDigest, LocalFileArtifact {

  private final File file;
  private final Path artifactPath;
  private final String configurationMnemonic;

  public LocalFileOutputArtifactWithoutDigest(
    File file, Path artifactPath, String configurationMnemonic) {
    this.file = file;
    this.artifactPath = artifactPath;
    this.configurationMnemonic = configurationMnemonic;
  }

  private long getLastModifiedTime() {
    return FileOperationProvider.getInstance().getFileModifiedTime(file);
  }

  @Override
  @Nullable
  public ArtifactState toArtifactState() {
    long lastModifiedTime = getLastModifiedTime();
    return lastModifiedTime == 0 ? null : new LocalFileState(getBazelOutRelativePath(), lastModifiedTime);
  }

  @Override
  public String getConfigurationMnemonic() {
    return configurationMnemonic;
  }

  @Override
  public Path getArtifactPath() {
    return artifactPath;
  }

  @Override
  @MustBeClosed
  public BufferedInputStream getInputStream() throws IOException {
    return new BufferedInputStream(new FileInputStream(file));
  }

  @Override
  public File getFile() {
    return file;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof LocalFileOutputArtifactWithoutDigest)) {
      return false;
    }
    LocalFileOutputArtifactWithoutDigest that = (LocalFileOutputArtifactWithoutDigest) obj;
    return Objects.equal(this.file.getPath(), that.file.getPath())
        && Objects.equal(this.artifactPath, that.artifactPath);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(file.getPath(), artifactPath);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("file", file.getPath())
        .add("artifactPath", artifactPath)
        .add("configurationMnemonic", configurationMnemonic)
        .toString();
  }
}
