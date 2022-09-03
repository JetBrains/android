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

import static com.android.tools.idea.gradle.project.sync.GradleSyncStateImplKt.setProjectSyncRequest;
import static com.android.tools.idea.gradle.project.sync.idea.AndroidGradleProjectResolverKeys.REQUESTED_PROJECT_RESOLUTION_MODE_KEY;
import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.ANDROID_MODEL;
import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.GRADLE_MODULE_MODEL;
import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.NATIVE_VARIANTS;
import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.NDK_MODEL;
import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.SYNC_ISSUE;
import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradleExecutionSettings;
import static com.intellij.openapi.externalSystem.model.ProjectKeys.MODULE;
import static com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter.NULL_OBJECT;
import static com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType.RESOLVE_PROJECT;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.find;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.findAll;
import static com.intellij.openapi.externalSystem.util.ExternalSystemUtil.refreshProject;
import static org.jetbrains.plugins.gradle.util.GradleConstants.SYSTEM_ID;

import com.android.annotations.concurrency.UiThread;
import com.android.annotations.concurrency.WorkerThread;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.model.IdeSyncIssue;
import com.android.tools.idea.gradle.model.impl.IdeResolvedLibraryTable;
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet;
import com.android.tools.idea.gradle.project.model.GradleAndroidModelData;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.project.sync.GradleModuleModels;
import com.android.tools.idea.gradle.project.sync.GradleProjectModels;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.project.sync.GradleSyncStateHolder;
import com.android.tools.idea.gradle.project.sync.PsdModuleModels;
import com.android.tools.idea.gradle.project.sync.SelectedVariantCollector;
import com.android.tools.idea.gradle.project.sync.SelectedVariants;
import com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys;
import com.android.tools.idea.gradle.project.sync.issues.SyncIssues;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder;
import com.intellij.openapi.externalSystem.importing.ProjectResolverPolicy;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.project.dependencies.ProjectDependencies;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;
import org.jetbrains.annotations.VisibleForTesting;
import org.jetbrains.plugins.gradle.model.ExternalProject;
import org.jetbrains.plugins.gradle.service.project.GradlePartialResolverPolicy;
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolver;
import org.jetbrains.plugins.gradle.service.project.data.ExternalProjectDataCache;
import org.jetbrains.plugins.gradle.service.project.open.GradleProjectImportUtil;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleJvmResolutionUtil;

public class GradleSyncExecutor {
  @NotNull private final Project myProject;

  @NotNull public static final Key<Boolean> ALWAYS_SKIP_SYNC = new Key<>("android.always.skip.sync");
  @VisibleForTesting @NotNull public static final Key<GradleSyncInvoker.Request> SKIPPED_SYNC = new Key<>("android.skipped.sync");

  public GradleSyncExecutor(@NotNull Project project) {
    myProject = project;
  }

  @UiThread
  public void sync(@NotNull GradleSyncInvoker.Request request, @Nullable GradleSyncListener listener) {
    if (Objects.equals(myProject.getUserData(ALWAYS_SKIP_SYNC), true)) {
      if (myProject.getUserData(SKIPPED_SYNC) != null) throw new IllegalStateException("Skipped sync request already present");
      myProject.putUserData(SKIPPED_SYNC, request);
      GradleSyncStateHolder.getInstance(myProject).syncSkipped(listener);
      return;
    }

    // the sync should be aware of multiple linked gradle project with a single IDE project
    // and a linked gradle project can be located not in the IDE Project.baseDir
    // FYI: some info on linked projects: https://www.jetbrains.com/help/idea/gradle.html#link_gradle_project
    Collection<String> androidProjectCandidatesPaths = GradleSettings.getInstance(myProject)
      .getLinkedProjectsSettings()
      .stream()
      .map(ExternalProjectSettings::getExternalProjectPath)
      .sorted() // Entries come in a hash-code order. Launch sync in some stable order.
      .collect(Collectors.toList());

    // We have no Gradle project linked, attempt to link one using Intellijs Projects root path.
    if (androidProjectCandidatesPaths.isEmpty()) {
      // auto-discovery of the gradle project located in the IDE Project.basePath can not be applied to IntelliJ IDEA
      // because IDEA still supports working with gradle projects w/o built-in gradle integration
      // (e.g. using generated project by 'idea' gradle plugin)
      if (IdeInfo.getInstance().isAndroidStudio()) {
        String foundPath = attemptToLinkGradleProject(myProject);
        if (foundPath != null) {
          androidProjectCandidatesPaths.add(foundPath);
        }
        else {
          // Linking failed.
          GradleSyncStateHolder.getInstance(myProject).syncSkipped(listener);
          return;
        }
      }
    }

    for (String rootPath : androidProjectCandidatesPaths) {
      ProjectSetUpTask setUpTask = new ProjectSetUpTask(myProject, listener);
      ProgressExecutionMode executionMode = request.getProgressExecutionMode();
      ImportSpecBuilder builder = new ImportSpecBuilder(myProject, GRADLE_SYSTEM_ID).callback(setUpTask).use(executionMode);
      if (request.getDontFocusSyncFailureOutput()) builder.dontReportRefreshErrors();
      setProjectSyncRequest(myProject, rootPath, request);
      refreshProject(rootPath, builder.build());
    }
  }

