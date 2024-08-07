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

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.command.buildresult.RemoteOutputArtifact;
import com.intellij.openapi.components.ServiceManager;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Implementation of {@link RemoteArtifactPrefetcher}. By default, IDE does not download any
 * artifacts to local directory.
 */
public class DefaultPrefetcher implements RemoteArtifactPrefetcher {
  @Override
  public ListenableFuture<?> loadFilesInJvm(Collection<RemoteOutputArtifact> outputArtifacts) {
    return DefaultPrefetcherDelegator.loadFilesInJvm(outputArtifacts);
  }

  @Override
  public ListenableFuture<?> downloadArtifacts(
      String projectName, Collection<RemoteOutputArtifact> outputArtifacts) {
    return DefaultPrefetcherDelegator.downloadArtifacts(projectName, outputArtifacts);
  }

  @Override
  public ListenableFuture<?> cleanupLocalCacheDir(String projectName) {
    return DefaultPrefetcherDelegator.cleanupLocalCacheDir(projectName);
  }

  /**
   * Provide access to functions of {@link DefaultPrefetcher} even it's not registered in {@link
   * ServiceManager}.
   */
  public static class DefaultPrefetcherDelegator {
    public static ListenableFuture<?> loadFilesInJvm(
        Collection<RemoteOutputArtifact> outputArtifacts) {
      List<ListenableFuture<?>> futures = new ArrayList<>();
      for (RemoteOutputArtifact remoteOutputArtifact : outputArtifacts) {
        futures.add(FetchExecutor.EXECUTOR.submit(remoteOutputArtifact::prefetch));
      }
      return Futures.allAsList(futures);
    }

    public static ListenableFuture<?> downloadArtifacts(
        String projectName, Collection<RemoteOutputArtifact> outputArtifacts) {
      return Futures.immediateFuture(null);
    }

    public static ListenableFuture<?> cleanupLocalCacheDir(String projectName) {
      return Futures.immediateFuture(null);
    }

    private DefaultPrefetcherDelegator() {}
  }
}
