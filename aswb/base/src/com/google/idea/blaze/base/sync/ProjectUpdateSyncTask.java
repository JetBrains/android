/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.async.FutureUtil;
import com.google.idea.blaze.base.async.FutureUtil.FutureResult;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.filecache.FileCaches;
import com.google.idea.blaze.base.filecache.RemoteOutputsCache;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.io.VirtualFileSystemProvider;
import com.google.idea.blaze.base.model.AspectSyncProjectData;
import com.google.idea.blaze.base.model.BlazeLibrary;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.blaze.base.model.ProjectTargetData;
import com.google.idea.blaze.base.model.RemoteOutputArtifacts;
import com.google.idea.blaze.base.model.SyncState;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.prefetch.PrefetchService;
import com.google.idea.blaze.base.prefetch.PrefetchStats;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.output.StatusOutput;
import com.google.idea.blaze.base.scope.scopes.NetworkTrafficTrackingScope.NetworkTrafficUsedOutput;
import com.google.idea.blaze.base.scope.scopes.TimingScope;
import com.google.idea.blaze.base.scope.scopes.TimingScope.EventType;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin.ModuleEditor;
import com.google.idea.blaze.base.sync.SyncScope.SyncCanceledException;
import com.google.idea.blaze.base.sync.SyncScope.SyncFailedException;
import com.google.idea.blaze.base.sync.aspects.BlazeIdeInterface;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.libraries.BlazeLibraryCollector;
import com.google.idea.blaze.base.sync.libraries.LibraryEditor;
import com.google.idea.blaze.base.sync.projectstructure.ContentEntryEditor;
import com.google.idea.blaze.base.sync.projectstructure.DirectoryStructure;
import com.google.idea.blaze.base.sync.projectstructure.ModuleEditorImpl;
import com.google.idea.blaze.base.sync.projectstructure.ModuleEditorProvider;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoderImpl;
import com.google.idea.blaze.base.util.SaveUtil;
import com.google.idea.common.util.Transactions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;

/** Runs the 'project update' phase of sync, after the blaze build phase has completed. */
final class ProjectUpdateSyncTask {

  private static final Logger logger = Logger.getInstance(ProjectUpdateSyncTask.class);

  /** Updates the project target map and related data, given the blaze build output. */
  @Nullable
  static ProjectTargetData updateTargetData(
      Project project,
      BlazeSyncParams syncParams,
      SyncProjectState projectState,
      BlazeSyncBuildResult buildResult,
      BlazeContext parentContext) {
    boolean mergeWithOldState = !syncParams.addProjectViewTargets();
    return Scope.push(
        parentContext,
        context -> {
          context.push(new TimingScope("ReadBuildOutputs", EventType.BlazeInvocation));
          context.output(new StatusOutput("Parsing build outputs..."));
          BlazeIdeInterface blazeIdeInterface = BlazeIdeInterface.getInstance();
          return blazeIdeInterface.updateTargetData(
              project,
              context,
              WorkspaceRoot.fromProject(project),
              projectState,
              buildResult,
              mergeWithOldState,
              getOldProjectData(project, syncParams.syncMode()));
        });
  }

  /** Runs the project update phase of sync. */
  static void runProjectUpdatePhase(
      Project project,
      SyncMode syncMode,
      SyncProjectState projectState,
      ProjectTargetData targetData,
      BlazeInfo blazeInfo,
      BlazeContext context)
      throws SyncCanceledException, SyncFailedException {
    SaveUtil.saveAllFiles();
    ProjectUpdateSyncTask task =
        new ProjectUpdateSyncTask(project, syncMode, projectState, targetData, blazeInfo);
    task.run(context);
  }

  private final Project project;
  private final BlazeImportSettings importSettings;
  private final WorkspaceRoot workspaceRoot;
  private final SyncMode syncMode;
  private final SyncProjectState projectState;
  private final ProjectTargetData targetData;
  private final BlazeInfo blazeInfo;
  @Nullable private final BlazeProjectData oldProjectData;

