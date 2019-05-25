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
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.ProjectBuildFileChecksums;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.project.model.JavaModuleModel;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.project.sync.*;
import com.android.tools.idea.gradle.project.sync.idea.data.DataNodeCaches;
import com.android.tools.idea.gradle.project.sync.setup.post.PostSyncProjectSetup;
import com.google.common.collect.ImmutableList;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.projectImport.ProjectOpenProcessor;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.project.GradleProjectOpenProcessor;
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolver;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import java.io.File;
import java.util.*;

import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.project.sync.idea.ProjectFinder.registerAsNewProject;
import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.*;
import static com.android.tools.idea.gradle.project.sync.ng.NewGradleSync.areCachedFilesMissing;
import static com.android.tools.idea.gradle.project.sync.ng.NewGradleSync.isCompoundSync;
import static com.android.tools.idea.gradle.project.sync.ng.NewGradleSync.isSingleVariantSync;
import static com.android.tools.idea.gradle.project.sync.setup.post.PostSyncProjectSetup.createProjectSetupFromCacheTaskWithStartMessage;
import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradleExecutionSettings;
import static com.intellij.openapi.externalSystem.model.ProjectKeys.MODULE;
import static com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter.NULL_OBJECT;
import static com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType.RESOLVE_PROJECT;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.*;
import static com.intellij.openapi.externalSystem.util.ExternalSystemUtil.refreshProject;
import static java.lang.System.currentTimeMillis;

public class IdeaGradleSync implements GradleSync {
  private static final boolean SYNC_WITH_CACHED_MODEL_ONLY =
    SystemProperties.getBooleanProperty("studio.sync.with.cached.model.only", false);

  @NotNull private final Project myProject;
  @NotNull private final GradleProjectInfo myProjectInfo;

  @NotNull public static final Key<GradleSyncListener> LISTENER_KEY = new Key<>("GradleSyncListener");
  @NotNull public static final Key<Boolean> SOURCE_GENERATION_KEY = new Key<>("android.sourcegeneration.enabled");
  @NotNull public static final Key<Boolean> SINGLE_VARIANT_KEY = new Key<>("android.singlevariant.enabled");

  public IdeaGradleSync(@NotNull Project project) {
    this(project, GradleProjectInfo.getInstance(project));
  }

  private IdeaGradleSync(@NotNull Project project, @NotNull GradleProjectInfo projectInfo) {
    myProject = project;
    myProjectInfo = projectInfo;
  }

  @Override
  public void sync(@NotNull GradleSyncInvoker.Request request, @Nullable GradleSyncListener listener) {
    if (myProjectInfo.isNewProject()) {
      registerAsNewProject(myProject);
    }

    if (SYNC_WITH_CACHED_MODEL_ONLY || request.useCachedGradleModels) {
      ProjectBuildFileChecksums buildFileChecksums = ProjectBuildFileChecksums.findFor((myProject));
      if (buildFileChecksums != null && buildFileChecksums.canUseCachedData()) {
        DataNodeCaches dataNodeCaches = DataNodeCaches.getInstance(myProject);
        DataNode<ProjectData> cache = dataNodeCaches.getCachedProjectData();
        if (cache != null && !dataNodeCaches.isCacheMissingModels(cache) && !areCachedFilesMissing(myProject)) {
          PostSyncProjectSetup.Request setupRequest = new PostSyncProjectSetup.Request();
          setupRequest.usingCachedGradleModels = true;
          setupRequest.generateSourcesAfterSync = true;
          setupRequest.lastSyncTimestamp = buildFileChecksums.getLastGradleSyncTimestamp();

          setSkipAndroidPluginUpgrade(request, setupRequest);

          // Create a new taskId when using cache
          ExternalSystemTaskId taskId = createProjectSetupFromCacheTaskWithStartMessage(myProject);

          ProjectSetUpTask setUpTask = new ProjectSetUpTask(myProject, setupRequest, listener, true /* sync skipped */);
          setUpTask.onSuccess(taskId, cache);
          return;
        }
      }
    }

    // Setup the settings for setup.
    PostSyncProjectSetup.Request setupRequest = new PostSyncProjectSetup.Request();
    setupRequest.generateSourcesAfterSync = request.generateSourcesOnSuccess && !isCompoundSync(myProject);
    setupRequest.cleanProjectAfterSync = request.cleanProject;
    setupRequest.usingCachedGradleModels = false;

    // Setup the settings for the resolver.
    // We enable compound sync if we have been requested to generate sources and compound sync is enabled.
    boolean shouldUseCompoundSync = request.generateSourcesOnSuccess && isCompoundSync(myProject);
    // We also pass through whether single variant sync should be enabled on the resolver, this allows fetchGradleModels to turn this off
    boolean shouldUseSingleVariantSync = !request.forceFullVariantsSync && isSingleVariantSync(myProject);
    // We also need to pass the listener so that the callbacks can be used
    setProjectUserDataForAndroidGradleProjectResolver(shouldUseCompoundSync, shouldUseSingleVariantSync, listener);

    setSkipAndroidPluginUpgrade(request, setupRequest);

    // the sync should be aware of multiple linked gradle project with a single IDE project
    // and a linked gradle project can be located not in the IDE Project.baseDir
    Set<String> androidProjectCandidatesPaths = new LinkedHashSet<>();
    if (myProjectInfo.isImportedProject()) {
      createGradleProjectSettingsIfNotExist(myProject);
      Collection<GradleProjectSettings> projectsSettings = GradleSettings.getInstance(myProject).getLinkedProjectsSettings();
      if (projectsSettings.size() == 1) {
        androidProjectCandidatesPaths.add(projectsSettings.iterator().next().getExternalProjectPath());
      }
    }
    else {
      for (Module module : ProjectFacetManager.getInstance(myProject).getModulesWithFacet(GradleFacet.getFacetTypeId())) {
        String projectPath = getExternalRootProjectPath(module);
        if (projectPath != null) {
          androidProjectCandidatesPaths.add(projectPath);
        }
      }
    }
    if (androidProjectCandidatesPaths.isEmpty()) {
      // try to discover the project in the IDE project base dir.
      String externalProjectPath = toCanonicalPath(getBaseDirPath(myProject).getPath());
      if (new File(externalProjectPath, SdkConstants.FN_BUILD_GRADLE).isFile() ||
          new File(externalProjectPath, SdkConstants.FN_SETTINGS_GRADLE).isFile()) {
        androidProjectCandidatesPaths.add(externalProjectPath);
      }
    }

    if (androidProjectCandidatesPaths.isEmpty()) {
      GradleSyncState.getInstance(myProject).syncSkipped(currentTimeMillis(), listener);
      return;
    }

    for (String rootPath : androidProjectCandidatesPaths) {
      ProjectSetUpTask setUpTask = new ProjectSetUpTask(myProject, setupRequest, listener, false);
      ProgressExecutionMode executionMode = request.getProgressExecutionMode();
      refreshProject(myProject, GRADLE_SYSTEM_ID, rootPath, setUpTask, false /* resolve dependencies */,
                     executionMode, true /* always report import errors */);
    }
  }

