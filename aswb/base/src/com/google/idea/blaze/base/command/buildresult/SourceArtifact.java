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

import com.google.errorprone.annotations.MustBeClosed;
import com.intellij.openapi.util.io.FileUtil;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/** A source file in the blaze workspace, accessible on the local file system. */
public class SourceArtifact implements LocalFileArtifact {

  private final File file;

  public SourceArtifact(File file) {
    this.file = file;
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
    if (!(obj instanceof SourceArtifact)) {
      return false;
    }
    return FileUtil.filesEqual(file, ((SourceArtifact) obj).file);
  }

  @Override
  public int hashCode() {
    return FileUtil.fileHashCode(file);
  }

  @Override
  public String toString() {
    return file.getPath();
  }
}