  private ProjectUpdateSyncTask(
      Project project,
      SyncMode syncMode,
      SyncProjectState projectState,
      ProjectTargetData targetData,
      BlazeInfo blazeInfo) {
    this.project = project;
    this.importSettings = BlazeImportSettingsManager.getInstance(project).getImportSettings();
    this.workspaceRoot = WorkspaceRoot.fromImportSettings(importSettings);
    this.syncMode = syncMode;
    this.projectState = projectState;
    this.targetData = targetData;
    this.blazeInfo = Preconditions.checkNotNull(blazeInfo, "Null BlazeInfo");
    this.oldProjectData = getOldProjectData(project, syncMode);
  }

  @Nullable
  private static AspectSyncProjectData getOldProjectData(Project project, SyncMode syncMode) {
    if (syncMode == SyncMode.FULL) {
      return null;
    }
    Preconditions.checkState(
        Blaze.getProjectType(project) == ProjectType.ASPECT_SYNC,
        "This should only happen in legacy sync");
    BlazeProjectData data = BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (data == null) {
      return null;
    }
    Preconditions.checkState(data instanceof AspectSyncProjectData, "Invalid project data type");
    return (AspectSyncProjectData) data;
  }

  private void run(BlazeContext context) throws SyncCanceledException, SyncFailedException {
    TargetMap targetMap = targetData.targetMap();
    RemoteOutputArtifacts oldRemoteState = RemoteOutputArtifacts.fromProjectData(oldProjectData);
    RemoteOutputArtifacts newRemoteState = targetData.remoteOutputs;

    ArtifactLocationDecoder artifactLocationDecoder =
        new ArtifactLocationDecoderImpl(
            blazeInfo, projectState.getWorkspacePathResolver(), newRemoteState);

    Scope.push(
        context,
        childContext -> {
          childContext.push(new TimingScope("UpdateRemoteOutputsCache", EventType.Prefetching));
          RemoteOutputsCache.getInstance(project)
              .updateCache(
                  context,
                  targetMap,
                  projectState.getLanguageSettings(),
                  newRemoteState,
                  oldRemoteState,
                  /* clearCache= */ syncMode == SyncMode.FULL);
        });

    SyncState.Builder syncStateBuilder = new SyncState.Builder();
    Scope.push(
        context,
        childContext -> {
          childContext.push(new TimingScope("UpdateSyncState", EventType.Other));
          for (BlazeSyncPlugin syncPlugin : BlazeSyncPlugin.EP_NAME.getExtensions()) {
            syncPlugin.updateSyncState(
                project,
                childContext,
                workspaceRoot,
                projectState.getProjectViewSet(),
                projectState.getLanguageSettings(),
                projectState.getBlazeVersionData(),
                projectState.getWorkingSet(),
                artifactLocationDecoder,
                targetMap,
                syncStateBuilder,
                oldProjectData != null ? oldProjectData.getSyncState() : null,
                syncMode);
          }
        });
    if (context.isCancelled()) {
      throw new SyncCanceledException();
    }
    if (context.hasErrors()) {
      throw new SyncFailedException();
    }

    BlazeProjectData newProjectData =
        new AspectSyncProjectData(
            targetData,
            blazeInfo,
            projectState.getBlazeVersionData(),
            projectState.getWorkspacePathResolver(),
            artifactLocationDecoder,
            projectState.getLanguageSettings(),
            syncStateBuilder.build());

    FileCaches.onSync(
        project,
        context,
        projectState.getProjectViewSet(),
        newProjectData,
        oldProjectData,
        syncMode);
    ListenableFuture<PrefetchStats> prefetch =
        PrefetchService.getInstance()
            .prefetchProjectFiles(project, projectState.getProjectViewSet(), newProjectData);
    FutureResult<PrefetchStats> result =
        FutureUtil.waitForFuture(context, prefetch)
            .withProgressMessage("Prefetching files...")
            .timed("PrefetchFiles", EventType.Prefetching)
            .onError("Prefetch failed")
            .run();
    if (result.success()) {
      long prefetched = result.result().bytesPrefetched();
      if (prefetched > 0) {
        context.output(new NetworkTrafficUsedOutput(prefetched, "prefetch"));
      }
    }

    ListenableFuture<DirectoryStructure> directoryStructureFuture =
        DirectoryStructure.getRootDirectoryStructure(
            project, workspaceRoot, projectState.getProjectViewSet());

    refreshVirtualFileSystem(context, project, newProjectData);

    DirectoryStructure directoryStructure =
        FutureUtil.waitForFuture(context, directoryStructureFuture)
            .withProgressMessage("Computing directory structure...")
            .timed("DirectoryStructure", EventType.Other)
            .onError("Directory structure computation failed")
            .run()
            .result();
    if (directoryStructure == null) {
      throw new SyncFailedException();
    }

    boolean success =
        updateProject(
            context,
            projectState.getProjectViewSet(),
            projectState.getBlazeVersionData(),
            directoryStructure,
            oldProjectData,
            newProjectData);
    if (!success) {
      throw new SyncFailedException();
    }
  }

