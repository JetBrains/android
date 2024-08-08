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
package com.google.idea.blaze.base.sync.projectstructure;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.prefetch.FetchExecutor;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Directory structure representation used by {@link ContentEntryEditor}.
 *
 * <p>The purpose of this class is to pull out all file system operations out of the project
 * structure commit step, as this step locks the UI.
 */
public class DirectoryStructure {

  final ImmutableMap<WorkspacePath, DirectoryStructure> directories;

  private DirectoryStructure(ImmutableMap<WorkspacePath, DirectoryStructure> directories) {
    this.directories = directories;
  }

  public static ListenableFuture<DirectoryStructure> getRootDirectoryStructure(
      Project project, WorkspaceRoot workspaceRoot, ProjectViewSet projectViewSet) {
    AtomicBoolean cancelled = new AtomicBoolean(false);
    try {
      ListenableFuture<DirectoryStructure> future =
          FetchExecutor.EXECUTOR.submit(
              () ->
                  computeRootDirectoryStructure(project, workspaceRoot, projectViewSet, cancelled));
      future.addListener(() -> cancelled.set(true), MoreExecutors.directExecutor());
      return future;

    } catch (Throwable e) {
      cancelled.set(true);
      return Futures.immediateFailedFuture(e);
    }
  }

  private static DirectoryStructure computeRootDirectoryStructure(
      Project project,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      AtomicBoolean cancelled)
      throws ExecutionException, InterruptedException {
    FileOperationProvider fileOperationProvider = FileOperationProvider.getInstance();
    ImportRoots importRoots =
        ImportRoots.builder(workspaceRoot, Blaze.getBuildSystemName(project))
            .add(projectViewSet)
            .build();
    Collection<WorkspacePath> rootDirectories = importRoots.rootDirectories();
    Set<WorkspacePath> excludeDirectories = importRoots.excludeDirectories();
    List<ListenableFuture<PathStructurePair>> futures =
        Lists.newArrayListWithExpectedSize(rootDirectories.size());
    for (WorkspacePath rootDirectory : rootDirectories) {
      futures.add(
          walkDirectoryStructure(
              workspaceRoot,
              excludeDirectories,
              fileOperationProvider,
              FetchExecutor.EXECUTOR,
              rootDirectory,
              cancelled));
    }
    ImmutableMap.Builder<WorkspacePath, DirectoryStructure> result = ImmutableMap.builder();
    for (PathStructurePair pair : Futures.allAsList(futures).get()) {
      if (pair != null) {
        result.put(pair.path, pair.directoryStructure);
      }
    }
    return new DirectoryStructure(result.build());
  }

  private static ListenableFuture<PathStructurePair> walkDirectoryStructure(
      WorkspaceRoot workspaceRoot,
      Set<WorkspacePath> excludeDirectories,
      FileOperationProvider fileOperationProvider,
      ListeningExecutorService executorService,
      WorkspacePath workspacePath,
      AtomicBoolean cancelled) {
    if (cancelled.get() || excludeDirectories.contains(workspacePath)) {
      return Futures.immediateFuture(null);
    }
    File file = workspaceRoot.fileForPath(workspacePath);
    if (!fileOperationProvider.isDirectory(file)) {
      return Futures.immediateFuture(null);
    }
    ListenableFuture<File[]> childrenFuture =
        executorService.submit(() -> fileOperationProvider.listFiles(file));
    return Futures.transformAsync(
        childrenFuture,
        children -> {
          if (cancelled.get() || children == null) {
            return Futures.immediateFuture(null);
          }
          List<ListenableFuture<PathStructurePair>> futures =
              Lists.newArrayListWithExpectedSize(children.length);
          for (File child : children) {
            WorkspacePath childWorkspacePath;
            try {
              childWorkspacePath = workspaceRoot.workspacePathFor(child);
            } catch (IllegalArgumentException e) {
              // stop at directories with unhandled characters.
              continue;
            }
            futures.add(
                walkDirectoryStructure(
                    workspaceRoot,
                    excludeDirectories,
                    fileOperationProvider,
                    executorService,
                    childWorkspacePath,
                    cancelled));
          }
          return Futures.transform(
              Futures.allAsList(futures),
              (Function<List<PathStructurePair>, PathStructurePair>)
                  pairs -> {
                    Builder<WorkspacePath, DirectoryStructure> result = ImmutableMap.builder();
                    for (PathStructurePair pair : pairs) {
                      if (pair != null) {
                        result.put(pair.path, pair.directoryStructure);
                      }
                    }
                    return new PathStructurePair(
                        workspacePath, new DirectoryStructure(result.build()));
                  },
              executorService);
        },
        executorService);
  }

  private static class PathStructurePair {
    final WorkspacePath path;
    final DirectoryStructure directoryStructure;

    PathStructurePair(WorkspacePath path, DirectoryStructure directoryStructure) {
      this.path = path;
      this.directoryStructure = directoryStructure;
    }
  }
}