  /**
   * Attempts to find and link a Gradle project based at the current Project's base path.
   * <p>
   * This method should only be called when running and Android Studio since intellij needs to support legacy Gradle projects
   * which should not be linked via the ExternalSystem API.
   *
   * @param project the current project
   * @return the canonical path to the project that has just been linked if successful, null otherwise.
   */
  @Nullable
  public static String attemptToLinkGradleProject(@NotNull Project project) {
    @SystemIndependent String projectBasePath = project.getBasePath();
    // We can't link anything if we have no path
    if (projectBasePath == null) {
      return null;
    }

    String externalProjectPath = ExternalSystemApiUtil.toCanonicalPath(projectBasePath);
    VirtualFile projectRootFolder = project.getBaseDir();

    if (!GradleProjectImportUtil.canOpenGradleProject(projectRootFolder)) {
      return null;
    }

    GradleProjectSettings projectSettings = new GradleProjectSettings();
    GradleProjectImportUtil.setupGradleSettings(GradleSettings.getInstance(project));
    GradleProjectImportUtil.setupGradleProjectSettings(projectSettings, project, new File(externalProjectPath).toPath());
    GradleJvmResolutionUtil.setupGradleJvm(project, projectSettings, projectSettings.resolveGradleVersion());
    //noinspection unchecked
    ExternalSystemApiUtil.getSettings(project, SYSTEM_ID).linkProject(projectSettings);
    return externalProjectPath;
  }

  @WorkerThread
  @NotNull
  public GradleProjectModels fetchGradleModels() {
    GradleExecutionSettings settings = getGradleExecutionSettings(myProject);
    if (settings == null) {
      throw new IllegalStateException("Cannot obtain GradleExecutionSettings");
    }
    ExternalSystemTaskId id = ExternalSystemTaskId.create(GRADLE_SYSTEM_ID, RESOLVE_PROJECT, myProject);
    String projectPath = myProject.getBasePath();
    assert projectPath != null;

    DataNode<ProjectData> projectDataNode;

    settings.putUserData(REQUESTED_PROJECT_RESOLUTION_MODE_KEY,
                         ProjectResolutionMode.FetchAllVariantsMode.INSTANCE);
    GradleProjectResolver projectResolver = new GradleProjectResolver();
    projectDataNode = projectResolver.resolveProjectInfo(id, projectPath, false, settings, NULL_OBJECT);
    ImmutableList.Builder<GradleModuleModels> builder = ImmutableList.builder();
    @Nullable IdeResolvedLibraryTable libraryTable = null;

    if (projectDataNode != null) {
      @SuppressWarnings("UnstableApiUsage") DataNode<ExternalProject> rootProjectNode = find(projectDataNode, ExternalProjectDataCache.KEY);
      DataNode<IdeResolvedLibraryTable> libraryTableNode = find(projectDataNode, AndroidProjectKeys.IDE_LIBRARY_TABLE);
      if (libraryTableNode != null) {
        libraryTable = libraryTableNode.getData();
      }
      Collection<DataNode<ModuleData>> moduleNodes = findAll(projectDataNode, MODULE);
      for (DataNode<ModuleData> moduleNode : moduleNodes) {
        DataNode<GradleModuleModel> gradleModelNode = find(moduleNode, GRADLE_MODULE_MODEL);
        if (gradleModelNode == null) {
          continue;
        }

        PsdModuleModels moduleModules = new PsdModuleModels(moduleNode.getData().getExternalName());
        moduleModules.addModel(GradleModuleModel.class, gradleModelNode.getData());

        @NotNull Collection<DataNode<IdeSyncIssue>> syncIssueNodes = findAll(moduleNode, SYNC_ISSUE);
        if (!syncIssueNodes.isEmpty()) {
          moduleModules.addModel(SyncIssues.class, new SyncIssues(ContainerUtil.map(syncIssueNodes, DataNode::getData)));
        }

        DataNode<GradleAndroidModelData> androidModelNode = find(moduleNode, ANDROID_MODEL);
        if (androidModelNode != null) {
          moduleModules.addModel(GradleAndroidModelData.class, androidModelNode.getData());

          DataNode<NdkModuleModel> ndkModelNode = find(moduleNode, NDK_MODEL);
          if (ndkModelNode != null) {
            moduleModules.addModel(NdkModuleModel.class, ndkModelNode.getData());
          }
        }

        if (rootProjectNode != null) {
          ExternalProject project = findExternalProjectForModule(rootProjectNode, moduleNode);
          if (project != null) {
            moduleModules.addModel(ExternalProject.class, project);
          }
          DataNode<ProjectDependencies> dependenciesNode = find(moduleNode, ProjectKeys.DEPENDENCIES_GRAPH);
          if (dependenciesNode != null) {
            moduleModules.addModel(ProjectDependencies.class, dependenciesNode.getData());
          }
        }
        builder.add(moduleModules);
      }
    }

    return new GradleProjectModels(builder.build(), libraryTable);
  }

