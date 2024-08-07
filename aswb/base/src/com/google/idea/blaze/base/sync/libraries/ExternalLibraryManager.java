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
package com.google.idea.blaze.base.sync.libraries;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.google.idea.blaze.base.sync.SyncListener;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.SyncResult;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.vcs.VcsSyncListener;
import com.google.idea.common.util.Transactions;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.vfs.AsyncVfsEventsPostProcessor;
import java.io.File;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * External library manager that rebuilds {@link BlazeExternalSyntheticLibrary}s during sync, and
 * updates individual {@link VirtualFile} entries in response to VFS events.
 */
public class ExternalLibraryManager implements Disposable {

  private static final Logger logger = Logger.getInstance(ExternalLibraryManager.class);
  private final Project project;
  private volatile boolean duringBlazeSync;
  private volatile ImmutableMap<
          Class<? extends BlazeExternalLibraryProvider>, BlazeExternalSyntheticLibrary>
      libraries;

  public static ExternalLibraryManager getInstance(Project project) {
    return project.getService(ExternalLibraryManager.class);
  }

  ExternalLibraryManager(Project project) {
    this.project = project;
    this.duringBlazeSync = false;
    this.libraries = ImmutableMap.of();
    AsyncVfsEventsPostProcessor.getInstance()
        .addListener(
            events -> {
              if (duringBlazeSync || libraries.isEmpty()) {
                return;
              }
              ImmutableList<VirtualFile> deletedFiles =
                  events.stream()
                      .filter(VFileDeleteEvent.class::isInstance)
                      .map(VFileEvent::getFile)
                      .collect(toImmutableList());
              if (!deletedFiles.isEmpty()) {
                libraries.values().forEach(library -> library.removeInvalidFiles(deletedFiles));
              }
            },
            this);
  }

  @Nullable
  public BlazeExternalSyntheticLibrary getLibrary(
      Class<? extends BlazeExternalLibraryProvider> providerClass) {
    return duringBlazeSync ? null : libraries.get(providerClass);
  }

  private void initialize(BlazeProjectData projectData) {
    this.libraries =
        AdditionalLibraryRootsProvider.EP_NAME
            .extensions()
            .filter(BlazeExternalLibraryProvider.class::isInstance)
            .map(BlazeExternalLibraryProvider.class::cast)
            .map(
                provider -> {
                  ImmutableList<File> files = provider.getLibraryFiles(project, projectData);
                  return !files.isEmpty()
                      ? Maps.immutableEntry(
                          provider.getClass(),
                          new BlazeExternalSyntheticLibrary(provider.getLibraryName(), files))
                      : null;
                })
            .filter(Objects::nonNull)
            .collect(toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  @Override
  public void dispose() {}

  /**
   * Sync listener to prevent external libraries from being accessed during sync to avoid spamming
   * {@link VirtualFile#isValid()} errors.
   */
  static class StartSyncListener implements SyncListener {
    @Override
    public void onSyncStart(Project project, BlazeContext context, SyncMode syncMode) {
      ExternalLibraryManager.getInstance(project).duringBlazeSync = true;
    }

    @Override
    public void afterSync(
        Project project,
        BlazeContext context,
        SyncMode syncMode,
        SyncResult syncResult,
        ImmutableSet<Integer> buildIds) {
      ExternalLibraryManager manager = ExternalLibraryManager.getInstance(project);
      if (syncMode.mayAttachExternalLibraries()) {
        BlazeProjectData blazeProjectData =
            BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
        if (blazeProjectData != null) {
          manager.initialize(blazeProjectData);
          manager.duringBlazeSync = false;
          if (!manager.libraries.isEmpty()) {
            // TODO(b/192431174): Consider not triggering `project roots have changed` events.
            logger.info(
                "External libraries have been attached to the project. Triggering a `project roots"
                    + " have changed` event so the external libraries can be indexed.");
            Transactions.submitWriteActionTransaction(
                manager,
                () ->
                    ProjectRootManagerEx.getInstanceEx(project)
                        .makeRootsChange(() -> {}, /* fileTypes= */ false, /* fireEvents= */ true));
          }
        }
      }
      manager.duringBlazeSync = false;
    }
  }

  /**
   * Sync plugin to rebuild external libraries during sync to be included in the reindexing
   * operation.
   */
  static class SyncPlugin implements BlazeSyncPlugin {
    @Override
    public boolean refreshExecutionRoot(Project project, BlazeProjectData blazeProjectData) {
      ExternalLibraryManager manager = ExternalLibraryManager.getInstance(project);
      manager.initialize(blazeProjectData);
      manager.duringBlazeSync = false;
      return true;
    }
  }

  static class VcsListener implements VcsSyncListener {
    @Override
    public void onVcsSync(Project project) {
      ExternalLibraryManager.getInstance(project)
          .libraries
          .values()
          .forEach(BlazeExternalSyntheticLibrary::restoreMissingFiles);
    }
  }
}