  public static void createGradleProjectSettingsIfNotExist(@NotNull Project project) {
    GradleSettings gradleSettings = GradleSettings.getInstance(project);
    Collection<GradleProjectSettings> projectsSettings = gradleSettings.getLinkedProjectsSettings();
    if (projectsSettings.isEmpty()) {
      GradleProjectOpenProcessor
        projectOpenProcessor = ProjectOpenProcessor.EXTENSION_POINT_NAME.findExtensionOrFail(GradleProjectOpenProcessor.class);
      if (project.getBasePath() != null && projectOpenProcessor.canOpenProject(project.getBaseDir())) {
        GradleProjectSettings projectSettings = new GradleProjectSettings();
        String externalProjectPath = toCanonicalPath(project.getBasePath());
        projectSettings.setExternalProjectPath(externalProjectPath);
        gradleSettings.setLinkedProjectsSettings(Collections.singletonList(projectSettings));
      }
    }
  }

  private static void setSkipAndroidPluginUpgrade(@NotNull GradleSyncInvoker.Request syncRequest,
                                                  @NotNull PostSyncProjectSetup.Request setupRequest) {
    if (ApplicationManager.getApplication().isUnitTestMode() && syncRequest.skipAndroidPluginUpgrade) {
      setupRequest.skipAndroidPluginUpgrade = true;
    }
  }

  /**
   * This method sets up the information to be used by the AndroidGradleProjectResolver.
   * We use the projects user data as a way of passing this information across since the resolver is create by the
   * external system infrastructure.
   *
   * @param shouldGenerateSources whether or not sources should be generated
   * @param singleVariant whether or not only a single variant should be synced
   * @param listener the listener that is being used for the current sync.
   */
  private void setProjectUserDataForAndroidGradleProjectResolver(boolean shouldGenerateSources,
                                                                 boolean singleVariant,
                                                                 @Nullable GradleSyncListener listener) {
    myProject.putUserData(SOURCE_GENERATION_KEY, shouldGenerateSources);
    myProject.putUserData(SINGLE_VARIANT_KEY, singleVariant);
    myProject.putUserData(LISTENER_KEY, listener);
  }

  @Override
  @NotNull
  public List<GradleModuleModels> fetchGradleModels(@NotNull ProgressIndicator indicator) {
    GradleExecutionSettings settings = getGradleExecutionSettings(myProject);
    ExternalSystemTaskId id = ExternalSystemTaskId.create(GRADLE_SYSTEM_ID, RESOLVE_PROJECT, myProject);
    String projectPath = myProject.getBasePath();
    assert projectPath != null;

    setProjectUserDataForAndroidGradleProjectResolver(false, false, null);

    GradleProjectResolver projectResolver = new GradleProjectResolver();
    DataNode<ProjectData> projectDataNode = projectResolver.resolveProjectInfo(id, projectPath, false, settings, NULL_OBJECT);

    ImmutableList.Builder<GradleModuleModels> builder = ImmutableList.builder();

    if (projectDataNode != null) {
      Collection<DataNode<ModuleData>> moduleNodes = findAll(projectDataNode, MODULE);
      for (DataNode<ModuleData> moduleNode : moduleNodes) {
        DataNode<GradleModuleModel> gradleModelNode = find(moduleNode, GRADLE_MODULE_MODEL);
        if (gradleModelNode != null) {
          PsdModuleModels moduleModules = new PsdModuleModels(moduleNode.getData().getExternalName());
          moduleModules.addModel(GradleModuleModel.class, gradleModelNode.getData());

          DataNode<AndroidModuleModel> androidModelNode = find(moduleNode, ANDROID_MODEL);
          if (androidModelNode != null) {
            moduleModules.addModel(AndroidModuleModel.class, androidModelNode.getData());

            DataNode<NdkModuleModel> ndkModelNode = find(moduleNode, NDK_MODEL);
            if (ndkModelNode != null) {
              moduleModules.addModel(NdkModuleModel.class, ndkModelNode.getData());
            }

            builder.add(moduleModules);
            continue;
          }

          DataNode<JavaModuleModel> javaModelNode = find(moduleNode, JAVA_MODULE_MODEL);
          if (javaModelNode != null) {
            moduleModules.addModel(JavaModuleModel.class, javaModelNode.getData());

            builder.add(moduleModules);
          }
        }
      }
    }

    return builder.build();
  }
}
