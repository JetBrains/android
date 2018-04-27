/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.structure.model.repositories.search;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import net.jcip.annotations.NotThreadSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.ide.PooledThreadExecutor;

import java.util.List;
import java.util.concurrent.Future;

@NotThreadSafe
public class ArtifactRepositorySearch implements ArtifactRepositorySearchService {
  @NotNull private final List<ArtifactRepository> myRepositories;
  @NotNull ListeningExecutorService executor = MoreExecutors.listeningDecorator(PooledThreadExecutor.INSTANCE);

  public ArtifactRepositorySearch(@NotNull List<ArtifactRepository> repositories) {
    myRepositories = repositories;
  }

  @Override
  @NotNull
  public ListenableFuture<ArtifactRepositorySearchResults> search(@NotNull SearchRequest request) {
    List<ListenableFuture<SearchResult>> jobs = Lists.newArrayListWithExpectedSize(myRepositories.size());
    List<SearchResult> results = Lists.newArrayList();
    List<Exception> errors = Lists.newArrayList();

    for (ArtifactRepository repository : myRepositories) {
      jobs.add(executor.submit(() -> repository.search(request)));
    }
    return Futures.whenAllComplete(jobs).call(
      () -> {
        for (Future<SearchResult> job : jobs) {
          try {
            results.add(Futures.getChecked(job, Exception.class));
          }
          catch (Exception e) {
            errors.add(e);
          }
        }
        return new ArtifactRepositorySearchResults(results, errors);
      }
    );
  }
}
