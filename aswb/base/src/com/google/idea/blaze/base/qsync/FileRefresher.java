/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.qsync;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.SettableFuture;
import com.google.common.util.concurrent.Uninterruptibles;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.common.PrintOutput.OutputType;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.vfs.newvfs.RefreshSession;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;

/**
 * Refreshes files in the IDE efficiently.
 *
 * <p>Refreshes all files that have changed in just a few refresh sessions (typically one).
 */
public class FileRefresher {

  private static final Logger logger = Logger.getInstance(DependencyTrackerImpl.class);

  private final Project project;

  public FileRefresher(Project project) {
    this.project = project;
  }

  public void refreshFiles(Context<?> context, ImmutableSet<Path> updatedFiles) {
    ApplicationEx applicationEx = ApplicationManagerEx.getApplicationEx();
    //noinspection UnstableApiUsage
    applicationEx.assertIsNonDispatchThread();
    context.output(
        new PrintOutput(
            String.format("Refreshing virtual file system... (%d files)", updatedFiles.size())));
    markExistingFilesDirty(context, updatedFiles);
    ImmutableList.Builder<VirtualFile> virtualFiles = ImmutableList.builder();
    applicationEx.invokeAndWait(
        () -> {
          final boolean unused =
              applicationEx.runWriteActionWithNonCancellableProgressInDispatchThread(
                  "Finding build outputs",
                  project,
                  null,
                  indicator -> {
                    ProjectRootManagerEx.getInstanceEx(project)
                        .mergeRootsChangesDuring(
                            () -> {
                              // Finding a virtual file that is not yet in the VFS runs a refresh
                              // session and triggers virtual file system changed events. Having
                              // multiple changed events in the same project root, like currently
                              // .dependencies dependency is, causes inefficient O(n^2) project
                              // structure refreshing.
                              //
                              // Bring new files to the VFS by refreshing their parents only. Do
                              // refreshing in two stages: (1) find parents and (2) rescan and
                              // refresh them from a background thread (involves files changed
                              // events being fired in the EDT).
                              //
                              // Considering the current artifact directories are almost flat it is
                              // not more expensive than refreshing specific files only. This action
                              // needs to run in a write action as in rare cases (initialization or
                              // after some directories where manually deleted) some parents may
                              // need to be refreshed first and it might actually be expensive in
                              // the later case.
                              virtualFiles.addAll(
                                  getFileParentsAsVirtualFilesMarkedDirty(context, updatedFiles));
                            });
                  });
        });
    refreshFilesRecursively(virtualFiles.build());
    context.output(
        new PrintOutput(
            String.format(
                "Done refreshing virtual file system... (%d files)", updatedFiles.size())));
  }

  private static void refreshFilesRecursively(ImmutableList<VirtualFile> virtualFiles) {
    if (virtualFiles.isEmpty()) {
      return;
    }
    SettableFuture<Boolean> done = SettableFuture.create();
    try {
      RefreshSession refreshSession =
          RefreshQueue.getInstance().createSession(true, true, () -> done.set(true));
      refreshSession.addAllFiles(virtualFiles);
      refreshSession.launch();
      Uninterruptibles.getUninterruptibly(done);
    } catch (ExecutionException e) {
      throw new IllegalStateException(e);
    }
  }

  private static ImmutableList<VirtualFile> getFileParentsAsVirtualFilesMarkedDirty(
      Context<?> context, ImmutableSet<Path> updatedFiles) {
    final ImmutableList.Builder<VirtualFile> virtualFiles = ImmutableList.builder();
    ImmutableList<Path> paths =
        updatedFiles.stream()
            .map(Path::getParent)
            .distinct()
            .collect(ImmutableList.toImmutableList());
    for (final Path path : paths) {
      VirtualFile virtualFile = VfsUtil.findFileByIoFile(path.toFile(), true);
      if (virtualFile != null) {
        if (virtualFile instanceof NewVirtualFile) {
          ((NewVirtualFile) virtualFile).markDirty();
        } else {
          // This is unexpected. Send details back to us.
          logger.error(
              String.format("Unknown virtual file class %s for %s.", virtualFile.getClass(), path),
              new Throwable());
        }
        virtualFiles.add(virtualFile);
      } else {
        context.output(new PrintOutput("Cannot find: " + path, OutputType.ERROR));
      }
    }
    return virtualFiles.build();
  }

  /**
   * Marks any existing artifact files as dirty.
   *
   * <p>The virtual file system relies on file watchers to discover files that have changed. Those
   * that are known to have possibly changed are refreshed during virtual file system rescan
   * sessions, which are initiated by calls to `LocalFileSystem.refreshFiles` and similar.
   *
   * <p>Since file watchers are asynchronous it might happen that by this point the IDE does not yet
   * know that existing artifact files have changed. This method marks any existing files from
   * {@code updatedFiles} to make sure that later refreshing of the virtual file system rescans
   * existing files.
   */
  private static void markExistingFilesDirty(Context<?> context, ImmutableSet<Path> updatedFiles) {
    int markedAsDirty = 0;
    for (final Path path : updatedFiles) {
      VirtualFile virtualFile = getFileByIoFileIfInVfs(path);
      if (virtualFile != null) {
        if (virtualFile instanceof NewVirtualFile) {
          ((NewVirtualFile) virtualFile).markDirty();
          markedAsDirty++;
        } else {
          // This is unexpected. Send details back to us.
          logger.error(
              String.format("Unknown virtual file class %s for %s.", virtualFile.getClass(), path),
              new Throwable());
        }
      }
    }
    context.output(
        new PrintOutput(String.format("%d existing files require refreshing...", markedAsDirty)));
  }

  /**
   * Returns a virtual file by its IO path if it is already known by the VFS.
   *
   * <p>This method does not attempt to bring to the VFS files that are not yet there and thus does
   * not cause any VFS level file-change events and does not need to run in a write action.
   */
  private static VirtualFile getFileByIoFileIfInVfs(Path path) {
    return VfsUtil.findFileByIoFile(path.toFile(), false /* refreshIfNeeded */);
  }
}
