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
package com.android.tools.idea.gradle.project.sync.messages.reporter;

import com.android.builder.model.SyncIssue;
import com.android.tools.idea.gradle.project.compatibility.VersionCompatibilityService;
import com.android.tools.idea.gradle.project.subset.ProjectSubset;
import com.android.tools.idea.gradle.project.sync.messages.MessageType;
import com.android.tools.idea.gradle.project.sync.messages.SyncMessage;
import com.android.tools.idea.gradle.project.sync.messages.issues.SyncIssuesMessageReporter;
import com.android.tools.idea.gradle.project.sync.messages.issues.UnresolvedDependencyMessageReporter;
import com.android.tools.idea.gradle.project.sync.setup.DependencySetupErrors;
import com.android.tools.idea.gradle.project.sync.setup.DependencySetupErrors.MissingModule;
import com.android.tools.idea.gradle.service.notification.hyperlink.NotificationHyperlink;
import com.google.common.collect.Sets;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemNotificationManager;
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory;
import com.intellij.openapi.externalSystem.service.notification.NotificationSource;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.android.tools.idea.gradle.project.sync.messages.GroupNames.FAILED_TO_SET_UP_DEPENDENCIES;
import static com.android.tools.idea.gradle.project.sync.messages.GroupNames.MISSING_DEPENDENCIES_BETWEEN_MODULES;
import static com.android.tools.idea.gradle.project.sync.messages.MessageType.ERROR;
import static com.android.tools.idea.gradle.project.sync.messages.MessageType.WARNING;
import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile;
import static com.android.tools.idea.gradle.util.Projects.getSkipSyncIssueReporting;
import static com.android.tools.idea.gradle.util.Projects.setHasSyncErrors;
import static com.intellij.openapi.externalSystem.service.notification.NotificationSource.PROJECT_SYNC;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.util.ArrayUtil.toStringArray;

/**
 * Service that collects and displays in the "Messages" tool window, post-sync project setup messages (errors, warnings, etc.)
 */
public class SyncMessages {
  private static final NotificationSource NOTIFICATION_SOURCE = PROJECT_SYNC;

  @NotNull private final Project myProject;
  @NotNull private final ExternalSystemNotificationManager myNotificationManager;

  @NotNull private final SyncMessageReporter myMessageReporter;
  @NotNull private final UnresolvedDependencyMessageReporter myUnresolvedDependencyMessageReporter;
  @NotNull private final SyncIssuesMessageReporter mySyncIssuesMessageReporter;

