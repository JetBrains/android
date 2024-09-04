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

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.Collection;
import javax.annotation.Nullable;

/** Mocks the prefetch service. */
public class MockPrefetchService implements PrefetchService {

  @Override
  public ListenableFuture<PrefetchStats> prefetchFiles(
      Collection<File> files, boolean refetchCachedFiles, boolean fetchFileTypes) {
    return Futures.immediateFuture(PrefetchStats.NONE);
  }

  @Override
  public ListenableFuture<PrefetchStats> prefetchProjectFiles(
      Project project, ProjectViewSet projectViewSet, @Nullable BlazeProjectData blazeProjectData) {
    return Futures.immediateFuture(PrefetchStats.NONE);
  }

  @Override
  public void clearPrefetchCache() {}
}
