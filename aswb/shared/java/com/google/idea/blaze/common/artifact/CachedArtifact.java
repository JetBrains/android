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
package com.google.idea.blaze.common.artifact;

import com.google.common.io.ByteSource;
import com.google.common.io.MoreFiles;
import com.google.errorprone.annotations.MustBeClosed;
import java.io.IOException;
import java.nio.file.Path;
import java.util.zip.ZipFile;

/** A build artifact that has been cached locally. Provides access to the artifacts contents. */
public class CachedArtifact {

  private final Path path;
  private final ByteSource byteSource;

  public CachedArtifact(Path path) {
    this.path = path;
    this.byteSource = MoreFiles.asByteSource(path);
  }

  public ByteSource byteSource() {
    return byteSource;
  }

  /**
   * Opens this artifact as a {@link ZipFile}. This is provided because not all zip files can be
   * opened from a {@link ByteSource}; a byte source only provides a stream, and the streaming Java
   * API for zip files cannot open all zip files; see https://stackoverflow.com/q/47208272 and JDK
   * bug https://bugs.openjdk.org/browse/JDK-8327690
   */
  @MustBeClosed
  public ZipFile openAsZipFile() throws IOException {
    return new ZipFile(path.toFile());
  }
}
