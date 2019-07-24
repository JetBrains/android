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

import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.ANDROID_MODEL;
import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.GRADLE_MODULE_MODEL;
import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.JAVA_MODULE_MODEL;
import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.NDK_MODEL;
import static com.android.tools.idea.gradle.project.sync.setup.post.PostSyncProjectSetup.createProjectSetupFromCacheTaskWithStartMessage;
import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradleExecutionSettings;
import static com.intellij.openapi.externalSystem.model.ProjectKeys.MODULE;
import static com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter.NULL_OBJECT;
import static com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType.RESOLVE_PROJECT;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.find;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.findAll;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.toCanonicalPath;
import static com.intellij.openapi.externalSystem.util.ExternalSystemUtil.refreshProject;
import static com.intellij.openapi.roots.OrderRootType.CLASSES;
import static java.lang.System.currentTimeMillis;
import static java.util.Arrays.asList;

import com.android.annotations.concurrency.WorkerThread;
import com.android.tools.idea.gradle.project.ProjectBuildFileChecksums;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.project.model.JavaModuleModel;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.project.sync.GradleModuleModels;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.PsdModuleModels;
import com.android.tools.idea.gradle.project.sync.idea.data.DataNodeCaches;
import com.android.tools.idea.gradle.project.sync.setup.post.PostSyncProjectSetup;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.projectImport.ProjectOpenProcessor;
import com.intellij.util.SystemProperties;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.project.GradleProjectOpenProcessor;
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolver;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

public class GradleSyncExecutor {
  private static final boolean SYNC_WITH_CACHED_MODEL_ONLY =
    SystemProperties.getBooleanProperty("studio.sync.with.cached.model.only", false);

  @NotNull private final Project myProject;

  @NotNull public static final Key<GradleSyncListener> LISTENER_KEY = new Key<>("GradleSyncListener");
  @NotNull public static final Key<Boolean> SOURCE_GENERATION_KEY = new Key<>("android.sourcegeneration.enabled");
  @NotNull public static final Key<Boolean> SINGLE_VARIANT_KEY = new Key<>("android.singlevariant.enabled");

  public GradleSyncExecutor(@NotNull Project project) {
    myProject = project;
  }

  @WorkerThread
  public void sync(@NotNull GradleSyncInvoker.Request request, @Nullable GradleSyncListener listener) {
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

          ProjectSetUpTask setUpTask = new ProjectSetUpTask(myProject, setupRequest, listener);
          if (ApplicationManager.getApplication().isUnitTestMode()) {
            setUpTask.onSuccess(taskId, cache);
          }
          else {
            ApplicationManager.getApplication().executeOnPooledThread(() -> setUpTask.onSuccess(taskId, cache));
          }
          return;
        }
      }
    }

    // Setup the settings for setup.
    PostSyncProjectSetup.Request setupRequest = new PostSyncProjectSetup.Request();
    setupRequest.generateSourcesAfterSync = request.generateSourcesOnSuccess && !GradleSyncState.isCompoundSync();
    setupRequest.cleanProjectAfterSync = request.cleanProject;
    setupRequest.usingCachedGradleModels = false;

    // Setup the settings for the resolver.
    // We enable compound sync if we have been requested to generate sources and compound sync is enabled.
    boolean shouldUseCompoundSync = request.generateSourcesOnSuccess && GradleSyncState.isCompoundSync();
    // We also pass through whether single variant sync should be enabled on the resolver, this allows fetchGradleModels to turn this off
    boolean shouldUseSingleVariantSync = !request.forceFullVariantsSync && GradleSyncState.isSingleVariantSync();
    // We also need to pass the listener so that the callbacks can be used
    setProjectUserDataForAndroidGradleProjectResolver(shouldUseCompoundSync, shouldUseSingleVariantSync, listener);

    setSkipAndroidPluginUpgrade(request, setupRequest);

    // the sync should be aware of multiple linked gradle project with a single IDE project
    // and a linked gradle project can be located not in the IDE Project.baseDir
    createGradleProjectSettingsIfNotExist(myProject);
    Set<String> androidProjectCandidatesPaths = GradleSettings.getInstance(myProject)
      .getLinkedProjectsSettings()
      .stream()
      .map(ExternalProjectSettings::getExternalProjectPath)
      .collect(Collectors.toSet());

    if (androidProjectCandidatesPaths.isEmpty()) {
      GradleSyncState.getInstance(myProject).syncSkipped(currentTimeMillis(), listener);
      return;
    }

    for (String rootPath : androidProjectCandidatesPaths) {
      ProjectSetUpTask setUpTask = new ProjectSetUpTask(myProject, setupRequest, listener);
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
   * @param singleVariant         whether or not only a single variant should be synced
   * @param listener              the listener that is being used for the current sync.
   */
  private void setProjectUserDataForAndroidGradleProjectResolver(boolean shouldGenerateSources,
                                                                 boolean singleVariant,
                                                                 @Nullable GradleSyncListener listener) {
    myProject.putUserData(SOURCE_GENERATION_KEY, shouldGenerateSources);
    myProject.putUserData(SINGLE_VARIANT_KEY, singleVariant);
    myProject.putUserData(LISTENER_KEY, listener);
  }

  @NotNull
  public List<GradleModuleModels> fetchGradleModels() {
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

  /**
   * @return true if the expected jars from cached libraries don't exist on disk.
   */
  private static boolean areCachedFilesMissing(@NotNull Project project) {
    final Ref<Boolean> missingFileFound = Ref.create(false);
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
      rootManager.orderEntries().withoutModuleSourceEntries().withoutDepModules().forEach(entry -> {
        for (OrderRootType type : OrderRootType.getAllTypes()) {
          List<String> expectedUrls = asList(entry.getUrls(type));
          // CLASSES root contains jar file and res folder, and none of them are guaranteed to exist. Fail validation only if
          // all files are missing.
          if (type.equals(CLASSES)) {
            if (expectedUrls.stream().noneMatch(url -> VirtualFileManager.getInstance().findFileByUrl(url) != null)) {
              missingFileFound.set(true);
              return false; // Don't continue with processor.
            }
          }
          // For other types of root, fail validation if any file is missing. This includes annotation processor, sources and javadoc.
          else {
            if (expectedUrls.stream().anyMatch(url -> VirtualFileManager.getInstance().findFileByUrl(url) == null)) {
              missingFileFound.set(true);
              return false; // Don't continue with processor.
            }
          }
        }
        return true;
      });
      if (missingFileFound.get()) {
        return true;
      }
    }
    return missingFileFound.get();
  }
}
