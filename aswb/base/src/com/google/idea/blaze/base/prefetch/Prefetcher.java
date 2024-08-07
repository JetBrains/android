/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
import com.google.common.util.concurrent.ListeningExecutorService;
import com.intellij.openapi.extensions.ExtensionPointName;
import java.io.File;
import java.util.Collection;
import java.util.Set;

/** Prefetches files when a project is opened or roots change. */
public interface Prefetcher {
  ExtensionPointName<Prefetcher> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.Prefetcher");

  /**
   * Prefetches the given list of canonical files.
   *
   * <p>When listing individual files, it is the responsibility of the caller to filter out any
   * files it isn't interested in. When listing directories, one can supply a set of
   * excludedDirectories that should not be walked down recursively during prefetch.
   */
  ListenableFuture<PrefetchStats> prefetchFiles(
      Set<File> excludedDirectories,
      Collection<ListenableFuture<File>> fileFutures,
      ListeningExecutorService executor,
      boolean fetchFileTypes);
}
