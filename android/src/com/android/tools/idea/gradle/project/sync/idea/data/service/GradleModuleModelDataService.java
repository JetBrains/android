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
package com.android.tools.idea.gradle.project.sync.idea.data.service;

import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.GRADLE_MODULE_MODEL;
import static com.android.tools.idea.gradle.project.sync.setup.Facets.removeAllFacets;
import static com.android.tools.idea.gradle.project.sync.setup.module.ModuleFinder.EXTRA_BUILD_PARTICIPANT_FROM_BUILD_SRC;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.getChildren;
import static com.intellij.openapi.util.text.StringUtil.equalsIgnoreCase;
import static org.jetbrains.plugins.gradle.service.project.GradleBuildSrcProjectsResolver.BUILD_SRC_MODULE_PROPERTY;
import static org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil.BUILD_SRC_NAME;

import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.project.sync.setup.module.GradleModuleSetup;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import java.util.Collection;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.data.BuildParticipant;

/**
 * Applies Gradle settings to the modules of an Android project.
 */
public class GradleModuleModelDataService extends ModuleModelDataService<GradleModuleModel> {
  @NotNull private final GradleModuleSetup myModuleSetup;

  @SuppressWarnings("unused") // Instantiated by IDEA
  public GradleModuleModelDataService() {
    this(new GradleModuleSetup());
  }

  @VisibleForTesting
  GradleModuleModelDataService(@NotNull GradleModuleSetup moduleSetup) {
    myModuleSetup = moduleSetup;
  }

  @Override
  @NotNull
  public Key<GradleModuleModel> getTargetDataKey() {
    return GRADLE_MODULE_MODEL;
  }

  @Override
  protected void importData(@NotNull Collection<? extends DataNode<GradleModuleModel>> toImport,
                            @NotNull Project project,
                            @NotNull IdeModifiableModelsProvider modelsProvider,
                            @NotNull Map<String, DataNode<GradleModuleModel>> modelsByModuleName) {
    for (Module module : modelsProvider.getModules()) {
      DataNode<GradleModuleModel> gradleModuleModelDataNode = modelsByModuleName.get(module.getName());
      if (gradleModuleModelDataNode == null) {
        // This happens when there is an orphan IDEA module that does not map to a Gradle project. One way for this to happen is when
        // opening a project created in another machine, and Gradle import assigns a different name to a module. Then, user decides
        // not to delete the orphan module when Studio prompts to do so.
        removeAllFacets(modelsProvider.getModifiableFacetModel(module), GradleFacet.getFacetTypeId());      }
      else {
        myModuleSetup.setUpModule(module, modelsProvider, gradleModuleModelDataNode.getData());
      }
    }
    // Create extra build participant for modules from build src. This will be used to locate the IDE module from dependency module in ModuleFinder.
    // For example, if module A -> B, and B comes from buildSrc. Then the dependency A has will be [PathToBuildSrcFolder]:[GradlePathOfModuleB].
    // In this case, a build participant will be created, which has [PathToBuildSrcFolder] as the project path, and [PathToModuleB] as one of the projects.
    populateExtraBuildParticipantFromBuildSrc(toImport, project);
  }

  private static void populateExtraBuildParticipantFromBuildSrc(@NotNull Collection<? extends DataNode<GradleModuleModel>> toImport,
                                                                @NotNull Project project) {
    if (toImport.isEmpty()) {
      return;
    }
    // Find root project based on the first node, since all DataNode in the collection should belong to the same project.
    DataNode<ProjectData> projectDataDataNode = findProjectDataNode(toImport.iterator().next());
    if (projectDataDataNode != null) {
      BuildParticipant participant = getParticipant(projectDataDataNode);
      if (!participant.getProjects().isEmpty()) {
        project.putUserData(EXTRA_BUILD_PARTICIPANT_FROM_BUILD_SRC, participant);
      }
    }
  }

  /**
   * Get BuildParticipant from buildSrc of the given project data node.
   * If buildSrc doesn't exist, it returns a {@link BuildParticipant} without projects.
   */
  @NotNull
  private static BuildParticipant getParticipant(@NotNull DataNode<ProjectData> projectDataDataNode) {
    BuildParticipant participant = new BuildParticipant();
    participant.setRootProjectName(projectDataDataNode.getData().getExternalName());
    for (DataNode<ModuleData> moduleNode : getChildren(projectDataDataNode, ProjectKeys.MODULE)) {
      if (equalsIgnoreCase("true", moduleNode.getData().getProperty(BUILD_SRC_MODULE_PROPERTY))) {
        String moduleFolder = moduleNode.getData().getLinkedExternalProjectPath();
        participant.getProjects().add(moduleFolder);
        if (BUILD_SRC_NAME.equals(moduleNode.getData().getExternalName())) {
          participant.setRootPath(moduleFolder);
        }
      }
    }
    return participant;
  }

  /**
   * Find the ProjectData DataNode from GradleModuleModel.
   * DataNode structure:
   * ProjectData - ModuleData - GradleModuleModel.
   */
  @Nullable
  private static DataNode<ProjectData> findProjectDataNode(@NotNull DataNode<GradleModuleModel> gradleModuleDataNode) {
    DataNode<?> moduleDataNode = gradleModuleDataNode.getParent();
    if (moduleDataNode != null) {
      DataNode<?> projectDataNode = moduleDataNode.getParent();
      if (projectDataNode != null) {
        return projectDataNode.getDataNode(ProjectKeys.PROJECT);
      }
    }
    return null;
  }
}
