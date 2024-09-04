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
package com.google.idea.blaze.qsync.java;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.Uninterruptibles;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

/** A {@link PackageReader} that parallelizes package reads of another {@link PackageReader}. */
public class ParallelPackageReader implements PackageReader {

  private final PackageReader reader;
  private final ListeningExecutorService executor;

  public ParallelPackageReader(ListeningExecutorService executor, PackageReader reader) {
    this.executor = executor;
    this.reader = reader;
  }

  @Override
  public String readPackage(Path path) throws IOException {
    return reader.readPackage(path);
  }

  @Override
  public List<String> readPackages(List<Path> paths) throws IOException {
    ArrayList<ListenableFuture<String>> futures = new ArrayList<>();
    for (Path path : paths) {
      futures.add(executor.submit(() -> readPackage(path)));
    }
    try {
      return Uninterruptibles.getUninterruptibly(Futures.allAsList(futures));
    } catch (ExecutionException e) {
      throw new IOException(e);
    }
  }
}
