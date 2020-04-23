/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.project;

import static com.android.tools.idea.gradle.project.sync.idea.GradleSyncExecutor.areCachedFilesMissing;
import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.ANDROID_MODEL;
import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.GRADLE_MODULE_MODEL;
import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.JAVA_MODULE_MODEL;
import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.NDK_MODEL;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_PROJECT_NEW;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_PROJECT_REOPEN;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.findAllRecursively;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.getChildren;

import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.facet.java.JavaFacet;
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.idea.data.DataNodeCaches;
import com.android.tools.idea.gradle.util.AndroidStudioPreferences;
import com.android.tools.idea.model.AndroidModel;
import com.google.wireless.android.sdk.stats.GradleSyncStats;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.util.Producer;
import com.intellij.util.containers.ContainerUtil;
import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

/**
 * Syncs Android Gradle project with the persisted project data on startup.
 */
public class AndroidGradleProjectStartupActivity implements StartupActivity {
  @Override
  public void runActivity(@NotNull Project project) {
    GradleProjectInfo gradleProjectInfo = GradleProjectInfo.getInstance(project);
    if ((
      // We only request sync if we know this is an Android project.

      // Opening an IDEA project with Android modules (AS and IDEA - i.e. previously synced).
      !gradleProjectInfo.getAndroidModules().isEmpty()
      // Opening a Gradle project with .idea but no .iml files or facets (Typical for AS but not in IDEA)
      || IdeInfo.getInstance().isAndroidStudio() && gradleProjectInfo.isBuildWithGradle()
      // Opening a project without .idea directory (including a newly created).
      || gradleProjectInfo.isImportedProject()
        ) &&
        !gradleProjectInfo.isSkipStartupActivity()) {
      attachCachedModelsOrTriggerSync(project, gradleProjectInfo);
    }
    gradleProjectInfo.setSkipStartupActivity(false);

    // Disable all settings sections that we don't want to be present in Android Studio.
    // See AndroidStudioPreferences for a full list.
    AndroidStudioPreferences.cleanUpPreferences(project);
  }

  /**
   * Attempts to see if the models cached by IDEAs external system are valid, if they are then we attach them to the facet,
   * if they are not then we request a project sync in order to ensure that the IDE has access to all of models it needs to function.
   */
  private static void attachCachedModelsOrTriggerSync(@NotNull Project project, GradleProjectInfo gradleProjectInfo) {
    Collection<String> linkedProjectPaths = GradleSettings.getInstance(project).getLinkedProjectsSettings().stream().map(
      GradleProjectSettings::getExternalProjectPath).collect(Collectors.toSet());

    Collection<DataNode<ProjectData>> projectsData = ContainerUtil.mapNotNull(linkedProjectPaths, path -> {
      ExternalProjectInfo settings = ProjectDataManager.getInstance().getExternalProjectData(project, GradleConstants.SYSTEM_ID, path);
      if (settings == null) {
        return null;
      }

      return settings.getExternalProjectStructure();
    });

    GradleSyncStats.Trigger trigger = gradleProjectInfo.isNewProject() ? TRIGGER_PROJECT_NEW : TRIGGER_PROJECT_REOPEN;
    GradleSyncInvoker.Request request = new GradleSyncInvoker.Request(trigger);
    DataNodeCaches caches = DataNodeCaches.getInstance(project);
    // If we don't have any cached data then do a full re-sync
    if (projectsData.isEmpty() ||
        projectsData.stream().anyMatch(data -> caches.isCacheMissingModels(data)) ||
        areCachedFilesMissing(project)) {
      GradleSyncInvoker.getInstance().requestProjectSync(project, request);
    }
    else {
      // Otherwise attach the models we found from the cache.
      Logger.getInstance(AndroidGradleProjectStartupActivity.class)
        .info("Up-to-date models found in the cache. Not invoking Gradle sync.");
      GradleSyncState syncState = GradleSyncState.getInstance(project);
      syncState.syncStarted(request, null);
      projectsData.forEach((data) -> {
        attachModelsToFacets(project, data);
      });
      ProjectStructure.getInstance(project).analyzeProjectStructure();
      syncState.syncSkipped(null);
    }
  }

  private static void attachModelsToFacets(@NotNull Project project, @NotNull DataNode<ProjectData> projectData) {
    Collection<DataNode<ModuleData>> moduleDataNodes = findAllRecursively(projectData, ProjectKeys.MODULE);
    moduleDataNodes.forEach((moduleDataNode) -> {
      Module module = ModuleManager.getInstance(project).findModuleByName(moduleDataNode.getData().getInternalName());
      if (module != null) {
        attachModelToFacet(moduleDataNode, ANDROID_MODEL, () -> AndroidFacet.getInstance(module), AndroidModel::set);
        attachModelToFacet(moduleDataNode, JAVA_MODULE_MODEL, () -> JavaFacet.getInstance(module), JavaFacet::setJavaModuleModel);
        attachModelToFacet(moduleDataNode, GRADLE_MODULE_MODEL, () -> GradleFacet.getInstance(module), GradleFacet::setGradleModuleModel);
        attachModelToFacet(moduleDataNode, NDK_MODEL, () -> NdkFacet.getInstance(module), NdkFacet::setNdkModuleModel);
      }
    });
  }

  private static <T, V> void attachModelToFacet(@NotNull DataNode<ModuleData> moduleDataNode,
                                                @NotNull Key<T> dataKey,
                                                @NotNull Producer<V> facetProducer,
                                                @NotNull BiConsumer<V, T> modelSetter) {
    Collection<DataNode<T>> dataNodes = getChildren(moduleDataNode, dataKey);
    if (!dataNodes.isEmpty()) {
      // There will only be one so taking the first is okay!
      T model = dataNodes.iterator().next().getData();
      V facet = facetProducer.produce();
      if (facet != null) {
        modelSetter.accept(facet, model);
      }
    }
  }

}