  private static void refreshVirtualFileSystem(
      BlazeContext context, Project project, BlazeProjectData blazeProjectData) {
    Scope.push(
        context,
        (childContext) -> {
          childContext.push(new TimingScope("RefreshVirtualFileSystem", EventType.Other));
          childContext.output(new StatusOutput("Refreshing files..."));
          if (ApplicationManager.getApplication().isReadAccessAllowed()) {
            IssueOutput.warn("Attempted to refresh file system while holding read lock")
                .submit(childContext);
            logger.warn("Attempted to refresh file system while holding read lock");
          } else if (Arrays.stream(BlazeSyncPlugin.EP_NAME.getExtensions())
              .anyMatch(p -> p.refreshExecutionRoot(project, blazeProjectData))) {
            // this refresh should happen off EDT and without read lock.
            VirtualFile root =
                VfsUtil.findFileByIoFile(blazeProjectData.getBlazeInfo().getExecutionRoot(), true);
            VfsUtil.markDirtyAndRefresh(
                /* async= */ false, /* recursive= */ true, /* reloadChildren= */ true, root);
          }
        });
  }

  private void createSdks(BlazeProjectData blazeProjectData) {
    for (BlazeSyncPlugin syncPlugin : BlazeSyncPlugin.EP_NAME.getExtensions()) {
      syncPlugin.createSdks(project, blazeProjectData);
    }
  }

  private boolean updateProject(
      BlazeContext parentContext,
      ProjectViewSet projectViewSet,
      BlazeVersionData blazeVersionData,
      DirectoryStructure directoryStructure,
      @Nullable BlazeProjectData oldBlazeProjectData,
      BlazeProjectData newBlazeProjectData) {
    return Scope.push(
        parentContext,
        context -> {
          context.push(new TimingScope("UpdateProjectStructure", EventType.Other));
          context.output(new StatusOutput("Initializing project SDKs..."));
          ApplicationManager.getApplication().invokeAndWait(() -> createSdks(newBlazeProjectData));
          context.output(new StatusOutput("Committing project structure..."));

          try {
            Transactions.submitWriteActionTransactionAndWait(
                () ->
                    ProjectRootManagerEx.getInstanceEx(this.project)
                        .mergeRootsChangesDuring(
                            () -> {
                              updateProjectStructure(
                                  context,
                                  importSettings,
                                  projectViewSet,
                                  blazeVersionData,
                                  directoryStructure,
                                  newBlazeProjectData,
                                  oldBlazeProjectData);
                            }));
          } catch (ProcessCanceledException e) {
            context.setCancelled();
            throw e;
          } catch (Throwable e) {
            IssueOutput.error("Internal error. Error: " + e).submit(context);
            logger.error(e);
            return false;
          }

          BlazeProjectDataManager.getInstance(project)
              .saveProject(importSettings, newBlazeProjectData);
          return true;
        });
  }

