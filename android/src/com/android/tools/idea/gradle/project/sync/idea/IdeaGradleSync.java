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

import com.android.SdkConstants;
import com.android.tools.idea.gradle.project.GradleProjectSyncData;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.sync.GradleSync;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.project.sync.idea.data.DataNodeCaches;
import com.android.tools.idea.gradle.project.sync.setup.post.PostSyncProjectSetup;
import com.android.tools.idea.gradle.util.Projects;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.Extensions;
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
import org.jetbrains.plugins.gradle.service.project.wizard.GradleJavaProjectOpenProcessor;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import java.io.File;
import java.util.Collection;
import java.util.Set;

import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.intellij.openapi.externalSystem.util.ExternalSystemUtil.refreshProject;

public class IdeaGradleSync implements GradleSync {
  private static final boolean SYNC_WITH_CACHED_MODEL_ONLY =
    SystemProperties.getBooleanProperty("studio.sync.with.cached.model.only", false);

  @NotNull private final Project myProject;

  public IdeaGradleSync(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public void sync(@NotNull GradleSyncInvoker.Request request,
                   @Nullable GradleSyncListener listener) {
    // Prevent IDEA from syncing with Gradle. We want to have full control of syncing.
    myProject.putUserData(ExternalSystemDataKeys.NEWLY_IMPORTED_PROJECT, true);
    boolean newProject = request.isNewOrImportedProject();

    if (SYNC_WITH_CACHED_MODEL_ONLY || request.isUseCachedGradleModels()) {
      GradleProjectSyncData syncData = GradleProjectSyncData.getInstance((myProject));
      if (syncData != null && syncData.canUseCachedProjectData()) {
        DataNodeCaches dataNodeCaches = DataNodeCaches.getInstance(myProject);
        DataNode<ProjectData> cache = dataNodeCaches.getCachedProjectData();
        if (cache != null && !dataNodeCaches.isCacheMissingModels(cache)) {
          PostSyncProjectSetup.Request setupRequest = new PostSyncProjectSetup.Request();

          // @formatter:off
          setupRequest.setUsingCachedGradleModels(true)
                      .setGenerateSourcesAfterSync(false)
                      .setLastSyncTimestamp(syncData.getLastGradleSyncTimestamp());
          // @formatter:on

          setSkipAndroidPluginUpgrade(request, setupRequest);

          ProjectSetUpTask setUpTask = new ProjectSetUpTask(myProject, setupRequest, listener, newProject, true /* select modules */, true);
          setUpTask.onSuccess(cache);
          return;
        }
      }
    }

    PostSyncProjectSetup.Request setupRequest = new PostSyncProjectSetup.Request();

    // @formatter:off
    setupRequest.setGenerateSourcesAfterSync(request.isGenerateSourcesOnSuccess())
                .setCleanProjectAfterSync(request.isCleanProject());
    // @formatter:on
    setSkipAndroidPluginUpgrade(request, setupRequest);

    // the sync should be aware of multiple linked gradle project with a single IDE project
    // and a linked gradle project can be located not in the IDE Project.baseDir
    Set<String> androidProjectCandidatesPaths = ContainerUtil.newLinkedHashSet();
    if (request.isNewOrImportedProject()) {
      GradleSettings gradleSettings = GradleSettings.getInstance(myProject);
      Collection<GradleProjectSettings> projectsSettings = gradleSettings.getLinkedProjectsSettings();
      if (projectsSettings.isEmpty()) {
        GradleJavaProjectOpenProcessor gradleProjectOpenProcessor =
          Extensions.findExtension(ProjectOpenProcessor.EXTENSION_POINT_NAME, GradleJavaProjectOpenProcessor.class);
        if (myProject.getBasePath() != null && gradleProjectOpenProcessor.canOpenProject(myProject.getBaseDir())) {
          GradleProjectSettings projectSettings = new GradleProjectSettings();
          String externalProjectPath = ExternalSystemApiUtil.toCanonicalPath(myProject.getBasePath());
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
      for (Module module : ProjectFacetManager.getInstance(myProject).getModulesWithFacet(GradleFacet.getFacetTypeId())) {
        String projectPath = ExternalSystemApiUtil.getExternalRootProjectPath(module);
        ContainerUtil.addIfNotNull(androidProjectCandidatesPaths, projectPath);
      }
    }
    if (androidProjectCandidatesPaths.isEmpty()) {
      // try to discover the project in the IDE project base dir if there is no linked gradle projects at all
      if (GradleSettings.getInstance(myProject).getLinkedProjectsSettings().isEmpty()) {
        String externalProjectPath = ExternalSystemApiUtil.toCanonicalPath(Projects.getBaseDirPath(myProject).getPath());
        if (new File(externalProjectPath, SdkConstants.FN_BUILD_GRADLE).isFile() ||
            new File(externalProjectPath, SdkConstants.FN_SETTINGS_GRADLE).isFile()) {
          androidProjectCandidatesPaths.add(externalProjectPath);
        }
      }
    }

    if (androidProjectCandidatesPaths.isEmpty()) {
      if (listener != null) {
        listener.syncSkipped(myProject);
      }
      return;
    }

    for (String rootPath : androidProjectCandidatesPaths) {
      ProjectSetUpTask setUpTask = new ProjectSetUpTask(myProject, setupRequest, listener, newProject,
                                                        newProject /* select modules if it's a new project */, false);
      ProgressExecutionMode executionMode = request.getProgressExecutionMode();
      refreshProject(myProject, GRADLE_SYSTEM_ID, rootPath, setUpTask, false /* resolve dependencies */,
                     executionMode, true /* always report import errors */);
    }
  }

  private static void setSkipAndroidPluginUpgrade(@NotNull GradleSyncInvoker.Request syncRequest,
                                                  @NotNull PostSyncProjectSetup.Request setupRequest) {
    if (ApplicationManager.getApplication().isUnitTestMode() && syncRequest.isSkipAndroidPluginUpgrade()) {
      //noinspection TestOnlyProblems
      setupRequest.setSkipAndroidPluginUpgrade();
    }
  }
}
