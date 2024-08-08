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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.common.artifact.BlazeArtifact;
import java.io.File;
import java.util.Collection;

/** A file artifact available on the local file system. */
public interface LocalFileArtifact extends BlazeArtifact {

  /**
   * Filters out non-local artifacts.
   *
   * <p>Some callers will only ever accept local outputs (e.g. when debugging, and making use of
   * runfiles directories).
   */
  static ImmutableList<File> getLocalFiles(Collection<? extends BlazeArtifact> artifacts) {
    return artifacts.stream()
        .filter(a -> a instanceof LocalFileArtifact)
        .map(a -> ((LocalFileArtifact) a).getFile())
        .collect(toImmutableList());
  }

  File getFile();

  @Override
  default long getLength() {
    return FileOperationProvider.getInstance().getFileSize(getFile());
  }
}
