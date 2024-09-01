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
package com.google.idea.blaze.base.sync;

import com.google.idea.blaze.base.command.buildresult.RemoteOutputArtifact;
import com.google.idea.blaze.common.artifact.ArtifactState;
import com.intellij.openapi.util.io.FileUtil;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import org.jetbrains.annotations.Nullable;

/** Use local file to fake {@link RemoteOutputArtifact} that used by tests. */
public class FakeRemoteOutputArtifact implements RemoteOutputArtifact {
  private final File file;

  public FakeRemoteOutputArtifact(File file) {
    this.file = file;
  }

  @Override
  public long getLength() {
    return this.file.length();
  }

  @Override
  public BufferedInputStream getInputStream() throws IOException {
    return new BufferedInputStream(new FileInputStream(file));
  }

  @Override
  public String getConfigurationMnemonic() {
    return "";
  }

  @Override
  public String getBazelOutRelativePath() {
    return file.getPath();
  }

  @Nullable
  @Override
  public ArtifactState toArtifactState() {
    return null;
  }

  @Override
  public void prefetch() {}

  @Override
  public String getHashId() {
    return String.valueOf(FileUtil.fileHashCode(file));
  }

  @Override
  public long getSyncTimeMillis() {
    return 0;
  }

  @Override
  public String getDigest() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int hashCode() {
    return file.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    return obj instanceof FakeRemoteOutputArtifact
           && getBazelOutRelativePath().equals(((FakeRemoteOutputArtifact) obj).getBazelOutRelativePath());
  }
}
