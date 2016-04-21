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
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.ActionCallback;
import net.jcip.annotations.NotThreadSafe;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.Future;

@NotThreadSafe
public class ArtifactRepositorySearch {
  @NotNull private final List<ArtifactRepository> myRepositories;

  public ArtifactRepositorySearch(@NotNull List<ArtifactRepository> repositories) {
    myRepositories = repositories;
  }

  @NotNull
  public Callback start(@NotNull SearchRequest request) {
    Callback callback = new Callback();

    List<Future<SearchResult>> jobs = Lists.newArrayListWithExpectedSize(myRepositories.size());
    List<SearchResult> results = Lists.newArrayList();
    List<Exception> errors = Lists.newArrayList();

    Application application = ApplicationManager.getApplication();
    application.executeOnPooledThread(() -> {
      for (ArtifactRepository repository : myRepositories) {
        jobs.add(application.executeOnPooledThread(() -> repository.search(request)));
      }

      for (Future<SearchResult> job : jobs) {
        try {
          results.add(Futures.get(job, Exception.class));
        }
        catch (Exception e) {
          errors.add(e);
        }
      }
      callback.setDone(results, errors);
    });
    return callback;
  }

  public static class Callback extends ActionCallback {
    @NotNull private final List<SearchResult> mySearchResults = Lists.newArrayList();
    @NotNull private final List<Exception> myErrors = Lists.newArrayList();

    void setDone(@NotNull List<SearchResult> searchResults, @NotNull List<Exception> errors) {
      searchResults.forEach(searchResult -> {
        Exception error = searchResult.getError();
        if (error != null) {
          myErrors.add(error);
          return;
        }
        mySearchResults.add(searchResult);
      });
      myErrors.addAll(errors);
      setDone();
    }

    @NotNull
    public List<SearchResult> getSearchResults() {
      checkIsDone();
      return mySearchResults;
    }

    @NotNull
    public List<Exception> getErrors() {
      checkIsDone();
      return myErrors;
    }

    private void checkIsDone() {
      if (!isDone()) {
        throw new IllegalStateException("Repository search has not finished yet");
      }
    }
  }
}
