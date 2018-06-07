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

import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.android.tools.idea.project.messages.MessageType;
import com.android.tools.idea.project.messages.SyncMessage;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

import static com.android.tools.idea.gradle.project.sync.messages.GroupNames.MISSING_DEPENDENCIES;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile;
import static com.android.tools.idea.project.messages.MessageType.ERROR;
import static com.android.tools.idea.project.messages.MessageType.WARNING;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.util.ArrayUtil.toStringArray;

/**
 * Collects and reports dependencies that were not correctly set up during a Gradle sync.
 */
public class DependencySetupIssues {
  @NotNull private final GradleSyncState mySyncState;
  @NotNull private final GradleSyncMessages mySyncMessages;

  @NotNull private final Map<String, MissingModule> myMissingModules = new ConcurrentHashMap<>();
  @NotNull private final Map<String, MissingModule> myMissingModulesWithBackupLibraries = new ConcurrentHashMap<>();

  @NotNull private final Set<String> myDependentsOnLibrariesWithoutBinaryPath = new CopyOnWriteArraySet<>();
  @NotNull private final Set<InvalidModuleDependency> myInvalidModuleDependencies = new CopyOnWriteArraySet<>();

  @NotNull
  public static DependencySetupIssues getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, DependencySetupIssues.class);
  }

  public DependencySetupIssues(@NotNull Project project, @NotNull GradleSyncState syncState, @NotNull GradleSyncMessages syncMessages) {
    mySyncState = syncState;
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

    reportModulesNotFoundIssues(getMissingModulesWithBackupLibraries());
    clear();
  }

  @VisibleForTesting
  @NotNull
  List<MissingModule> getMissingModules() {
    return getMissingModules(myMissingModules);
  }

  @VisibleForTesting
  @NotNull
  List<MissingModule> getMissingModulesWithBackupLibraries() {
    return getMissingModules(myMissingModulesWithBackupLibraries);
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

        String backupLibraryName = missingModule.backupLibraryName;
        if (isNotEmpty(backupLibraryName)) {
          String msg = String.format("Linking to library '%1$s' instead.", backupLibraryName);
          messageLines.add(msg);
        }
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
    myMissingModulesWithBackupLibraries.clear();
    myDependentsOnLibrariesWithoutBinaryPath.clear();
    myInvalidModuleDependencies.clear();
  }

  public void addMissingModule(@NotNull String dependencyName, @NotNull String dependentName, @Nullable String backupLibraryName) {
    Map<String, MissingModule> mapping = isNotEmpty(backupLibraryName) ? myMissingModulesWithBackupLibraries : myMissingModules;
    MissingModule missingModule = mapping.computeIfAbsent(dependencyName, name -> new MissingModule(name, backupLibraryName));
    missingModule.addDependent(dependentName);
    if (missingModule.isError()) {
      registerSyncError();
    }
  }

  /**
   * Adds the name of a module that depends on a library, but the library is missing the path of the binary file.
   *
   * @param dependentName the name of the module.
   */
  public void addMissingBinaryPath(@NotNull String dependentName) {
    myDependentsOnLibrariesWithoutBinaryPath.add(dependentName);
    registerSyncError();
  }

  private void registerSyncError() {
    mySyncState.getSummary().setSyncErrorsFound(true);
  }

  public void addInvalidModuleDependency(@NotNull Module dependency,
                                         @NotNull String dependent,
                                         @SuppressWarnings("SameParameterValue") @NotNull String cause) {
    myInvalidModuleDependencies.add(new InvalidModuleDependency(dependency, dependent, cause));
  }

  @VisibleForTesting
  static class MissingModule {
    @NotNull final String dependencyPath;
    @NotNull final MessageType issueType;
    @Nullable final String backupLibraryName;

    @NotNull final List<String> dependentNames = new CopyOnWriteArrayList<>();

    MissingModule(@NotNull String dependencyPath, @Nullable String backupLibraryName) {
      this.dependencyPath = dependencyPath;
      this.backupLibraryName = backupLibraryName;
      issueType = isEmpty(backupLibraryName) ? ERROR : WARNING;
    }

    void addDependent(@NotNull String dependentName) {
      dependentNames.add(dependentName);
    }

    void sortDependentNames() {
      if (!dependentNames.isEmpty()) {
        dependentNames.sort(String::compareTo);
      }
    }

    boolean isError() {
      return issueType == ERROR;
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
