/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.startup;

import com.android.annotations.concurrency.GuardedBy;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.build.*;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.res.AppResourceRepository;
import com.android.tools.idea.res.ResourceClassRegistry;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.util.ThreeState;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.resourceManagers.ModuleResourceManagers;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.android.tools.idea.projectsystem.ProjectSystemSyncUtil.PROJECT_SYSTEM_SYNC_TOPIC;

/**
 * Delay the initialization of various tasks until the build state is known.
 */
public class DelayedInitialization extends AbstractProjectComponent {
  private final GradleSyncState mySyncState;
  private final GradleBuildState myBuildState;
  @GuardedBy("myLock")
  private final List<RunnablePair> myRunnables;
  private final Object myLock = new Object();

  @NotNull
  public static DelayedInitialization getInstance(@NotNull Project project) {
    return project.getComponent(DelayedInitialization.class);
  }

  private DelayedInitialization(@NotNull Project project,
                                @NotNull GradleSyncState syncState,
                                @NotNull GradleBuildState buildState) {
    super(project);

    mySyncState = syncState;
    myBuildState = buildState;
    myRunnables = new ArrayList<>();
  }

  @Override
  public void projectOpened() {
    GradleBuildState.subscribe(myProject, new BuildListener());

    myProject.getMessageBus().connect(myProject).subscribe(PROJECT_SYSTEM_SYNC_TOPIC, result -> {
      if (result == ProjectSystemSyncManager.SyncResult.SKIPPED || result == ProjectSystemSyncManager.SyncResult.FAILURE) {
        afterBuild();
      }
    });

    runAfterBuild(this::clearResourceCache, null);
  }

  /**
   * Register 2 callbacks for execution after the build status is known.
   * @param success      called if the build was successful
   * @param buildFailure called if the build finished with an error or if the user cancelled the build
   */
  public void runAfterBuild(@NotNull Runnable success, @Nullable Runnable buildFailure) {
    GradleBuildStatus status;
    synchronized (myLock) {
      status = getBuildStatus();
      if (status != GradleBuildStatus.BUILD_SUCCESS) {
        myRunnables.add(new RunnablePair(success, buildFailure));
      }
    }
    switch (status) {
      case BUILDING:
        break;
      case BUILD_NEEDED:
        // Request a sync
        ApplicationManager.getApplication().invokeLater(() -> ProjectSystemUtil.getProjectSystem(myProject)
          .getSyncManager().syncProject(ProjectSystemSyncManager.SyncReason.PROJECT_MODIFIED, true));
        break;
      case BUILD_ERROR:
        if (buildFailure != null) {
          buildFailure.run();
        }
        break;
      case BUILD_SUCCESS:
        success.run();
        break;
      default:
        assert false : "Not Implemented";
    }
  }

  private void afterBuild() {
    boolean buildError = getBuildStatus() != GradleBuildStatus.BUILD_SUCCESS;
    List<RunnablePair> runnables = new ArrayList<>();
    synchronized (myLock) {
      runnables.addAll(myRunnables);
      if (!buildError) {
        myRunnables.clear();
      }
    }
    runnables.stream().map(pair -> buildError ? pair.failure : pair.success).filter(Objects::nonNull).forEach(Runnable::run);
  }

  /**
   * Dump the cached resources if we have accessed the resources before the build was ready.
   * Clear the file based resources and attributes that may have been created based on those resources.
   */
  private void clearResourceCache() {
    if (AppResourceRepository.testAndClearTempResourceCached(myProject)) {
      ResourceClassRegistry.get(myProject).clearCache();
      for (AndroidFacet facet : AndroidUtils.getApplicationFacets(myProject)) {
        facet.refreshResources();
        ModuleResourceManagers.getInstance(facet).getLocalResourceManager().invalidateAttributeDefinitions();
      }
    }
  }

  private GradleBuildStatus getBuildStatus() {
    BuildSummary buildSummary = myBuildState.getSummary();
    if (buildSummary == null ||                                                                 // Project was just opened. The state is being computed.
        myBuildState.isBuildInProgress() ||                                                     // A build is currently in progress
        ProjectSystemUtil.getProjectSystem(myProject).getSyncManager().isSyncInProgress()) {    // A sync is currently in progress. A build is expected after that.
      return GradleBuildStatus.BUILDING;
    }

    // Don't return BUILD_ERROR if sync failed in a prior session but hasn't been attempted
    // in the current one yet, another sync will start soon.
    if (mySyncState.getSummary().getSyncTimestamp() > 0 && mySyncState.lastSyncFailedOrHasIssues()) {
      return GradleBuildStatus.BUILD_ERROR;                  // Sync failed in the current session.
    }

    if (buildSummary.getTimestamp() < 0 ||                   // New project, an initial build has not happened yet
        buildSummary.getStatus() == BuildStatus.CANCELED ||  // User cancelled the previous build
        mySyncState.isSyncNeeded() != ThreeState.NO) {       // A Gradle sync is needed
      return GradleBuildStatus.BUILD_NEEDED;
    }
    if (buildSummary.getStatus() == BuildStatus.FAILED) {
      return GradleBuildStatus.BUILD_ERROR;
    }
    return GradleBuildStatus.BUILD_SUCCESS;
  }

  private enum GradleBuildStatus {
    BUILDING,
    BUILD_NEEDED,
    BUILD_ERROR,
    BUILD_SUCCESS
  }

  private class BuildListener extends GradleBuildListener.Adapter {
    @Override
    public void buildFinished(@NotNull BuildStatus status, @Nullable BuildContext context) {
      afterBuild();
    }
  }

  private static class RunnablePair {
    public final Runnable success;
    public final Runnable failure;

    private RunnablePair(@NotNull Runnable success, @Nullable Runnable failure) {
      this.success = success;
      this.failure = failure;
    }
  }
}
