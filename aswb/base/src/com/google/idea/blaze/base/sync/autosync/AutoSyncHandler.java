/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync.autosync;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.logging.EventLoggingService;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.base.sync.BlazeSyncManager;
import com.google.idea.blaze.base.sync.BlazeSyncParams;
import com.google.idea.blaze.base.sync.SyncListener;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.status.BlazeSyncStatus;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerAdapter;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileMoveEvent;
import com.intellij.openapi.vfs.VirtualFilePropertyEvent;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Listens for changes to files in the current project, and both updates the sync 'dirty' status and
 * kicks off automatic syncs in response, where appropriate.
 */
public class AutoSyncHandler implements ProjectComponent {
  private static final Logger logger = Logger.getInstance(AutoSyncHandler.class);

  private static final BoolExperiment autoSyncEnabled =
      new BoolExperiment("blaze.auto.sync.enabled", true);

  /** Auto-syncs will only be run when there are no relevant file events for this length of time. */
  private static final Duration AUTO_SYNC_DELAY = Duration.ofSeconds(5);

  /** We ignore all events for this duration after starting a project-wide sync. */
  private static final Duration THROTTLE_AFTER_FULL_SYNC = Duration.ofSeconds(5);

  public static AutoSyncHandler getInstance(Project project) {
    return project.getComponent(AutoSyncHandler.class);
  }

  private final PendingChangesHandler<VirtualFile> pendingChangesHandler =
      new PendingChangesHandler<VirtualFile>(AUTO_SYNC_DELAY) {
        @Override
        boolean runTask(ImmutableSet<VirtualFile> changes) {
          // TODO(b/226553780) update for go/rabbit-decide-automatically
          if (!Blaze.getBuildSystemProvider(project).syncingRemotely()
              && BlazeSyncStatus.getInstance(project).syncInProgress()) {
            return false;
          }
          queueAutomaticSync(changes);
          return true;
        }
      };

  private final Project project;

