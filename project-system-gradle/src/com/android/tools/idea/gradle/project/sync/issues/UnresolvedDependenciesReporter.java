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
package com.android.tools.idea.gradle.project.sync.issues;

import static com.android.tools.idea.gradle.util.GradleProjects.isOfflineBuildModeEnabled;

import com.android.tools.idea.gradle.model.IdeSyncIssue;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel;
import com.android.tools.idea.gradle.project.sync.hyperlink.AddGoogleMavenRepositoryHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.DisableOfflineModeHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.ShowDependencyInProjectStructureHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.ShowSyncIssuesDetailsHyperlink;
import com.android.tools.idea.project.messages.MessageType;
import com.android.tools.idea.project.messages.SyncMessage;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

public class UnresolvedDependenciesReporter extends SimpleDeduplicatingSyncIssueReporter {
  private static final String UNRESOLVED_DEPENDENCIES_GROUP = "Unresolved dependencies";
  private boolean myAssumeProjectNotInitialized = false;

  @Override
  int getSupportedIssueType() {
    return IdeSyncIssue.TYPE_UNRESOLVED_DEPENDENCY;
  }

  @NotNull
  @Override
  protected List<SyncIssueNotificationHyperlink> getCustomLinks(@NotNull Project project,
                                                                @NotNull List<IdeSyncIssue> syncIssues,
                                                                @NotNull List<Module> affectedModules,
                                                                @NotNull Map<Module, VirtualFile> buildFileMap) {
    assert !syncIssues.isEmpty() && !affectedModules.isEmpty();
    IdeSyncIssue issue = syncIssues.get(0);
    String dependency = issue.getData();

    List<SyncIssueNotificationHyperlink> quickFixes = new ArrayList<>();
    if (dependency == null) {

      if (isOfflineBuildModeEnabled(project)) {
        quickFixes.add(0, new DisableOfflineModeHyperlink());
      }
    }
    else {
      GradleCoordinate coordinate = GradleCoordinate.parseCoordinateString(dependency);
      List<VirtualFile> buildFiles = ContainerUtil.map(affectedModules, m -> buildFileMap.get(m));
      Module module = affectedModules.get(0);

      if (dependency.startsWith("com.android.support") || dependency.startsWith("androidx.")
          || dependency.startsWith("com.google.android")) {
        addGoogleMavenRepositoryHyperlink(project, buildFiles, quickFixes);
      }
      else {
        if (isOfflineBuildModeEnabled(project)) {
          quickFixes.add(new DisableOfflineModeHyperlink());
        }
      }

      if (IdeInfo.getInstance().isAndroidStudio()) {
        if (coordinate != null) {
          quickFixes.add(new ShowDependencyInProjectStructureHyperlink(module, coordinate));
        }
      }
    }

    List<String> extraInfo = new ArrayList<>();
    try {
      List<String> multiLineMessage = issue.getMultiLineMessage();
      if (multiLineMessage != null && !issue.getMultiLineMessage().isEmpty()) {
        extraInfo.addAll(multiLineMessage);
      }
    }
    catch (UnsupportedOperationException ex) {
      // IdeSyncIssue.getMultiLineMessage() is not available for pre 3.0 plugins.
    }

    if (!extraInfo.isEmpty()) {
      try {
        String encodedMessage = URLEncoder.encode(issue.getMessage(), "UTF-8").replace("+", " ");
        quickFixes.add(new ShowSyncIssuesDetailsHyperlink(encodedMessage, extraInfo));
      }
      catch (UnsupportedEncodingException e) {
        quickFixes.add(new ShowSyncIssuesDetailsHyperlink(issue.getMessage(), extraInfo));
      }
    }

    return quickFixes;
  }

  @Override
  protected @NotNull SyncMessage setupSyncMessage(@NotNull Project project,
                                                  @NotNull List<IdeSyncIssue> syncIssues,
                                                  @NotNull List<Module> affectedModules,
                                                  @NotNull Map<Module, VirtualFile> buildFileMap,
                                                  @NotNull MessageType type) {
    var syncMessage =
      super.setupSyncMessage(project, syncIssues, affectedModules, buildFileMap, type);
    syncMessage = new SyncMessage(UNRESOLVED_DEPENDENCIES_GROUP, syncMessage.getType(), syncMessage.getNavigatable(), syncMessage.getText());

    String dependency = syncIssues.get(0).getData();
    if (dependency == null) {
      return syncMessage;
    }

    String message = "Failed to resolve: " + dependency;
    syncMessage = new SyncMessage(syncMessage.getGroup(), syncMessage.getType(), syncMessage.getNavigatable(), message);
    return syncMessage;
  }


  /**
   * Append a quick fix to add Google Maven repository to solve dependencies in a module in a list of fixes if needed.
   *
   * @param project    the project
   * @param buildFiles Build files where the dependencies are.
   * @param fixes      List of hyperlinks in which the quickfix will be added if the repository is not already used.
   */
  private void addGoogleMavenRepositoryHyperlink(@NotNull Project project,
                                                 @NotNull List<VirtualFile> buildFiles,
                                                 @NotNull List<SyncIssueNotificationHyperlink> fixes) {
    if ((!project.isInitialized()) || myAssumeProjectNotInitialized) {
      // No way to tell if the project contains the Google repository, add quick fix anyway (it will do nothing if it already has it)
      fixes.add(new AddGoogleMavenRepositoryHyperlink(project));
      return;
    }

    ProjectBuildModel projectBuildModel = ProjectBuildModel.getOrLog(project);
    if (projectBuildModel == null) {
      return;
    }

    // Check modules
    List<VirtualFile> filesToFix = new ArrayList<>();
    for (VirtualFile file : buildFiles) {
      if (file != null && file.isValid()) {
        GradleBuildModel moduleModel = projectBuildModel.getModuleBuildModel(file);

        if (!moduleModel.repositories().hasGoogleMavenRepository()) {
          filesToFix.add(file);
        }
      }
    }

    if (filesToFix.isEmpty()) {
      // All modules already have it
      return;
    }

    // Check project
    GradleBuildModel buildModel = projectBuildModel.getProjectBuildModel();
    if (buildModel != null) {
      if (!buildModel.repositories().hasGoogleMavenRepository()) {
        fixes.add(new AddGoogleMavenRepositoryHyperlink(ImmutableList.of(buildModel.getVirtualFile())));
        return;
      }
    }

    // Add to all modules
    fixes.add(new AddGoogleMavenRepositoryHyperlink(filesToFix));
  }

  @VisibleForTesting
  void assumeProjectNotInitialized(boolean assumeNotInitialized) {
    myAssumeProjectNotInitialized = assumeNotInitialized;
  }
}
