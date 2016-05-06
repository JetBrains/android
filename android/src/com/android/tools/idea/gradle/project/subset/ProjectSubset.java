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

import com.android.builder.model.Variant;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.AndroidGradleModel.SourceFileContainerInfo;
import com.android.tools.idea.gradle.GradleModel;
import com.android.tools.idea.gradle.JavaProject;
import com.android.tools.idea.gradle.project.AndroidGradleNotification;
import com.android.tools.idea.gradle.project.GradleExperimentalSettings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

import static com.android.tools.idea.gradle.AndroidProjectKeys.*;
import static com.android.tools.idea.gradle.util.GradleUtil.getCachedProjectData;
import static com.android.tools.idea.gradle.util.Projects.populate;
import static com.intellij.notification.NotificationType.ERROR;
import static com.intellij.notification.NotificationType.INFORMATION;
import static com.intellij.openapi.externalSystem.model.ProjectKeys.MODULE;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.find;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.findAll;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static com.intellij.util.ArrayUtil.toStringArray;
import static com.intellij.util.ui.UIUtil.invokeLaterIfNeeded;

/**
 * A project subset is a feature where users can select the modules in a project to include when an existing project is imported into the
 * IDE. This feature is handy when users work with big projects (e.g. 300+ modules) but, in practice, modify sources in a few of them. A
 * smaller set of source code can make IDE's performance better (e.g. indexing and building.)
 */
public final class ProjectSubset {
  @NonNls private static final String PROJECT_SUBSET_PROPERTY_NAME = "com.android.studio.selected.modules.on.import";

