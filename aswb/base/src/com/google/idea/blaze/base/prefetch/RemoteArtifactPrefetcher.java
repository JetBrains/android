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
package com.google.idea.blaze.base.prefetch;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.command.buildresult.RemoteOutputArtifact;
import com.intellij.openapi.application.ApplicationManager;
import java.util.Collection;

/** A service for fetching a batch of remote files */
public interface RemoteArtifactPrefetcher {
  static RemoteArtifactPrefetcher getInstance() {
    return ApplicationManager.getApplication().getService(RemoteArtifactPrefetcher.class);
  }

  /**
   * Fetch file content for a list of {@link RemoteOutputArtifact}. Only load content into JVM
   * memory. Return {@link ListenableFuture} to indicate whether load completed/ any of them failed
   * with exception.
   */
  ListenableFuture<?> loadFilesInJvm(Collection<RemoteOutputArtifact> outputArtifacts);

  /**
   * Download for a list of {@link RemoteOutputArtifact} to local cache directory. Return {@link
   * ListenableFuture} to indicate whether download completed/ any of them failed with exception. If
   * projectName is provided, objfs files will be downloaded to projectName specific directory.
   */
  ListenableFuture<?> downloadArtifacts(
      String projectName, Collection<RemoteOutputArtifact> outputArtifacts);

  /** Clean up any file downloaded into local directory */
  ListenableFuture<?> cleanupLocalCacheDir(String projectName);
}
