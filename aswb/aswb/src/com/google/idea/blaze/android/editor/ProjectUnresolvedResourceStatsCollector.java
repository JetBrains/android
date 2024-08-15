/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.editor;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.android.util.ResourcePsiElementFinder;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.google.idea.blaze.base.logging.EventLoggingService;
import com.google.idea.blaze.base.logging.utils.FileHighlights;
import com.google.idea.blaze.base.logging.utils.HighlightInfo;
import com.google.idea.blaze.base.logging.utils.HighlightStats;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.sync.SyncListener;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.SyncResult;
import com.google.idea.blaze.base.sync.autosync.ProjectTargetManager.SyncStatus;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.projectview.WorkspaceFileFinder;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverProvider;
import com.google.idea.blaze.base.syncstatus.SyncStatusContributor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.serviceContainer.NonInjectable;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/** Class to log unresolved resource symbols per project. */
class ProjectUnresolvedResourceStatsCollector implements Disposable {
  static ProjectUnresolvedResourceStatsCollector getInstance(Project project) {
    return project.getService(ProjectUnresolvedResourceStatsCollector.class);
  }

  private final WorkspacePathResolverProvider workspacePathResolverProvider;
  private final BlazeProjectDataManager blazeProjectDataManager;
  private final WorkspaceFileFinder.Provider workspaceFileFinderProvider;
  private final FileTypeRegistry fileTypeRegistry;

  private final Project project;
  private SyncMode lastSyncMode;
  private SyncResult lastSyncResult;
  private boolean isSyncing;

  /** Map from file path to the list of highlights for unresolved resource references. */
  private final Map<String, List<HighlightInfo>> fileToHighlightStats;

  ProjectUnresolvedResourceStatsCollector(Project project) {
    this(
        project,
        WorkspacePathResolverProvider.getInstance(project),
        BlazeProjectDataManager.getInstance(project),
        WorkspaceFileFinder.Provider.getInstance(project),
        FileTypeRegistry.getInstance());
  }

  @NonInjectable
  ProjectUnresolvedResourceStatsCollector(
      Project project,
      WorkspacePathResolverProvider workspacePathResolverProvider,
      BlazeProjectDataManager blazeProjectDataManager,
      WorkspaceFileFinder.Provider workspaceFileFinderProvider,
      FileTypeRegistry fileTypeRegistry) {

    this.project = project;
    fileToHighlightStats = Collections.synchronizedMap(new HashMap<>());

    this.workspacePathResolverProvider = workspacePathResolverProvider;
    this.blazeProjectDataManager = blazeProjectDataManager;
    this.workspaceFileFinderProvider = workspaceFileFinderProvider;
    this.fileTypeRegistry = fileTypeRegistry;

    LowMemoryWatcher.register(this::clearMap, project);
  }

  /**
   * Returns whether the collector wants to process the file.
   *
   * <p>Checks to see if information about the file is already present, and return true only if the
   * collector has no information on the file. Whether a resource reference resolves does not change
   * very frequently without syncing so we only collect information about a file once per sync.
   */
  boolean canProcessFile(PsiFile psiFile) {
    String filePath = PsiUtils.getFilePath(psiFile);
    return filePath != null && !fileToHighlightStats.containsKey(filePath);
  }

  void processHighlight(
      PsiElement psiElement, com.intellij.codeInsight.daemon.impl.HighlightInfo highlightInfo) {
    if (isSyncing) {
      return;
    }

    String filePath = getFilePath(psiElement);
    if (filePath == null) {
      return;
    }

    PsiElement resourceExpression = ResourcePsiElementFinder.getFullExpression(psiElement);
    if (resourceExpression == null) {
      return;
    }

    com.google.idea.blaze.base.logging.utils.HighlightInfo fileHighlightInfo =
        com.google.idea.blaze.base.logging.utils.HighlightInfo.builder()
            .setText(resourceExpression.getText())
            .setSeverity(HighlightInfo.convertHighlightSeverity(highlightInfo.getSeverity()))
            .setType(HighlightInfo.convertHighlightInfoType(highlightInfo.type))
            .setStartOffset(highlightInfo.startOffset)
            .setEndOffset(highlightInfo.endOffset)
            .build();

    fileToHighlightStats.computeIfAbsent(filePath, k -> new ArrayList<>()).add(fileHighlightInfo);
  }

  void onSyncStart(SyncMode syncMode) {
    logStatsAndClearMap();
    lastSyncMode = syncMode;
    lastSyncResult = null;
    isSyncing = true;
  }

  void onSyncComplete(SyncMode syncMode, SyncResult syncResult) {
    lastSyncMode = syncMode;
    lastSyncResult = syncResult;
    isSyncing = false;
  }

  /**
   * Called when the project is closing.
   *
   * <p>Project services are auto-registered by platform when they are created.
   */
  @Override
  public void dispose() {
    logStatsAndClearMap();
  }

