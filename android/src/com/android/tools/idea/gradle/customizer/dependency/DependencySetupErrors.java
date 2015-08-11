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
package com.android.tools.idea.gradle.customizer.dependency;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static java.util.Collections.sort;

/**
 * Dependencies that were not correctly set up after a Gradle sync.
 */
public class DependencySetupErrors {
  @NotNull private final Map<String, MissingModule> myMissingModules = Maps.newHashMap();
  @NotNull private final Map<String, MissingModule> myMissingModulesWithBackupLibraries = Maps.newHashMap();

  @NotNull private final Set<String> myDependentsOnModulesWithoutName = Sets.newHashSet();
  @NotNull private final Set<String> myDependentsOnLibrariesWithoutBinaryPath = Sets.newHashSet();

  @NotNull private final Set<InvalidModuleDependency> myInvalidModuleDependencies = Sets.newHashSet();

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
