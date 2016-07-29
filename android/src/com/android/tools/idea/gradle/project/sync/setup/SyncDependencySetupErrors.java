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
package com.android.tools.idea.gradle.project.sync.setup;

import com.google.common.collect.Lists;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static java.util.Collections.sort;

/**
 * Dependencies that were not correctly set up after a Gradle sync.
 */
public class SyncDependencySetupErrors {
  @NotNull private final Map<String, MissingModule> myMissingModules = new HashMap<>();
  @NotNull private final Map<String, MissingModule> myMissingModulesWithBackupLibraries = new HashMap<>();

  @NotNull private final Set<String> myDependentsOnModulesWithoutName = new HashSet<>();
  @NotNull private final Set<String> myDependentsOnLibrariesWithoutBinaryPath = new HashSet<>();

  @NotNull private final Set<InvalidModuleDependency> myInvalidModuleDependencies = new HashSet<>();

  @NotNull
  public static SyncDependencySetupErrors getInstance(@NotNull Module module) {
    return getInstance(module.getProject());
  }

  @NotNull
  public static SyncDependencySetupErrors getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, SyncDependencySetupErrors.class);
  }

  public void clear() {
    myMissingModules.clear();
    myMissingModulesWithBackupLibraries.clear();
    myDependentsOnModulesWithoutName.clear();
    myDependentsOnLibrariesWithoutBinaryPath.clear();
    myInvalidModuleDependencies.clear();
  }

  public void addMissingModule(@NotNull String dependencyName, @NotNull String dependentName, @Nullable String backupLibraryName) {
    Map<String, MissingModule> mapping = isNotEmpty(backupLibraryName) ? myMissingModulesWithBackupLibraries : myMissingModules;
    MissingModule missingModule = mapping.get(dependencyName);
    if (missingModule == null) {
      missingModule = new MissingModule(dependencyName, backupLibraryName);
      mapping.put(dependencyName, missingModule);
    }
    missingModule.addDependent(dependentName);
  }

  /**
   * Adds the name of a module that depends on another module that does not have a name.
   * @param dependentName the name of the module.
   */
  public void addMissingName(@NotNull String dependentName) {
    myDependentsOnModulesWithoutName.add(dependentName);
  }

  /**
   * Adds the name of a module that depends on a library, but the library is missing the path of the binary file.
   * @param dependentName the name of the module.
   */
  public void addMissingBinaryPath(@NotNull String dependentName) {
    myDependentsOnLibrariesWithoutBinaryPath.add(dependentName);
  }

  public void addInvalidModuleDependency(@NotNull Module module, @NotNull String targetModuleName, @NotNull String detail) {
    myInvalidModuleDependencies.add(new InvalidModuleDependency(module, targetModuleName, detail));
  }

  @NotNull
  public List<MissingModule> getMissingModules() {
    return getMissingModules(myMissingModules);
  }

  @NotNull
  public List<MissingModule> getMissingModulesWithBackupLibraries() {
    return getMissingModules(myMissingModulesWithBackupLibraries);
  }

  @NotNull
  private static List<MissingModule> getMissingModules(@NotNull Map<String, MissingModule> missingModulesByName) {
    if (missingModulesByName.isEmpty()) {
      return Collections.emptyList();
    }
    List<MissingModule> missingModules = Lists.newArrayList();
    List<String> names = Lists.newArrayList(missingModulesByName.keySet());
    if (names.size() > 1) {
      sort(names);
    }
    for (String name : names) {
      MissingModule missingModule = missingModulesByName.get(name);
      missingModule.sortDependentNames();
      missingModules.add(missingModule);
    }
    return missingModules;
  }

  @NotNull
  public List<String> getMissingNames() {
    return sortSet(myDependentsOnModulesWithoutName);
  }

  @NotNull
  public List<String> getDependentsOnLibrariesWithoutBinaryPath() {
    return sortSet(myDependentsOnLibrariesWithoutBinaryPath);
  }

  @NotNull
  public Set<InvalidModuleDependency> getInvalidModuleDependencies() {
    return myInvalidModuleDependencies;
  }

  @NotNull
  private static List<String> sortSet(@NotNull Set<String> set) {
    if (set.isEmpty()) {
      return Collections.emptyList();
    }
    List<String> sorted = Lists.newArrayList(set);
    if (sorted.size() > 1) {
      sort(sorted);
    }
    return sorted;
  }

  public static class MissingModule {
    @NotNull public final String dependencyPath;
    @NotNull public final List<String> dependentNames = Lists.newArrayList();

    @Nullable public final String backupLibraryName;

    MissingModule(@NotNull String dependencyPath, @Nullable String backupLibraryName) {
      this.dependencyPath = dependencyPath;
      this.backupLibraryName = backupLibraryName;
    }

    void addDependent(@NotNull String dependentName) {
      dependentNames.add(dependentName);
    }

    void sortDependentNames() {
      if (!dependentNames.isEmpty()) {
        sort(dependentNames);
      }
    }
  }

  public static class InvalidModuleDependency {
    @NotNull public final Module dependency;
    @NotNull public final String dependent;
    @NotNull public final String detail;

    InvalidModuleDependency(@NotNull Module dependency, @NotNull String dependent, @NotNull String detail) {
      this.dependency = dependency;
      this.dependent = dependent;
      this.detail = detail;
    }
  }
}
