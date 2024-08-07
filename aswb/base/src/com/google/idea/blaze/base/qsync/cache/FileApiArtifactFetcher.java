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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.command.buildresult.LocalFileOutputArtifact;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.artifact.ArtifactFetcher;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map.Entry;

/** Implementation of {@link ArtifactFetcher} that copy file via file api. */
public class FileApiArtifactFetcher implements ArtifactFetcher<LocalFileOutputArtifact> {
  @Override
  public ListenableFuture<?> copy(
      ImmutableMap<? extends LocalFileOutputArtifact, ArtifactDestination> artifactToDest,
      Context<?> context) {
    ImmutableList.Builder<ListenableFuture<Path>> tasks = ImmutableList.builder();
    for (Entry<? extends LocalFileOutputArtifact, ArtifactDestination> entry :
        artifactToDest.entrySet()) {
      tasks.add(
          ArtifactFetchers.EXECUTOR.submit(
              () -> {
                Path dest = entry.getValue().path;
                LocalFileOutputArtifact localFileOutputArtifact = entry.getKey();
                if (Files.exists(dest) && Files.isDirectory(dest)) {
                  FileOperationProvider.getInstance().deleteRecursively(dest.toFile(), true);
                }
                Files.copy(
                    Paths.get(localFileOutputArtifact.getFile().getPath()),
                    dest,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.COPY_ATTRIBUTES);
                return dest;
              }));
    }
    return Futures.allAsList(tasks.build());
  }

  @Override
  public Class<LocalFileOutputArtifact> supportedArtifactType() {
    return LocalFileOutputArtifact.class;
  }
}
