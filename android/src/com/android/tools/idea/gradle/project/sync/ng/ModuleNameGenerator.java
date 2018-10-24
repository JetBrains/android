/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.ng;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.intellij.openapi.util.io.FileUtil.splitPath;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;

import com.android.tools.idea.gradle.util.GradleUtil;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.tooling.model.GradleProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;

/**
 * Generate unique module names that are used in IDE.
 * By default, Gradle Sync has module name set to GradleProject.getName().
 * IDEA requires uniqueness of module names, if there are duplicated names, prefix module name with part of gradle path.
 * This is consistent with idea gradle plugin, see
 * https://docs.gradle.org/current/dsl/org.gradle.plugins.ide.idea.model.IdeaModule.html#org.gradle.plugins.ide.idea.model.IdeaModule:name
 */
public class ModuleNameGenerator {
  // Keep consistent with IdeModelsProviderImpl.ModuleNameGenerator.MAX_FILE_DEPTH.
  private static final int MAX_FILE_DEPTH = 3;

  /**
   * Deduplicate the module names in SyncProjectModels returned by BuildAction.
   */
  static void deduplicateModuleNames(@NotNull SyncProjectModels projectModels, @NotNull Project project) {
    Map<SyncModuleModels, ModuleName> moduleModelsToModuleName = new HashMap<>();
    List<SyncModuleModels> moduleModels = projectModels.getModuleModels();
    for (SyncModuleModels moduleModel : moduleModels) {
      GradleProject gradleProject = moduleModel.findModel(GradleProject.class);
      if (gradleProject != null) {
        moduleModelsToModuleName.put(moduleModel,
                                     new ModuleName(gradleProject.getProjectDirectory().getPath(), moduleModel.getModuleName()));
      }
    }
    doDeduplicate(moduleModelsToModuleName.values(), project);
    for (SyncModuleModels moduleModel : moduleModels) {
      ModuleName moduleName = moduleModelsToModuleName.get(moduleModel);
      if (moduleName != null) {
        moduleModel.setModuleName(moduleName.name);
      }
    }
  }

  /**
   * @return a map from module path to unique module name.
   */
  @NotNull
  public static Map<String, String> getModuleNameByModulePath(@NotNull Collection<String> modulePaths, @NotNull Project project) {
    Map<String, ModuleName> moduleNameByModulePath =
      modulePaths.stream().collect(toMap(p -> p, p -> new ModuleName(p, new File(p).getName())));
    doDeduplicate(moduleNameByModulePath.values(), project);
    return moduleNameByModulePath.entrySet()
      .stream()
      .collect(toMap(e -> e.getKey(),
                     e -> e.getValue().name));
  }

  private static void doDeduplicate(@NotNull Collection<ModuleName> moduleNames, @NotNull Project project) {
    Map<String, List<ModuleName>> moduleNamesByName = moduleNames.stream().collect(groupingBy(m -> m.name));
    GradleProjectSettings settings = GradleUtil.getGradleProjectSettings(project);
    char delimiter = settings != null && settings.isUseQualifiedModuleNames() ? '.' : '-';

    for (List<ModuleName> names : moduleNamesByName.values()) {
      if (names.size() > 1) {
        // More than one module has the same name.
        doDeduplicate(names, delimiter);
      }
    }
  }

  private static void doDeduplicate(@NotNull List<ModuleName> moduleNames, char delimiter) {
    Map<ModuleName, List<String>> moduleNameToNameCandidates = new HashMap<>();
    for (ModuleName moduleName : moduleNames) {
      List<String> pathParts = splitPath(toSystemDependentName(moduleName.moduleDirectory));
      List<String> nameCandidates = new ArrayList<>();
      moduleNameToNameCandidates.put(moduleName, nameCandidates);
      StringBuilder nameBuilder = new StringBuilder(moduleName.name);
      for (int i = pathParts.size() - 2, j = 0; i >= 0 && j < MAX_FILE_DEPTH; i--, j++) {
        String prefix = pathParts.get(i);
        if (!isNullOrEmpty(prefix)) {
          nameBuilder.insert(0, prefix + delimiter);
          nameCandidates.add(nameBuilder.toString());
        }
      }
    }

    Set<String> uniqueNames;
    int nameIndex = 0;
    do {
      for (ModuleName moduleName : moduleNames) {
        List<String> nameCandidates = moduleNameToNameCandidates.get(moduleName);
        if (nameCandidates != null && nameIndex < nameCandidates.size()) {
          moduleName.name = nameCandidates.get(nameIndex);
        }
      }
      uniqueNames = moduleNames.stream().map(m -> m.name).collect(Collectors.toSet());
      nameIndex++;
    }
    while (uniqueNames.size() < moduleNames.size() && nameIndex < MAX_FILE_DEPTH);
  }

  private static class ModuleName {
    @NotNull private String moduleDirectory;
    @NotNull private String name;

    ModuleName(@NotNull String moduleDirectory, @NotNull String baseName) {
      this.moduleDirectory = moduleDirectory;
      this.name = baseName;
    }
  }
}
