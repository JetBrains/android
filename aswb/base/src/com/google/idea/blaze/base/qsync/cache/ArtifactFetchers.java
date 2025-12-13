/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.qsync.cache;

import com.google.idea.blaze.common.artifact.ArtifactFetcher;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.common.artifact.ArtifactFetcher;
import com.google.idea.blaze.common.artifact.OutputArtifact;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Static utilities for use with {@link ArtifactFetcher} and implementations. */
public class ArtifactFetchers {

  private ArtifactFetchers() {}

  /**
   * Downloads an artifact to a temporary file.
   *
   * @return A buffered input stream of the temporary file, which will delete the file when closed.
   */
  public static BufferedInputStream downloadArtifact(
      OutputArtifact artifact, BlazeContext context, Project project) throws IOException {
    ArtifactFetcher<OutputArtifact> fetcher = project.getService(ArtifactFetcher.class);
    Path tempFile = Files.createTempFile("artifact", ".tmp");
    try {
      Futures.getChecked(
          fetcher.copy(
              ImmutableMap.of(
                  artifact,
                  new ArtifactFetcher.ArtifactDestination(tempFile)),
              context),
          IOException.class);
      return new BufferedInputStream(new FileInputStream(tempFile.toFile())) {
        @Override
        public void close() throws IOException {
          super.close();
          Files.deleteIfExists(tempFile);
        }
      };
    } catch (IOException e) {
      Files.deleteIfExists(tempFile);
      throw e;
    }
  }

  public static final ExtensionPointName<ArtifactFetcher<?>> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.qsync.ArtifactFetcher");
}
