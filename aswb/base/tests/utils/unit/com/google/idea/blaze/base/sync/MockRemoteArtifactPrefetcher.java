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
package com.google.idea.blaze.base.sync;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.command.buildresult.RemoteOutputArtifact;
import com.google.idea.blaze.base.prefetch.RemoteArtifactPrefetcher;
import java.util.Collection;

/** Mock RemoteArtifactPrefetcher that return empty list for all requests. */
public class MockRemoteArtifactPrefetcher implements RemoteArtifactPrefetcher {
  @Override
  public ListenableFuture<?> loadFilesInJvm(Collection<RemoteOutputArtifact> outputArtifacts) {
    return Futures.immediateFuture(null);
  }

  @Override
  public ListenableFuture<?> downloadArtifacts(
      String projectName, Collection<RemoteOutputArtifact> outputArtifacts) {
    return Futures.immediateFuture(null);
  }

  @Override
  public ListenableFuture<?> cleanupLocalCacheDir(String projectName) {
    return Futures.immediateFuture(null);
  }
}
