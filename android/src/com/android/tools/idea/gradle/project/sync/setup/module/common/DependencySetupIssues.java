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
package com.android.tools.idea.gradle.project.sync.setup.module.common;

import static com.android.tools.idea.gradle.project.sync.messages.GroupNames.MISSING_DEPENDENCIES;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile;
import static com.android.tools.idea.project.messages.MessageType.ERROR;
import static com.android.tools.idea.project.messages.MessageType.WARNING;
import static com.intellij.util.ArrayUtil.toStringArray;

import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.android.tools.idea.project.messages.MessageType;
import com.android.tools.idea.project.messages.SyncMessage;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.serviceContainer.NonInjectable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import org.jetbrains.annotations.NotNull;

/**
 * Collects and reports dependencies that were not correctly set up during a Gradle sync.
 */
public final class DependencySetupIssues {
  @NotNull private final GradleSyncMessages mySyncMessages;

  @NotNull private final Map<String, MissingModule> myMissingModules = new ConcurrentHashMap<>();

  @NotNull private final Set<String> myDependentsOnLibrariesWithoutBinaryPath = new CopyOnWriteArraySet<>();
  @NotNull private final Set<InvalidModuleDependency> myInvalidModuleDependencies = new CopyOnWriteArraySet<>();

  @NotNull
  public static DependencySetupIssues getInstance(@NotNull Project project) {
    return project.getService(DependencySetupIssues.class);
  }

  public DependencySetupIssues(@NotNull Project project) {
    mySyncMessages = GradleSyncMessages.getInstance(project);
  }

  @NonInjectable
  public DependencySetupIssues(@NotNull GradleSyncMessages syncMessages) {
    mySyncMessages = syncMessages;
  }

  public void reportIssues() {
    reportModulesNotFoundIssues(getMissingModules());

    for (String dependent : getDependentsOnLibrariesWithoutBinaryPath()) {
      String msg = String.format("Module '%1$s' depends on libraries that do not have a 'binary' path.", dependent);
      mySyncMessages.report(new SyncMessage(MISSING_DEPENDENCIES, ERROR, msg));
    }

    for (DependencySetupIssues.InvalidModuleDependency dependency : myInvalidModuleDependencies) {
      String msg = String.format("Ignoring dependency of module '%1$s' on module '%2$s'. %3$s",
                                 dependency.dependent, dependency.dependency.getName(), dependency.cause);
      VirtualFile buildFile = getGradleBuildFile(dependency.dependency);
      assert buildFile != null;
      OpenFileDescriptor navigatable = new OpenFileDescriptor(dependency.dependency.getProject(), buildFile, 0);
      mySyncMessages.report(new SyncMessage(MISSING_DEPENDENCIES, WARNING, navigatable, msg));
    }

    clear();
  }

  @VisibleForTesting
  @NotNull
  List<MissingModule> getMissingModules() {
    return getMissingModules(myMissingModules);
  }

  @NotNull
  private static List<MissingModule> getMissingModules(@NotNull Map<String, MissingModule> missingModulesByName) {
    if (missingModulesByName.isEmpty()) {
      return Collections.emptyList();
    }
    List<MissingModule> missingModules = new ArrayList<>();
    List<String> names = new ArrayList<>(missingModulesByName.keySet());
    if (names.size() > 1) {
      names.sort(String::compareTo);
    }
    for (String name : names) {
      MissingModule missingModule = missingModulesByName.get(name);
      missingModule.sortDependentNames();
      missingModules.add(missingModule);
    }
    return missingModules;
  }

  @VisibleForTesting
  @NotNull
  List<String> getDependentsOnLibrariesWithoutBinaryPath() {
    return sortSet(myDependentsOnLibrariesWithoutBinaryPath);
  }

  @NotNull
  private static List<String> sortSet(@NotNull Set<String> set) {
    if (set.isEmpty()) {
      return Collections.emptyList();
    }
    List<String> sorted = new ArrayList<>(set);
    if (sorted.size() > 1) {
      sorted.sort(String::compareTo);
    }
    return sorted;
  }

  private void reportModulesNotFoundIssues(@NotNull List<MissingModule> missingModules) {
    if (!missingModules.isEmpty()) {
      for (MissingModule missingModule : missingModules) {
        List<String> messageLines = new ArrayList<>();

        StringBuilder text = new StringBuilder();
        text.append(String.format("Unable to find module with Gradle path '%1$s' (needed by module", missingModule.dependencyPath));

        addDependentsToText(text, missingModule.dependentNames);
        text.append(".)");
        messageLines.add(text.toString());

        mySyncMessages.report(new SyncMessage(MISSING_DEPENDENCIES, missingModule.issueType, toStringArray(messageLines)));
      }
    }
  }

  private static void addDependentsToText(@NotNull StringBuilder text, @NotNull List<String> dependents) {
    assert !dependents.isEmpty();

    if (dependents.size() == 1) {
      text.append(String.format(" '%1$s'", dependents.get(0)));
      return;
    }

    text.append("s: ");
    int i = 0;
    for (String dependent : dependents) {
      if (i++ > 0) {
        text.append(", ");
      }
      text.append(String.format("'%1$s'", dependent));
    }
  }

  private void clear() {
    myMissingModules.clear();
    myDependentsOnLibrariesWithoutBinaryPath.clear();
    myInvalidModuleDependencies.clear();
  }

  public void addMissingModule(@NotNull String dependencyName, @NotNull String dependentName) {
    Map<String, MissingModule> mapping = myMissingModules;
    MissingModule missingModule = mapping.computeIfAbsent(dependencyName, name -> new MissingModule(name));
    missingModule.addDependent(dependentName);
  }

  /**
   * Adds the name of a module that depends on a library, but the library is missing the path of the binary file.
   *
   * @param dependentName the name of the module.
   */
  public void addMissingBinaryPath(@NotNull String dependentName) {
    myDependentsOnLibrariesWithoutBinaryPath.add(dependentName);
  }

  public void addInvalidModuleDependency(@NotNull Module dependency,
                                         @NotNull String dependent,
                                         @SuppressWarnings("SameParameterValue") @NotNull String cause) {
    myInvalidModuleDependencies.add(new InvalidModuleDependency(dependency, dependent, cause));
  }

  @VisibleForTesting
  static class MissingModule {
    @NotNull final String dependencyPath;
    @NotNull final MessageType issueType = ERROR;

    @NotNull final List<String> dependentNames = new CopyOnWriteArrayList<>();

    MissingModule(@NotNull String dependencyPath) {
      this.dependencyPath = dependencyPath;
    }

    void addDependent(@NotNull String dependentName) {
      dependentNames.add(dependentName);
    }

    void sortDependentNames() {
      if (!dependentNames.isEmpty()) {
        dependentNames.sort(String::compareTo);
      }
    }
  }

  @VisibleForTesting
  static class InvalidModuleDependency {
    @NotNull final Module dependency;
    @NotNull final String dependent;
    @NotNull final String cause;

    InvalidModuleDependency(@NotNull Module dependency, @NotNull String dependent, @NotNull String cause) {
      this.dependency = dependency;
      this.dependent = dependent;
      this.cause = cause;
    }
  }
}
