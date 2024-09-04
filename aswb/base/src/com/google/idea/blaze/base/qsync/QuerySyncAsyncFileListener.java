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
import com.google.idea.blaze.base.logging.utils.querysync.QuerySyncActionStatsScope;
import com.google.idea.blaze.base.qsync.QuerySyncManager.TaskOrigin;
import com.google.idea.blaze.base.qsync.settings.QuerySyncSettings;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.SyncListener;
import com.google.idea.blaze.base.sync.status.BlazeSyncStatus;
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
  private final SyncRequester syncRequester;

  private final AtomicBoolean hasDirtyBuildFiles = new AtomicBoolean(false);

  @VisibleForTesting
  public QuerySyncAsyncFileListener(Project project, SyncRequester syncRequester) {
    this.project = project;
    this.syncRequester = syncRequester;
  }

  /** Returns true if {@code absolutePath} is in a directory included by the project. */
  public boolean isPathIncludedInProject(Path absolutePath) {
    return QuerySyncManager.getInstance(project)
        .getLoadedProject()
        .map(p -> p.containsPath(absolutePath))
        .orElse(false);
  }

  /** Returns true if the listener should request a project sync on significant changes */
  public boolean syncOnFileChanges() {
    return QuerySyncSettings.getInstance().syncOnFileChanges();
  }

  private static QuerySyncAsyncFileListener create(Project project, Disposable parentDisposable) {
    SyncRequester syncRequester = QueueingSyncRequester.create(project, parentDisposable);
    return new QuerySyncAsyncFileListener(project, syncRequester);
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

                    if (syncOnFileChanges()) {
                      syncRequester.requestSync();
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
   * {link @SyncRequester} that can listen to sync events and request a sync later if changes are
   * added during a sync.
   */
  private static class QueueingSyncRequester implements SyncRequester {
    private final Project project;

    private final AtomicBoolean changePending = new AtomicBoolean(false);

    public QueueingSyncRequester(Project project) {
      this.project = project;
    }

    static QueueingSyncRequester create(Project project, Disposable parentDisposable) {
      QueueingSyncRequester requester = new QueueingSyncRequester(project);
      ApplicationManager.getApplication()
          .getExtensionArea()
          .getExtensionPoint(SyncListener.EP_NAME)
          .registerExtension(
              new SyncListener() {
                @Override
                public void afterQuerySync(Project project, BlazeContext context) {
                  if (!requester.project.equals(project)) {
                    return;
                  }
                  if (requester.changePending.get()) {
                    requester.requestSyncInternal();
                  }
                }
              },
              parentDisposable);
      return requester;
    }

    @Override
    public void requestSync() {
      if (changePending.compareAndSet(false, true)) {
        if (!BlazeSyncStatus.getInstance(project).syncInProgress()) {
          requestSyncInternal();
        }
      }
    }

    private void requestSyncInternal() {
      QuerySyncManager.getInstance(project)
          .deltaSync(
              QuerySyncActionStatsScope.create(QuerySyncAsyncFileListener.class, null),
              TaskOrigin.AUTOMATIC);
      changePending.set(false);
    }
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