  @Nullable
  private ExternalProject findExternalProjectForModule(@NotNull DataNode<ExternalProject> rootProjectNode,
                                                       @NotNull DataNode<ModuleData> moduleNode) {
    String[] moduleIdParts = moduleNode.getData().getId().split(":");
    ExternalProject project = rootProjectNode.getData();
    for (String idPart : moduleIdParts) {
      if (idPart.isEmpty()) {
        continue;
      }

      project = project.getChildProjects().get(idPart);
      if (project == null) {
        Logger.getInstance(GradleSyncExecutor.class)
          .warn("Could not find ExternalProject for " + moduleNode.getData().getId(), new Throwable());
        return null;
      }
    }
    return project;
  }

  @WorkerThread
  public void fetchAndMergeNativeVariants(@NotNull Set<@NotNull String> requestedAbis) {
    SelectedVariantCollector variantCollector = new SelectedVariantCollector(myProject);
    SelectedVariants selectedVariants = variantCollector.collectSelectedVariants();
    GradleExecutionSettings settings = getGradleExecutionSettings(myProject);
    if (settings == null) {
      throw new IllegalStateException("Cannot obtain GradleExecutionSettings");
    }

    Map<String, String> variantsByNativeModule =
      selectedVariants.getSelectedVariants().values().stream()
        .filter(it -> it.getAbiName() != null)
        .collect(Collectors.toMap(it -> it.getModuleId(), it -> it.getVariantName()));

    settings.putUserData(REQUESTED_PROJECT_RESOLUTION_MODE_KEY,
                         new ProjectResolutionMode.FetchNativeVariantsMode(variantsByNativeModule, requestedAbis));
    ExternalSystemTaskId id = ExternalSystemTaskId.create(GRADLE_SYSTEM_ID, RESOLVE_PROJECT, myProject);
    String projectPath = myProject.getBasePath();
    assert projectPath != null;

    GradleProjectResolver projectResolver = new GradleProjectResolver();
    ProjectResolverPolicy projectResolverPolicy = new GradlePartialResolverPolicy(it -> it instanceof AndroidGradleProjectResolverMarker);
    DataNode<ProjectData> projectDataNode =
      projectResolver.resolveProjectInfo(id, projectPath, false, settings, projectResolverPolicy, NULL_OBJECT);
    if (projectDataNode == null) {
      Logger.getInstance(GradleSyncExecutor.class).warn("Failed to retrieve native variant models.");
      return;
    }
    @NotNull Collection<DataNode<IdeAndroidNativeVariantsModelsWrapper>> nativeVariants = findAll(projectDataNode, NATIVE_VARIANTS);
    Map<String, Module> moduleMap = Stream.of(ModuleManager.getInstance(myProject).getModules())
      .filter(it -> ExternalSystemApiUtil.isExternalSystemAwareModule(SYSTEM_ID, it))
      .collect(Collectors.toMap(it -> ExternalSystemApiUtil.getExternalProjectId(it), it -> it));
    for (DataNode<IdeAndroidNativeVariantsModelsWrapper> nativeVariantsWrapperNode : nativeVariants) {
      IdeAndroidNativeVariantsModelsWrapper nativeVariantsWrapper = nativeVariantsWrapperNode.getData();
      String moduleId = nativeVariantsWrapper.getModuleId();
      Module module = moduleMap.get(moduleId);
      if (module == null){
        Logger.getInstance(GradleSyncExecutor.class).error("Module not found. ModuleId: " + moduleId);
        continue;
      }
      NdkFacet ndkFacet = NdkFacet.getInstance(module);
      if (ndkFacet == null){
        Logger.getInstance(GradleSyncExecutor.class).error("NdkFacet not found. ModuleId: " + moduleId);
        continue;
      }
      nativeVariantsWrapper.mergeInto(ndkFacet);
    }
  }
}