  /**
   * Flushes data to logging service and clears {@link #fileToHighlightStats}
   *
   * <p>This method is synchronized because it can be called from either {@link
   * CollectorSyncListener}, or during project disposal. These are not guaranteed to be the same
   * thread.
   *
   * <p>Map is cleared after flushing to prevent accidental double logging.
   */
  private synchronized void logStatsAndClearMap() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      clearMap();
      return;
    }

    if (fileToHighlightStats.isEmpty() || lastSyncResult == null) {
      return;
    }

    HighlightStats highlightStats =
        HighlightStats.builder()
            .setGroup(HighlightStats.Group.ANDROID_RESOURCE_MISSING_REF)
            .setLastSyncMode(lastSyncMode)
            .setLastSyncResult(lastSyncResult)
            .setFileHighlights(getAllFileHighlights())
            .build();

    EventLoggingService.getInstance().logHighlightStats(highlightStats);
    clearMap();
  }

  /**
   * Clears {@link #fileToHighlightStats}. This method is synchronized to ensure that the map isn't
   * cleared while we are trying to flush the data.
   */
  private synchronized void clearMap() {
    fileToHighlightStats.clear();
  }

  private ImmutableList<FileHighlights> getAllFileHighlights() {
    ImmutableList.Builder<FileHighlights> listBuilder = new ImmutableList.Builder<>();
    synchronized (fileToHighlightStats) {
      fileToHighlightStats.forEach(
          (filePath, highlightInfos) -> {
            FileHighlights fileHighlights = createFileHighlights(filePath, highlightInfos);
            if (fileHighlights != null) {
              listBuilder.add(fileHighlights);
            }
          });
    }

    return listBuilder.build();
  }

  @Nullable
  private FileHighlights createFileHighlights(String filePath, List<HighlightInfo> highlightInfos) {
    File file = new File(filePath);
    VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(file);
    if (vFile == null) {
      return null;
    }

    String workspaceFilePath = filePath;
    SyncStatus fileSyncStatus = SyncStatus.UNSYNCED;

    // Attempt to find workspace relative file.
    // If it is a workspace file, set the fileSyncStatus.
    // Otherwise, the file is considered unsynced.
    WorkspacePathResolver workspacePathResolver = workspacePathResolverProvider.getPathResolver();
    if (workspacePathResolver != null) {
      WorkspacePath workspacePath = workspacePathResolver.getWorkspacePath(file);
      workspaceFilePath = workspacePath == null ? workspaceFilePath : workspacePath.relativePath();

      BlazeProjectData blazeProjectData = blazeProjectDataManager.getBlazeProjectData();
      if (blazeProjectData != null) {
        fileSyncStatus = SyncStatusContributor.getSyncStatus(project, blazeProjectData, vFile);
        fileSyncStatus = fileSyncStatus == null ? SyncStatus.UNSYNCED : fileSyncStatus;
      }
    }

    WorkspaceFileFinder workspaceFileFinder = workspaceFileFinderProvider.getWorkspaceFileFinder();
    boolean isProjectSource = workspaceFileFinder != null && workspaceFileFinder.isInProject(file);
    FileType fileType = fileTypeRegistry.getFileTypeByFileName(vFile.getName());

    return FileHighlights.builder()
        .setFileName(file.getName())
        .setFilePath(workspaceFilePath)
        .setFileType(fileType.getName())
        .setIsProjectSource(isProjectSource)
        .setSyncStatus(fileSyncStatus)
        .setHighlightInfos(ImmutableList.copyOf(highlightInfos))
        .build();
  }

  @Nullable
  private static String getFilePath(PsiElement psiElement) {
    PsiFile psiFile = psiElement.getContainingFile();
    if (psiFile == null) {
      return null;
    }
    return PsiUtils.getFilePath(psiFile);
  }

  /**
   * SyncListener for {@link ProjectUnresolvedResourceStatsCollector}
   *
   * <p>A static class is used to listen for Sync Events because IntelliJ will create new class
   * instances for each extension point if the same class is registered with multiple extension
   * points. See b/144167776 for an example of problem caused by this.
   */
  static class CollectorSyncListener implements SyncListener {
    @Override
    public void onSyncStart(Project project, BlazeContext context, SyncMode syncMode) {
      if (!UnresolvedResourceStatsCollector.enabled.getValue()) {
        return;
      }
      ProjectUnresolvedResourceStatsCollector.getInstance(project).onSyncStart(syncMode);
    }

    @Override
    public void onSyncComplete(
        Project project,
        BlazeContext context,
        BlazeImportSettings importSettings,
        ProjectViewSet projectViewSet,
        ImmutableSet<Integer> buildIds,
        BlazeProjectData blazeProjectData,
        SyncMode syncMode,
        SyncResult syncResult) {
      if (!UnresolvedResourceStatsCollector.enabled.getValue()) {
        return;
      }
      ProjectUnresolvedResourceStatsCollector.getInstance(project)
          .onSyncComplete(syncMode, syncResult);
    }
  }
}
