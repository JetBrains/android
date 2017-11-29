/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.idea;

<<<<<<< HEAD
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.ProjectBuildFileChecksums;
=======
import com.android.SdkConstants;
import com.android.tools.idea.gradle.project.GradleProjectSyncData;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
>>>>>>> goog/upstream-ij17
import com.android.tools.idea.gradle.project.sync.GradleSync;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.project.sync.idea.data.DataNodeCaches;
import com.android.tools.idea.gradle.project.sync.setup.post.PostSyncProjectSetup;
<<<<<<< HEAD
import com.intellij.openapi.application.ApplicationManager;
=======
import com.android.tools.idea.gradle.util.Projects;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.extensions.Extensions;
>>>>>>> goog/upstream-ij17
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.projectImport.ProjectOpenProcessor;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.project.wizard.GradleProjectOpenProcessor;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import java.io.File;
import java.util.Collection;
import java.util.Set;

import static com.android.tools.idea.gradle.project.sync.idea.ProjectFinder.registerAsNewProject;
import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
<<<<<<< HEAD
import static com.android.tools.idea.Projects.getBaseDirPath;
=======
>>>>>>> goog/upstream-ij17
import static com.intellij.openapi.externalSystem.util.ExternalSystemUtil.refreshProject;

public class IdeaGradleSync implements GradleSync {
  private static final boolean SYNC_WITH_CACHED_MODEL_ONLY =
    SystemProperties.getBooleanProperty("studio.sync.with.cached.model.only", false);

  @NotNull private final Project myProject;
  @NotNull private final GradleProjectInfo myProjectInfo;

  public IdeaGradleSync(@NotNull Project project) {
    this(project, GradleProjectInfo.getInstance(project));
  }

  private IdeaGradleSync(@NotNull Project project, @NotNull GradleProjectInfo projectInfo) {
    myProject = project;
    myProjectInfo = projectInfo;
  }

