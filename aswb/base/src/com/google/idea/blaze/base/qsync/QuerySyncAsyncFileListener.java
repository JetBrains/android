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
package com.google.idea.blaze.base.qsync;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.lang.buildfile.language.BuildFileType;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.SyncListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.AsyncFileListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.ui.EditorNotifications;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;

/** {@link AsyncFileListener} for monitoring project changes requiring a re-sync */
public class QuerySyncAsyncFileListener implements AsyncFileListener {

  private final Project project;

  private final AtomicBoolean hasDirtyBuildFiles = new AtomicBoolean(false);

  @VisibleForTesting
  public QuerySyncAsyncFileListener(Project project) {
    this.project = project;
  }

  /** Returns true if {@code absolutePath} is in a directory included by the project. */
  public boolean isPathIncludedInProject(Path absolutePath) {
    return QuerySyncManager.getInstance(project)
        .getLoadedProject()
        .map(p -> p.containsPath(absolutePath))
        .orElse(false);
  }

  private static QuerySyncAsyncFileListener create(Project project, Disposable parentDisposable) {
    return new QuerySyncAsyncFileListener(project);
  }

  public static QuerySyncAsyncFileListener createAndListen(
      Project project, Disposable parentDisposable) {
    QuerySyncAsyncFileListener fileListener = create(project, parentDisposable);
    VirtualFileManager.getInstance().addAsyncFileListener(fileListener, parentDisposable);
    return fileListener;
  }

  public boolean hasModifiedBuildFiles() {
    return hasDirtyBuildFiles.get();
  }

  public void clearState() {
    hasDirtyBuildFiles.set(false);
  }

  @Override
  @Nullable
  public ChangeApplier prepareChange(List<? extends VFileEvent> events) {

    ImmutableList<? extends VFileEvent> eventsRequiringSync =
        events.stream().filter(this::requiresSync).collect(toImmutableList());

    if (!eventsRequiringSync.isEmpty()) {
      boolean buildFileModified =
          eventsRequiringSync.stream()
              .anyMatch(
                  e ->
                      Optional.ofNullable(e.getFile())
                          .map(vf -> vf.getFileType() == BuildFileType.INSTANCE)
                          .orElse(false));

      return new ChangeApplier() {
        @Override
        public void afterVfsChange() {
          ApplicationManager.getApplication()
              .invokeLater(
                  () -> {
                    if (UnsyncedFileEditorNotificationProvider.NOTIFY_ON_BUILD_FILE_CHANGES
                            .getValue()
                        && buildFileModified) {
                      hasDirtyBuildFiles.set(true);
                    }

                    EditorNotifications.getInstance(project).updateAllNotifications();
                  });
        }
      };
    }
    return null;
  }

  private boolean requiresSync(VFileEvent event) {
    if (!isPathIncludedInProject(Path.of(event.getPath()))) {
      return false;
    }
    if (event instanceof VFileCreateEvent || event instanceof VFileMoveEvent) {
      return true;
    } else if (event instanceof VFilePropertyChangeEvent
        && ((VFilePropertyChangeEvent) event).getPropertyName().equals("name")) {
      return true;
    }

    VirtualFile vf = event.getFile();
    if (vf == null) {
      return false;
    }

    if (vf.getFileType() instanceof BuildFileType) {
      return true;
    }

    return false;
  }

  /** Interface for requesting project syncs. */
  public interface SyncRequester {
    void requestSync();
  }

  /**
   * {@link com.google.idea.blaze.base.sync.SyncListener} for clearing file listener state on syncs
   */
  public static class QuerySyncListener implements SyncListener {

    @Override
    public void onQuerySyncStart(Project project, BlazeContext context) {
      QuerySyncAsyncFileListener fileListener =
          QuerySyncManager.getInstance(project).getFileListener();
      fileListener.clearState();
    }
  }
}