  protected AutoSyncHandler(Project project) {
    this.project = project;
    if (Blaze.getProjectType(project) != ProjectType.ASPECT_SYNC) {
      return;
    }
    // listen for changes to the VFS
    VirtualFileManager.getInstance().addVirtualFileListener(new FileListener(), project);

    // trigger a check when navigating away from an unsaved file
    project
        .getMessageBus()
        .connect()
        .subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileFocusListener());
  }

  /**
   * Kicks off an automatic incremental sync, clearing the auto-sync queue.
   *
   * <p>TODO(brendandouglas): move to a Topic-based push model.
   */
  public void queueIncrementalSync(String reason) {
    pendingChangesHandler.clearQueue();

    BlazeSyncParams params =
        BlazeSyncParams.builder()
            .setTitle(AutoSyncProvider.AUTO_SYNC_TITLE)
            .setSyncMode(SyncMode.INCREMENTAL)
            .setSyncOrigin(AutoSyncProvider.AUTO_SYNC_REASON + "." + reason)
            .setAddProjectViewTargets(true)
            .setAddWorkingSet(BlazeUserSettings.getInstance().getExpandSyncToWorkingSet())
            .setBackgroundSync(true)
            .build();
    queueSync(params);
  }

  private void handleFileChange(VirtualFile file) {
    boolean setDirty = false;
    for (AutoSyncProvider provider : AutoSyncProvider.EP_NAME.getExtensions()) {
      if (provider.isSyncSensitiveFile(project, file)) {
        setDirty = true;
        queueChangedFile(file);
      }
    }
    if (setDirty) {
      BlazeSyncStatus.getInstance(project).setDirty();
    }
  }

  private void queueChangedFile(VirtualFile file) {
    if (autoSyncEnabled.getValue()) {
      pendingChangesHandler.queueChange(file);
    }
  }

  private void queueAutomaticSync(ImmutableSet<VirtualFile> changedFiles) {
    if (!autoSyncEnabled.getValue()) {
      return;
    }
    BlazeSyncParams autoSyncParams = null;
    for (AutoSyncProvider provider : AutoSyncProvider.EP_NAME.getExtensions()) {
      for (VirtualFile file : changedFiles) {
        autoSyncParams = combineSyncParams(autoSyncParams, getSyncParams(provider, file));
      }
    }
    autoSyncParams = filterTargets(autoSyncParams);
    if (autoSyncParams != null) {
      queueSync(autoSyncParams);
    }
  }

  /** Filters a list of targets to be synced, for example removing currently-syncing targets. */
  @Nullable
  private BlazeSyncParams filterTargets(@Nullable BlazeSyncParams params) {
    if (params == null || params.syncMode() != SyncMode.PARTIAL) {
      return params;
    }
    ImmutableSet<TargetExpression> targets =
        params.targetExpressions().stream()
            .filter(t -> !ignoreTarget(project, t))
            .collect(toImmutableSet());
    if (targets.isEmpty()) {
      // skip the sync entirely
      return null;
    }
    return params.toBuilder().setTargetExpressions(targets).build();
  }

  private static boolean ignoreTarget(Project project, TargetExpression target) {
    return ProjectTargetManager.getInstance(project).syncInProgress(target);
  }

  private void queueSync(BlazeSyncParams syncParams) {
    // all auto-syncs must have the 'backgroundSync' flag
    syncParams = syncParams.toBuilder().setBackgroundSync(true).build();
    logSync(syncParams);
    BlazeSyncManager.getInstance(project).requestProjectSync(syncParams);
  }

  private void logSync(BlazeSyncParams syncParams) {
    Map<String, String> data = new HashMap<>();
    data.put("syncMode", syncParams.syncMode().toString());
    if (syncParams.syncMode() == SyncMode.PARTIAL) {
      data.put("targets", Joiner.on(',').join(syncParams.targetExpressions()));
    }
    EventLoggingService.getInstance().logEvent(getClass(), "auto-sync", data);
    logger.info("Automatic sync queued: " + syncParams.syncMode());
  }

  @Nullable
  private BlazeSyncParams getSyncParams(AutoSyncProvider provider, VirtualFile file) {
    return autoSyncEnabled.getValue() ? provider.getAutoSyncParamsForFile(project, file) : null;
  }

  @Nullable
  private static BlazeSyncParams combineSyncParams(
      @Nullable BlazeSyncParams params1, @Nullable BlazeSyncParams params2) {
    if (params1 == null || params2 == null) {
      return params1 == null ? params2 : params1;
    }
    SyncMode mode = combineModes(params1.syncMode(), params2.syncMode());
    String origin =
        params1.syncOrigin().equals(params2.syncOrigin())
            ? params1.syncOrigin()
            : AutoSyncProvider.AUTO_SYNC_REASON + ".Combined";
    return BlazeSyncParams.builder()
        .setTitle(AutoSyncProvider.AUTO_SYNC_TITLE)
        .setSyncMode(mode)
        .setSyncOrigin(origin)
        .setBackgroundSync(params1.backgroundSync() && params2.backgroundSync())
        .addTargetExpressions(params1.targetExpressions())
        .addTargetExpressions(params2.targetExpressions())
        .setAddWorkingSet(params1.addWorkingSet() || params2.addWorkingSet())
        .setAddProjectViewTargets(
            params1.addProjectViewTargets() || params2.addProjectViewTargets())
        .build();
  }

  private static SyncMode combineModes(SyncMode mode1, SyncMode mode2) {
    return getSyncModePriority(mode1) < getSyncModePriority(mode2) ? mode1 : mode2;
  }

  private static int getSyncModePriority(SyncMode mode) {
    switch (mode) {
      case FULL:
        return 0;
      case INCREMENTAL:
        return 1;
      case PARTIAL:
        return 2;
      case NO_BUILD:
        return 3;
      case STARTUP:
        return 4;
    }
    throw new IllegalArgumentException("Unhandled sync mode: " + mode);
  }

  static class Listener implements SyncListener {
    @Override
    public void onSyncStart(Project project, BlazeContext context, SyncMode syncMode) {
      // cancel any pending auto-syncs if we're doing a project-wide sync
      if (syncMode == SyncMode.INCREMENTAL || syncMode == SyncMode.FULL) {
        AutoSyncHandler.getInstance(project)
            .pendingChangesHandler
            .clearQueueAndIgnoreChangesForDuration(THROTTLE_AFTER_FULL_SYNC);
      }
    }
  }

  private class FileListener extends VirtualFileAdapter {
    @Override
    public void propertyChanged(VirtualFilePropertyEvent event) {
      if (VirtualFile.PROP_NAME.equals(event.getPropertyName())) {
        handleFileEvent(event);
      }
    }

    @Override
    public void fileCreated(VirtualFileEvent event) {
      handleFileEvent(event);
    }

    @Override
    public void fileMoved(VirtualFileMoveEvent event) {
      handleFileEvent(event);
    }

    @Override
    public void contentsChanged(VirtualFileEvent event) {
      handleFileEvent(event);
    }

    private void handleFileEvent(VirtualFileEvent event) {
      if (event.getRequestor() == null) {
        // ignore events originating externally -- these are usually covered by
        // VcsAutoSyncProvider, and we don't want to trigger endless auto-syncs from VCS syncs
        return;
      }
      handleFileChange(event.getFile());
    }
  }

  private class FileFocusListener extends FileEditorManagerAdapter {
    @Override
    public void fileClosed(FileEditorManager source, VirtualFile file) {
      processEvent(file);
    }

    @Override
    public void selectionChanged(FileEditorManagerEvent event) {
      processEvent(event.getOldFile());
    }

    private void processEvent(@Nullable VirtualFile file) {
      FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
      if (file != null && fileDocumentManager.isFileModified(file)) {
        handleFileChange(file);
      }
    }
  }
}