  private static final String MODULE_LOOKUP_MESSAGE_TITLE = "Module Lookup";

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
    Collection<DataNode<ModuleData>> modules = getCachedModuleData();
    return modules != null && !modules.isEmpty();
  }

  public void addOrRemoveModules() {
    Collection<DataNode<ModuleData>> modules = getCachedModuleData();
    if (modules != null) {
      Collection<String> selectedModuleNames = Collections.emptySet();
      String[] selection = getSelection();
      if (selection != null) {
        selectedModuleNames = Sets.newHashSet(selection);
      }
      Collection<DataNode<ModuleData>> selectedModules = showModuleSelectionDialog(modules, selectedModuleNames);
      if (selectedModules != null) {
        setSelection(selectedModules);
        if (!Arrays.equals(getSelection(), selection)) {
          populate(myProject, selectedModules, true);
        }
      }
    }
  }

  /**
   * Finds and includes the module that contains the given file.
   * <p>
   * When using the "Project Subset" feature it is possible that the user knows which file she wants to edit but not the module where
   * such file is. This method tries to find the module that includes the given file in the folders that it marked as "source", either
   * production or test code.
   * </p>
   * <p>
   * The search is based on the Gradle models for both Android and Java modules. If the search finds more than one module that might contain
   * the file, the IDE will display a dialog where the user can see the potential matches and choose the module to include in the project.
   * </p>
   * @param virtualFile the given file.
   */
  public void findAndIncludeModuleContainingSourceFile(@NotNull VirtualFile virtualFile) {
    final Collection<DataNode<ModuleData>> modules = getCachedModuleData();

    if (modules != null && !modules.isEmpty()) {
      final Project project = myProject;
      final File file = virtualToIoFile(virtualFile);

      new Task.Modal(project, "Looking up Module", false) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          List<ModuleSearchResult> results = Lists.newArrayList();

          String[] storedSelection = getSelection();
          Set<String> selection = storedSelection != null ? Sets.newHashSet(storedSelection) : Sets.<String>newHashSet();

          List<DataNode<ModuleData>> selectedModules = Lists.newArrayList();

          int doneCount = 0;
          for (DataNode<ModuleData> moduleNode : modules) {
            indicator.setFraction(++doneCount / modules.size());
            ModuleData module = moduleNode.getData();

            String name = module.getExternalName();
            boolean selected = selection.contains(name);
            if (selected) {
              // This module is already included in the project. We need to mark it as "selected" so when we are done searching we don't
              // exclude it by accident.
              selectedModules.add(moduleNode);
            }
            ModuleSearchResult result = containsSourceFile(moduleNode, file, selected);
            if (result != null) {
              // Even though the module is already included, we add it to the search results, because the module might not be the one that
              // actually contains the file, and the user might need to exclude it in the case that the module that contains the file has
              // the same path as the already-included module.
              results.add(result);
            }
          }

          int resultCount = results.size();
          if (resultCount == 0) {
            // Nothing found.
            invokeLaterIfNeeded(new Runnable() {
              @Override
              public void run() {
                String text = String.format("Unable to find a module containing the file '%1$s' in a source directory.", file.getName());
                AndroidGradleNotification notification = AndroidGradleNotification.getInstance(project);
                notification.showBalloon(MODULE_LOOKUP_MESSAGE_TITLE, text, ERROR);
              }
            });
          }
          else if (resultCount == 1) {
            // If there is one result,just apply it.
            addResultAndPopulateProject(results.get(0), selectedModules, file);
          }
          else {
            // We need to let user decide which modules to include.
            showModuleSelectionDialog(results, selectedModules, file);
          }
        }
      }.queue();
    }
  }

  /**
   * Checks in the Android and Java models to see if the module contains the given file.
   * @param moduleNode represents the module that is not included yet in the IDE.
   * @param file the given file.
   * @param selected indicates whether the module is included in the project.
   * @return the result of the search, or {@code null} if this module does not contain the given file.
   */
  @Nullable
  private static ModuleSearchResult containsSourceFile(@NotNull DataNode<ModuleData> moduleNode, @NotNull File file, boolean selected) {
    DataNode<AndroidGradleModel> androidProjectNode = find(moduleNode, ANDROID_MODEL);
    if (androidProjectNode != null) {
      AndroidGradleModel androidModel = androidProjectNode.getData();
      SourceFileContainerInfo result = androidModel.containsSourceFile(file);
      if (result != null) {
        return new ModuleSearchResult(moduleNode, result, selected);
      }
    }

    DataNode<JavaProject> javaProjectNode = find(moduleNode, JAVA_PROJECT);
    if (javaProjectNode != null) {
      JavaProject javaProject = javaProjectNode.getData();
      if (javaProject.containsSourceFile(file)) {
        return new ModuleSearchResult(moduleNode, null, selected);
      }
    }
    return null;
  }

  /**
   * Adds the module in the given search results to the IDE. If the search result indicates the variant where the file is, this method
   * will select such variant in the Android model.
   * @param result the search result.
   * @param selectedModules all the modules to be included in the project.
   * @param file the file to include in the project.
   */
  private void addResultAndPopulateProject(@NotNull ModuleSearchResult result,
                                           @NotNull List<DataNode<ModuleData>> selectedModules,
                                           @NotNull File file) {
    DataNode<ModuleData> moduleNode = result.moduleNode;
    String moduleName = getNameOf(moduleNode);
    final String text;
    if (result.selected) {
      String tmp = String.format("File '%1$s' is already in module '%2$s'", file.getName(), moduleName);
      SourceFileContainerInfo containerInfo = result.containerInfo;
      if (containerInfo != null) {
        containerInfo.updateSelectedVariantIn(moduleNode);
        Variant variant = containerInfo.variant;
        if (variant != null) {
          tmp += String.format(", variant '%1$s'", variant.getName());
        }
      }
      text = tmp;
    }
    else {
      text = String.format("Module '%1$s' was added to the project.", moduleName);
      SourceFileContainerInfo containerInfo = result.containerInfo;
      if (containerInfo != null) {
        containerInfo.updateSelectedVariantIn(moduleNode);
      }
      selectedModules.add(moduleNode);
      setSelection(selectedModules);
    }

    invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        AndroidGradleNotification notification = AndroidGradleNotification.getInstance(myProject);
        notification.showBalloon(MODULE_LOOKUP_MESSAGE_TITLE, text, INFORMATION);
      }
    });

    populate(myProject, selectedModules, true);
  }

  /**
   * Displays the "Select Modules" dialog. This method is invoked when the search for a module containing a file returns more than one
   * result. The user now needs to select the module(s) to include.
   * @param searchResults includes the modules that might contain the given file.
   * @param selection all the modules that need to be included in the project.
   * @param file the file to include in the project.
   */
  private void showModuleSelectionDialog(@NotNull List<ModuleSearchResult> searchResults,
                                         final @NotNull List<DataNode<ModuleData>> selection,
                                         final @NotNull File file) {
    final List<DataNode<ModuleData>> finalSelection = Lists.newArrayList(selection);
    final List<DataNode<ModuleData>> modulesToDisplayInDialog = Lists.newArrayList();
    final Map<String, ModuleSearchResult> resultsByModuleName = Maps.newHashMap();

    for (ModuleSearchResult result : searchResults) {
      DataNode<ModuleData> module = result.moduleNode;
      modulesToDisplayInDialog.add(module);
      if (result.selected) {
        finalSelection.remove(module);
      }
      String moduleName = getNameOf(module);
      resultsByModuleName.put(moduleName, result);
    }
    invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        ModulesToImportDialog dialog = new ModulesToImportDialog(modulesToDisplayInDialog, myProject);

        String description = String.format("The file '%1$s' may be include in one of the following modules.", file.getName());
        dialog.setDescription(description);

        dialog.clearSelection();

        if (dialog.showAndGet()) {
          Collection<DataNode<ModuleData>> selectedModules = dialog.getSelectedModules();
          if (!selectedModules.isEmpty()) {
            for (DataNode<ModuleData> selectedModule : selectedModules) {
              String name = getNameOf(selectedModule);
              ModuleSearchResult result = resultsByModuleName.get(name);
              if (result != null) {
                SourceFileContainerInfo containerInfo = result.containerInfo;
                if (containerInfo != null) {
                  containerInfo.updateSelectedVariantIn(selectedModule);
                }
              }
            }

            finalSelection.addAll(selectedModules);
            setSelection(finalSelection);
            populate(myProject, finalSelection, true);
          }
        }
      }
    });
  }

  public void findAndIncludeModules(@NotNull final Collection<String> moduleGradlePaths) {
    final Collection<DataNode<ModuleData>> modules = getCachedModuleData();

    if (modules != null && !modules.isEmpty()) {
      final Project project = myProject;

      new Task.Modal(project, "Finding Missing Modules", false) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          String[] originalSelection = getSelection();
          Set<String> selection = originalSelection != null ? Sets.newHashSet(originalSelection) : Sets.<String>newHashSet();

          List<DataNode<ModuleData>> selectedModules = Lists.newArrayList();
          boolean found = false;

          int doneCount = 0;
          for (DataNode<ModuleData> module : modules) {
            indicator.setFraction(++doneCount / modules.size());

            String name = getNameOf(module);
            if (selection.contains(name)) {
              selectedModules.add(module);
              continue;
            }
            DataNode<GradleModel> gradleProjectNode = find(module, GRADLE_MODEL);
            if (gradleProjectNode != null) {
              GradleModel gradleModel = gradleProjectNode.getData();
              if (moduleGradlePaths.contains(gradleModel.getGradlePath())) {
                selection.add(name);
                selectedModules.add(module);
                found = true;
              }
            }
          }
          if (!selectedModules.isEmpty() && found) {
            setSelection(selectedModules);
            populate(project, selectedModules, true);
          }
        }
      }.queue();
    }
  }

  @Nullable
  public Collection<DataNode<ModuleData>> getCachedModuleData() {
    DataNode<ProjectData> projectData = getCachedProjectData(myProject);
    if (projectData != null) {
      return findAll(projectData, MODULE);
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
      moduleNames.add(getNameOf(module));
    }

    // Persist the selected modules between sessions.
    updateSelection(moduleNames);
  }

  @NotNull
  private static String getNameOf(@NotNull DataNode<ModuleData> module) {
    return module.getData().getExternalName();
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

  private static class ModuleSearchResult {
    @NotNull public final DataNode<ModuleData> moduleNode;
    @Nullable public final SourceFileContainerInfo containerInfo;
    public final boolean selected;

    ModuleSearchResult(@NotNull DataNode<ModuleData> moduleNode, @Nullable SourceFileContainerInfo containerInfo, boolean selected) {
      this.moduleNode = moduleNode;
      this.containerInfo = containerInfo;
      this.selected = selected;
    }
  }
}