  private void updateProjectStructure(
      BlazeContext context,
      BlazeImportSettings importSettings,
      ProjectViewSet projectViewSet,
      BlazeVersionData blazeVersionData,
      DirectoryStructure directoryStructure,
      BlazeProjectData newBlazeProjectData,
      @Nullable BlazeProjectData oldBlazeProjectData) {

    for (BlazeSyncPlugin syncPlugin : BlazeSyncPlugin.EP_NAME.getExtensions()) {
      syncPlugin.updateProjectSdk(
          project, context, projectViewSet, blazeVersionData, newBlazeProjectData);
    }

    ModuleEditorImpl moduleEditor =
        ModuleEditorProvider.getInstance().getModuleEditor(project, importSettings);

    ModuleType<?> workspaceModuleType = null;
    for (BlazeSyncPlugin syncPlugin : BlazeSyncPlugin.EP_NAME.getExtensions()) {
      workspaceModuleType =
          syncPlugin.getWorkspaceModuleType(
              newBlazeProjectData.getWorkspaceLanguageSettings().getWorkspaceType());
      if (workspaceModuleType != null) {
        break;
      }
    }
    if (workspaceModuleType == null) {
      workspaceModuleType = ModuleTypeManager.getInstance().getDefaultModuleType();
    }

    Module workspaceModule =
        moduleEditor.createModule(BlazeDataStorage.WORKSPACE_MODULE_NAME, workspaceModuleType);
    ModifiableRootModel workspaceModifiableModel = moduleEditor.editModule(workspaceModule);

    ContentEntryEditor.createContentEntries(
        project,
        workspaceRoot,
        projectViewSet,
        newBlazeProjectData,
        directoryStructure,
        workspaceModifiableModel);

    List<BlazeLibrary> libraries =
        BlazeLibraryCollector.getLibraries(projectViewSet, newBlazeProjectData);
    LibraryEditor.updateProjectLibraries(
        project, context, projectViewSet, newBlazeProjectData, libraries);
    LibraryEditor.configureDependencies(project, workspaceModifiableModel, libraries);

    for (BlazeSyncPlugin blazeSyncPlugin : BlazeSyncPlugin.EP_NAME.getExtensions()) {
      blazeSyncPlugin.updateProjectStructure(
          project,
          context,
          workspaceRoot,
          projectViewSet,
          newBlazeProjectData,
          oldBlazeProjectData,
          moduleEditor,
          workspaceModule,
          workspaceModifiableModel);
    }

    createProjectDataDirectoryModule(
        moduleEditor, new File(importSettings.getProjectDataDirectory()), workspaceModuleType);

    moduleEditor.commitWithGc(context);
  }

  /**
   * Creates a module that includes the user's data directory.
   *
   * <p>This is useful to be able to edit the project view without IntelliJ complaining it's outside
   * the project.
   */
  private void createProjectDataDirectoryModule(
      ModuleEditor moduleEditor, File projectDataDirectory, ModuleType<?> moduleType) {
    Module module = moduleEditor.createModule(".project-data-dir", moduleType);
    ModifiableRootModel modifiableModel = moduleEditor.editModule(module);
    ContentEntry rootContentEntry =
        modifiableModel.addContentEntry(pathToUrl(projectDataDirectory));
    rootContentEntry.addExcludeFolder(pathToUrl(new File(projectDataDirectory, ".idea")));
    rootContentEntry.addExcludeFolder(
        pathToUrl(BlazeDataStorage.getProjectDataDir(importSettings)));
  }

  private static String pathToUrl(File path) {
    String filePath = FileUtil.toSystemIndependentName(path.getPath());
    return VirtualFileManager.constructUrl(
        VirtualFileSystemProvider.getInstance().getSystem().getProtocol(), filePath);
  }
}