  @Override
  public void sync(@NotNull GradleSyncInvoker.Request request, @Nullable GradleSyncListener listener) {
    // Prevent IDEA from syncing with Gradle. We want to have full control of syncing.
    myProject.putUserData(ExternalSystemDataKeys.NEWLY_IMPORTED_PROJECT, true);

    if (myProjectInfo.isNewProject()) {
      registerAsNewProject(myProject);
    }

    if (SYNC_WITH_CACHED_MODEL_ONLY || request.useCachedGradleModels) {
      ProjectBuildFileChecksums buildFileChecksums = ProjectBuildFileChecksums.findFor((myProject));
      if (buildFileChecksums != null && buildFileChecksums.canUseCachedData()) {
        DataNodeCaches dataNodeCaches = DataNodeCaches.getInstance(myProject);
        DataNode<ProjectData> cache = dataNodeCaches.getCachedProjectData();
        if (cache != null && !dataNodeCaches.isCacheMissingModels(cache)) {
          PostSyncProjectSetup.Request setupRequest = new PostSyncProjectSetup.Request();
          setupRequest.usingCachedGradleModels = true;
          setupRequest.generateSourcesAfterSync = false;
          setupRequest.lastSyncTimestamp = buildFileChecksums.getLastGradleSyncTimestamp();

          setSkipAndroidPluginUpgrade(request, setupRequest);

          ProjectSetUpTask setUpTask = new ProjectSetUpTask(myProject, setupRequest, listener, true /* sync skipped */);
          setUpTask.onSuccess(cache);
          return;
        }
      }
    }

    PostSyncProjectSetup.Request setupRequest = new PostSyncProjectSetup.Request();
    setupRequest.generateSourcesAfterSync = request.generateSourcesOnSuccess;
    setupRequest.cleanProjectAfterSync = request.cleanProject;

    setSkipAndroidPluginUpgrade(request, setupRequest);

<<<<<<< HEAD
    String externalProjectPath = getBaseDirPath(myProject).getPath();

    ProjectSetUpTask setUpTask = new ProjectSetUpTask(myProject, setupRequest, listener, false /* sync not skipped */);
    ProgressExecutionMode executionMode = request.getProgressExecutionMode();
    refreshProject(myProject, GRADLE_SYSTEM_ID, externalProjectPath, setUpTask, false /* resolve dependencies */,
                   executionMode, true /* always report import errors */);
=======
    // the sync should be aware of multiple linked gradle project with a single IDE project
    // and a linked gradle project can be located not in the IDE Project.baseDir
    Set<String> androidProjectCandidatesPaths = ContainerUtil.newLinkedHashSet();
    if (request.isNewProject()) {
      GradleSettings gradleSettings = GradleSettings.getInstance(project);
      Collection<GradleProjectSettings> projectsSettings = gradleSettings.getLinkedProjectsSettings();
      if (projectsSettings.isEmpty()) {
        GradleProjectOpenProcessor gradleProjectOpenProcessor =
          Extensions.findExtension(ProjectOpenProcessor.EXTENSION_POINT_NAME, GradleProjectOpenProcessor.class);
        if (project.getBasePath() != null && gradleProjectOpenProcessor.canOpenProject(project.getBaseDir())) {
          GradleProjectSettings projectSettings = new GradleProjectSettings();
          String externalProjectPath = ExternalSystemApiUtil.toCanonicalPath(project.getBasePath());
          projectSettings.setExternalProjectPath(externalProjectPath);
          gradleSettings.setLinkedProjectsSettings(ContainerUtil.list(projectSettings));
          androidProjectCandidatesPaths.add(externalProjectPath);
        }
      }
      else if (projectsSettings.size() == 1) {
        androidProjectCandidatesPaths.add(projectsSettings.iterator().next().getExternalProjectPath());
      }
    }
    else {
      for (Module module : ProjectFacetManager.getInstance(project).getModulesWithFacet(GradleFacet.getFacetTypeId())) {
        String projectPath = ExternalSystemApiUtil.getExternalRootProjectPath(module);
        ContainerUtil.addIfNotNull(androidProjectCandidatesPaths, projectPath);
      }
    }
    if (androidProjectCandidatesPaths.isEmpty()) {
      // try to discover the project in the IDE project base dir if there is no linked gradle projects at all
      if (GradleSettings.getInstance(project).getLinkedProjectsSettings().isEmpty()) {
        String externalProjectPath = ExternalSystemApiUtil.toCanonicalPath(Projects.getBaseDirPath(project).getPath());
        if (new File(externalProjectPath, SdkConstants.FN_BUILD_GRADLE).isFile() ||
            new File(externalProjectPath, SdkConstants.FN_SETTINGS_GRADLE).isFile()) {
          androidProjectCandidatesPaths.add(externalProjectPath);
        }
      }
    }

    if (androidProjectCandidatesPaths.isEmpty()) {
      if (listener != null) {
        listener.syncSkipped(project);
      }
      return;
    }

    for (String rootPath : androidProjectCandidatesPaths) {
      ProjectSetUpTask setUpTask =
        new ProjectSetUpTask(project, setupRequest, listener, request.isNewProject(), false, false);
      ProgressExecutionMode executionMode = request.getProgressExecutionMode();
      refreshProject(project, GRADLE_SYSTEM_ID, rootPath, setUpTask, false /* resolve dependencies */,
                     executionMode, true /* always report import errors */);
    }
>>>>>>> goog/upstream-ij17
  }

  private static void setSkipAndroidPluginUpgrade(@NotNull GradleSyncInvoker.Request syncRequest,
                                                  @NotNull PostSyncProjectSetup.Request setupRequest) {
    if (ApplicationManager.getApplication().isUnitTestMode() && syncRequest.skipAndroidPluginUpgrade) {
      setupRequest.skipAndroidPluginUpgrade = true;
    }
  }
}
