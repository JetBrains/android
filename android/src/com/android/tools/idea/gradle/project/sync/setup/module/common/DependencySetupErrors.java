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

import com.android.tools.idea.gradle.project.subset.ProjectSubset;
import com.android.tools.idea.gradle.project.sync.messages.MessageType;
import com.android.tools.idea.gradle.project.sync.messages.SyncMessage;
import com.android.tools.idea.gradle.project.sync.messages.SyncMessages;
import com.android.tools.idea.gradle.project.sync.hyperlink.NotificationHyperlink;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.android.tools.idea.gradle.project.sync.messages.GroupNames.MISSING_DEPENDENCIES;
import static com.android.tools.idea.gradle.project.sync.messages.MessageType.ERROR;
import static com.android.tools.idea.gradle.project.sync.messages.MessageType.WARNING;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.util.ArrayUtil.toStringArray;
import static java.util.Collections.sort;

/**
 * Collects and reports dependencies that were not correctly set up during a Gradle sync.
 */
public class DependencySetupErrors {
  @NotNull private final Project myProject;
  @NotNull private final SyncMessages mySyncMessages;

  @NotNull private final Map<String, MissingModule> myMissingModules = new HashMap<>();
  @NotNull private final Map<String, MissingModule> myMissingModulesWithBackupLibraries = new HashMap<>();

  @NotNull private final Set<String> myDependentsOnModulesWithoutName = new HashSet<>();
  @NotNull private final Set<String> myDependentsOnLibrariesWithoutBinaryPath = new HashSet<>();

  @NotNull private final Set<InvalidModuleDependency> myInvalidModuleDependencies = new HashSet<>();

  @NotNull
  public static DependencySetupErrors getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, DependencySetupErrors.class);
  }

  public DependencySetupErrors(@NotNull Project project, @NotNull SyncMessages syncMessages) {
    myProject = project;
    mySyncMessages = syncMessages;
  }

  public void reportErrors() {
    reportModulesNotFoundIssues(getMissingModules());

    for (String dependent : getMissingNames()) {
      String msg = String.format("Module '%1$s' depends on modules that do not have a name.", dependent);
      mySyncMessages.report(new SyncMessage(MISSING_DEPENDENCIES, ERROR, msg));
    }

    for (String dependent : getDependentsOnLibrariesWithoutBinaryPath()) {
      String msg = String.format("Module '%1$s' depends on libraries that do not have a 'binary' path.", dependent);
      mySyncMessages.report(new SyncMessage(MISSING_DEPENDENCIES, ERROR, msg));
    }

    for (DependencySetupErrors.InvalidModuleDependency dependency : myInvalidModuleDependencies) {
      String msg = String.format("Ignoring dependency of module '%1$s' on module '%2$s'. %3$s",
                                 dependency.dependent, dependency.dependency.getName(), dependency.causeDescription);
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
      sort(names);
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
  List<String> getMissingNames() {
    return sortSet(myDependentsOnModulesWithoutName);
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
      sort(sorted);
    }
    return sorted;
  }

  private void reportModulesNotFoundIssues(@NotNull List<MissingModule> missingModules) {
    if (!missingModules.isEmpty()) {
      MessageType type = ERROR;

      for (MissingModule missingModule : missingModules) {
        List<String> messageLines = new ArrayList<>();

        StringBuilder text = new StringBuilder();
        text.append(String.format("Unable to find module with Gradle path '%1$s' (needed by module", missingModule.dependencyPath));

        addDependentsToText(text, missingModule.dependentNames);
        text.append(".)");
        messageLines.add(text.toString());

        String backupLibraryName = missingModule.backupLibraryName;
        if (isNotEmpty(backupLibraryName)) {
          type = WARNING;
          String msg = String.format("Linking to library '%1$s' instead.", backupLibraryName);
          messageLines.add(msg);
        }
        mySyncMessages.report(new SyncMessage(MISSING_DEPENDENCIES, type, toStringArray(messageLines)));
      }

      // If the project is really a subset of the project, attempt to find and include missing modules.
      ProjectSubset projectSubset = ProjectSubset.getInstance(myProject);
      String[] selection = projectSubset.getSelection();
      boolean hasSelection = selection != null && selection.length > 0;
      if (type == ERROR && hasSelection && projectSubset.hasCachedModules()) {
        String text = "The missing modules may have been excluded from the project subset.";
        SyncMessage message = new SyncMessage(MISSING_DEPENDENCIES, MessageType.INFO, text);
        message.add(new IncludeMissingModulesHyperlink(missingModules));
        mySyncMessages.report(message);
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

  public void addInvalidModuleDependency(@NotNull Module module, @NotNull String targetModuleName, @NotNull String causeDescription) {
    myInvalidModuleDependencies.add(new InvalidModuleDependency(module, targetModuleName, causeDescription));
  }

  @VisibleForTesting
  static class MissingModule {
    @NotNull final String dependencyPath;
    @NotNull final List<String> dependentNames;

    @Nullable final String backupLibraryName;

    MissingModule(@NotNull String dependencyPath, @Nullable String backupLibraryName) {
      this.dependencyPath = dependencyPath;
      dependentNames = new ArrayList<>();
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

  @VisibleForTesting
  static class InvalidModuleDependency {
    @NotNull final Module dependency;
    @NotNull final String dependent;
    @NotNull final String causeDescription;

    InvalidModuleDependency(@NotNull Module dependency, @NotNull String dependent, @NotNull String causeDescription) {
      this.dependency = dependency;
      this.dependent = dependent;
      this.causeDescription = causeDescription;
    }
  }

  /**
   * "Quick Fix" link that attempts to find and include any modules that were not previously included in the project subset.
   */
  private static class IncludeMissingModulesHyperlink extends NotificationHyperlink {
    @NotNull private final Set<String> myModuleGradlePaths;

    IncludeMissingModulesHyperlink(@NotNull List<MissingModule> missingModules) {
      super("include.missing.modules", "Find and include missing modules");
      myModuleGradlePaths = new HashSet<>(missingModules.size());
      for (MissingModule module : missingModules) {
        myModuleGradlePaths.add(module.dependencyPath);
      }
    }

    @Override
    protected void execute(@NotNull Project project) {
      ProjectSubset.getInstance(project).findAndIncludeModules(myModuleGradlePaths);
    }
  }
}
