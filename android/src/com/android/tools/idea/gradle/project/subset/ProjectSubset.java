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
package com.android.tools.idea.gradle.project.subset;

import com.android.tools.idea.gradle.IdeaGradleProject;
import com.android.tools.idea.gradle.project.GradleExperimentalSettings;
import com.android.tools.idea.gradle.util.Projects;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.util.*;

import static com.android.tools.idea.gradle.AndroidProjectKeys.IDE_GRADLE_PROJECT;
import static com.android.tools.idea.gradle.util.Projects.populate;
import static com.intellij.openapi.externalSystem.model.ProjectKeys.MODULE;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.find;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.findAll;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static com.intellij.util.ArrayUtil.toStringArray;

/**
 * A project subset is a feature where users can select the modules in a project to include when an existing project is imported into the
 * IDE. This feature is handy when users work with big projects (e.g. 300+ modules) but, in practice, modify sources in a few of them. A
 * smaller set of source code can make IDE's performance better (e.g. indexing and building.)
 */
public final class ProjectSubset {
  @NonNls private static final String PROJECT_SUBSET_PROPERTY_NAME = "com.android.studio.selected.modules.on.import";

  @NotNull private Project myProject;

  @NotNull
  public static ProjectSubset getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, ProjectSubset.class);
  }

  public ProjectSubset(@NotNull Project project) {
    myProject = project;
  }

  public static boolean isSettingEnabled() {
    return GradleExperimentalSettings.getInstance().SELECT_MODULES_ON_PROJECT_IMPORT;
  }

  public boolean hasCachedModules() {
    DataNode<ProjectData> projectData = getCachedProjectData();
    if (projectData != null) {
      Collection<DataNode<ModuleData>> modules = findAll(projectData, MODULE);
      return !modules.isEmpty();
    }
    return false;
  }

  public void addOrRemoveModules() {
    DataNode<ProjectData> projectData = getCachedProjectData();
    if (projectData != null)  {
      Collection<DataNode<ModuleData>> modules = findAll(projectData, MODULE);
      Collection<String> selectedModuleNames = Collections.emptySet();
      String[] selection = getSelection();
      if (selection != null) {
        selectedModuleNames = Sets.newHashSet(selection);
      }
      Collection<DataNode<ModuleData>> selectedModules = showModuleSelectionDialog(modules, selectedModuleNames);
      if (selectedModules != null) {
        setSelection(selectedModules);
        if (!Arrays.equals(getSelection(), selection)) {
          populate(myProject, selectedModules);
        }
      }
    }
  }

  public void findAndIncludeModules(@NotNull final Collection<String> moduleGradlePaths) {
    DataNode<ProjectData> projectData = getCachedProjectData();

    if (projectData != null) {
      final Project project = myProject;
      final Collection<DataNode<ModuleData>> modules = findAll(projectData, MODULE);

      new Task.Modal(project, "Finding Missing Modules", false) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {

          String[] storedSelection = getSelection();
          Set<String> currentSelection = storedSelection != null ? Sets.newHashSet(storedSelection) : Sets.<String>newHashSet();

          List<DataNode<ModuleData>> selectedModules = Lists.newArrayList();

          int doneCount = 0;
          for (DataNode<ModuleData> module : modules) {
            indicator.setFraction(++doneCount / modules.size());

            String name = module.getData().getExternalName();
            if (currentSelection.contains(name)) {
              selectedModules.add(module);
              continue;
            }
            DataNode<IdeaGradleProject> gradleProjectNode = find(module, IDE_GRADLE_PROJECT);
            if (gradleProjectNode != null) {
              IdeaGradleProject gradleProject = gradleProjectNode.getData();
              if (moduleGradlePaths.contains(gradleProject.getGradlePath())) {
                selectedModules.add(module);
              }
            }
          }
          if (!selectedModules.isEmpty()) {
            Projects.populate(project, selectedModules);
          }
        }
      }.queue();
    }
  }

  @Nullable
  private DataNode<ProjectData> getCachedProjectData() {
    ProjectDataManager dataManager = ProjectDataManager.getInstance();
    Collection<ExternalProjectInfo> projectsData = dataManager.getExternalProjectsData(myProject, GradleConstants.SYSTEM_ID);
    for (ExternalProjectInfo projectInfo : projectsData) {
      String projectPath = toSystemDependentName(projectInfo.getExternalProjectPath());
      if (projectPath.equals(myProject.getBasePath())) {
        return projectInfo.getExternalProjectStructure();
      }
    }
    return null;
  }

  @Nullable
  public Collection<DataNode<ModuleData>> showModuleSelectionDialog(@NotNull Collection<DataNode<ModuleData>> modules) {
    Set<String> noSelection = Collections.emptySet();
    return showModuleSelectionDialog(modules, noSelection);
  }

  @Nullable
  private Collection<DataNode<ModuleData>> showModuleSelectionDialog(@NotNull Collection<DataNode<ModuleData>> modules,
                                                                     @NotNull Collection<String> selectedModuleNames) {
    ModulesToImportDialog dialog = new ModulesToImportDialog(modules, myProject);
    if (!selectedModuleNames.isEmpty()) {
      dialog.updateSelection(selectedModuleNames);
    }
    if (dialog.showAndGet()) {
      Collection<DataNode<ModuleData>> selectedModules = dialog.getSelectedModules();

      // Store the name of the selected modules, so future 'project sync' invocations won't add unselected modules.
      setSelection(selectedModules);
      return selectedModules;
    }
    return null;
  }

  private void setSelection(@NotNull Collection<DataNode<ModuleData>> modules) {
    List<String> moduleNames = Lists.newArrayListWithExpectedSize(modules.size());
    for (DataNode<ModuleData> module : modules) {
      moduleNames.add(module.getData().getExternalName());
    }

    // Persist the selected modules between sessions.
    updateSelection(moduleNames);
  }

  public void clearSelection() {
    updateSelection(null);
  }

  private void updateSelection(@Nullable List<String> moduleNames) {
    String[] values = moduleNames != null ? toStringArray(moduleNames) : null;
    PropertiesComponent.getInstance(myProject).setValues(PROJECT_SUBSET_PROPERTY_NAME, values);
  }

  @Nullable
  public String[] getSelection() {
    return PropertiesComponent.getInstance(myProject).getValues(PROJECT_SUBSET_PROPERTY_NAME);
  }
}