  @NotNull
  public static SyncMessages getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, SyncMessages.class);
  }

  public int getErrorCount() {
    return myNotificationManager.getMessageCount(null, NOTIFICATION_SOURCE, NotificationCategory.ERROR, GRADLE_SYSTEM_ID);
  }

  public SyncMessages(@NotNull Project project, @NotNull ExternalSystemNotificationManager manager) {
    myProject = project;
    myNotificationManager = manager;
    myMessageReporter = new SyncMessageReporter(project, manager);
    myUnresolvedDependencyMessageReporter = new UnresolvedDependencyMessageReporter(myMessageReporter);
    mySyncIssuesMessageReporter = new SyncIssuesMessageReporter(myMessageReporter);
  }

  public int getMessageCount(@NotNull String groupName) {
    return myNotificationManager.getMessageCount(groupName, NOTIFICATION_SOURCE, null, GRADLE_SYSTEM_ID);
  }

  public boolean isEmpty() {
    return myNotificationManager.getMessageCount(NOTIFICATION_SOURCE, null, GRADLE_SYSTEM_ID) == 0;
  }

  public void reportComponentIncompatibilities() {
    VersionCompatibilityService compatibilityService = VersionCompatibilityService.getInstance();
    List<SyncMessage> messages = compatibilityService.checkComponentCompatibility(myProject);
    for (SyncMessage message : messages) {
      report(message);
    }
    if (!messages.isEmpty()) {
      setHasSyncErrors(myProject, true);
    }
  }

  public void reportSyncIssues(@NotNull Collection<SyncIssue> syncIssues, @NotNull Module module) {
    mySyncIssuesMessageReporter.reportSyncIssues(syncIssues, module);
  }

  public void reportUnresolvedDependencies(@NotNull Collection<String> unresolvedDependencies, @NotNull Module module) {
    if (unresolvedDependencies.isEmpty() || getSkipSyncIssueReporting(module.getProject())) {
      return;
    }
    VirtualFile buildFile = getGradleBuildFile(module);
    for (String dependency : unresolvedDependencies) {
      myUnresolvedDependencyMessageReporter.report(dependency, module, buildFile);
    }
    setHasSyncErrors(myProject, true);
  }

  public void reportDependencySetupErrors() {
    DependencySetupErrors setupErrors = DependencySetupErrors.getInstance(myProject);
    reportModulesNotFoundErrors(setupErrors);
    setupErrors.clear();
  }

  private void reportModulesNotFoundErrors(@NotNull DependencySetupErrors setupErrors) {
    reportModulesNotFoundIssues(MISSING_DEPENDENCIES_BETWEEN_MODULES, setupErrors.getMissingModules());

    for (String dependent : setupErrors.getMissingNames()) {
      String msg = String.format("Module '%1$s' depends on modules that do not have a name.", dependent);
      report(new SyncMessage(FAILED_TO_SET_UP_DEPENDENCIES, ERROR, msg));
    }

    for (String dependent : setupErrors.getDependentsOnLibrariesWithoutBinaryPath()) {
      String msg = String.format("Module '%1$s' depends on libraries that do not have a 'binary' path.", dependent);
      report(new SyncMessage(FAILED_TO_SET_UP_DEPENDENCIES, ERROR, msg));
    }

    for (DependencySetupErrors.InvalidModuleDependency dependency : setupErrors.getInvalidModuleDependencies()) {
      String msg = String.format("Ignoring dependency of module '%1$s' on module '%2$s'. %3$s",
                                 dependency.dependent, dependency.dependency.getName(), dependency.detail);
      VirtualFile buildFile = getGradleBuildFile(dependency.dependency);
      assert buildFile != null;
      OpenFileDescriptor navigatable = new OpenFileDescriptor(dependency.dependency.getProject(), buildFile, 0);
      report(new SyncMessage(FAILED_TO_SET_UP_DEPENDENCIES, WARNING, navigatable, msg));
    }

    reportModulesNotFoundIssues(FAILED_TO_SET_UP_DEPENDENCIES, setupErrors.getMissingModulesWithBackupLibraries());
  }

  private void reportModulesNotFoundIssues(@NotNull String groupName, @NotNull List<MissingModule> missingModules) {
    if (!missingModules.isEmpty()) {
      MessageType type = ERROR;

      for (MissingModule missingModule : missingModules) {
        List<String> messageLines = new ArrayList<>();

        StringBuilder text = new StringBuilder();
        text.append(String.format("Unable to find module with Gradle path '%1$s' (needed by module", missingModule.dependencyPath));

        addDependentsToText(text, missingModule.getDependentNames());
        text.append(".)");
        messageLines.add(text.toString());

        String backupLibraryName = missingModule.backupLibraryName;
        if (isNotEmpty(backupLibraryName)) {
          type = WARNING;
          String msg = String.format("Linking to library '%1$s' instead.", backupLibraryName);
          messageLines.add(msg);
        }
        report(new SyncMessage(groupName, type, toStringArray(messageLines)));
      }

      // If the project is really a subset of the project, attempt to find and include missing modules.
      ProjectSubset projectSubset = ProjectSubset.getInstance(myProject);
      String[] selection = projectSubset.getSelection();
      boolean hasSelection = selection != null && selection.length > 0;
      if (type == ERROR && hasSelection && projectSubset.hasCachedModules()) {
        String text = "The missing modules may have been excluded from the project subset.";
        SyncMessage message = new SyncMessage(groupName, MessageType.INFO, text);
        message.add(new IncludeMissingModulesHyperlink(missingModules));
        report(message);
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

  public void removeMessages(@NotNull String... groupNames) {
    for (String groupName : groupNames) {
      myNotificationManager.clearNotifications(groupName, NOTIFICATION_SOURCE, GRADLE_SYSTEM_ID);
    }
  }

  public void report(@NotNull SyncMessage message) {
    myMessageReporter.report(message);
  }

  /**
   * "Quick Fix" link that attempts to find and include any modules that were not previously included in the project subset.
   */
  private static class IncludeMissingModulesHyperlink extends NotificationHyperlink {
    @NotNull private final Set<String> myModuleGradlePaths;

    IncludeMissingModulesHyperlink(@NotNull List<MissingModule> missingModules) {
      super("include.missing.modules", "Find and include missing modules");
      myModuleGradlePaths = Sets.newHashSetWithExpectedSize(missingModules.size());
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
