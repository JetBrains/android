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

import static com.android.builder.model.SyncIssue.TYPE_UNRESOLVED_DEPENDENCY;
import static com.android.tools.idea.gradle.util.GradleProjects.isOfflineBuildModeEnabled;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile;

import com.android.annotations.NonNull;
import com.android.builder.model.SyncIssue;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.project.sync.hyperlink.AddGoogleMavenRepositoryHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.DisableOfflineModeHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenFileHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.ShowDependencyInProjectStructureHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.ShowSyncIssuesDetailsHyperlink;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.project.messages.MessageType;
import com.android.tools.idea.sdk.AndroidSdks;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UnresolvedDependenciesReporter extends SimpleDeduplicatingSyncIssueReporter {
  private static final String UNRESOLVED_DEPENDENCIES_GROUP = "Unresolved dependencies";

  @NotNull
  public static UnresolvedDependenciesReporter getInstance() {
    return ServiceManager.getService(UnresolvedDependenciesReporter.class);
  }

  @Override
  int getSupportedIssueType() {
    return TYPE_UNRESOLVED_DEPENDENCY;
  }

  @Override
  @NotNull
  protected OpenFileHyperlink createModuleLink(@NotNull Project project,
                                               @NotNull Module module,
                                               @NotNull ProjectBuildModel projectBuildModel,
                                               @NotNull List<SyncIssue> syncIssues,
                                               @NotNull VirtualFile buildFile) {
    assert !syncIssues.isEmpty();
    // Get the dependency
    String dependency = syncIssues.get(0).getData();
    GradleBuildModel buildModel = projectBuildModel.getModuleBuildModel(buildFile);
    ArtifactDependencyModel dependencyModel =
      buildModel.dependencies().artifacts().stream().filter(artifact -> artifact.compactNotation().equals(dependency)).findFirst()
                .orElse(null);
    PsiElement element = dependencyModel == null ? null : dependencyModel.getPsiElement();
    int lineNumber = getLineNumberForElement(project, element);

    return new OpenFileHyperlink(buildFile.getPath(), module.getName(), lineNumber, -1);
  }

  @NotNull
  @Override
  protected Object getDeduplicationKey(@NotNull SyncIssue issue) {
    return issue;
  }

  @NotNull
  @Override
  protected List<NotificationHyperlink> getCustomLinks(@NotNull Project project,
                                                       @NotNull List<SyncIssue> syncIssues,
                                                       @NotNull List<Module> affectedModules,
                                                       @NotNull Map<Module, VirtualFile> buildFileMap) {
    assert !syncIssues.isEmpty() && !affectedModules.isEmpty();
    SyncIssue issue = syncIssues.get(0);
    String dependency = issue.getData();

    List<NotificationHyperlink> quickFixes = Lists.newArrayList();
    if (dependency == null) {
      List<String> extraInfo = new ArrayList<>();
      try {
        List<String> multiLineMessage = issue.getMultiLineMessage();
        if (multiLineMessage != null) {
          extraInfo.addAll(multiLineMessage);
        }
      }
      catch (UnsupportedOperationException ex) {
        // SyncIssue.getMultiLineMessage() is not available for pre 3.0 plugins.
      }

      if (!extraInfo.isEmpty()) {
        quickFixes.add(new ShowSyncIssuesDetailsHyperlink(issue.getMessage(), extraInfo));
      }

      if (isOfflineBuildModeEnabled(project)) {
        quickFixes.add(0, new DisableOfflineModeHyperlink());
      }

      return quickFixes;
    }
    else {
      GradleCoordinate coordinate = GradleCoordinate.parseCoordinateString(dependency);
      List<VirtualFile> buildFiles = affectedModules.stream().map(m -> buildFileMap.get(m)).collect(Collectors.toList());
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

      //TODO(b/130224064): PSD is empty for projects with KTS at this moment. Need to remove kts check when fixed
      if (IdeInfo.getInstance().isAndroidStudio() && buildFileMap.values().stream().noneMatch(GradleUtil::isKtsFile)) {
        if (coordinate != null) {
          quickFixes.add(new ShowDependencyInProjectStructureHyperlink(module, coordinate));
        }
      }

      return quickFixes;
    }
  }

  @NotNull
  @Override
  protected NotificationData setupNotificationData(@NotNull Project project,
                                                   @NotNull List<SyncIssue> syncIssues,
                                                   @NotNull List<Module> affectedModules,
                                                   @NotNull Map<Module, VirtualFile> buildFileMap,
                                                   @NotNull MessageType type) {
    NotificationData notificationData =
      super.setupNotificationData(project, syncIssues, affectedModules, buildFileMap, type);
    notificationData.setTitle(UNRESOLVED_DEPENDENCIES_GROUP);

    String dependency = syncIssues.get(0).getData();
    if (dependency == null) {
      return notificationData;
    }

    String message = "Failed to resolve: " + dependency;
    notificationData.setMessage(message);
    return notificationData;
  }


  public void report(@NotNull Collection<String> unresolvedDependencies, @NotNull Module module) {
    // TODO: Allow java modules to have sync issues.
    if (unresolvedDependencies.isEmpty()) {
      return;
    }
    VirtualFile buildFile = getGradleBuildFile(module);
    List<SyncIssue> syncIssues = unresolvedDependencies.stream().map(s -> new SyncIssue() {
      @Override
      public int getSeverity() {
        return SEVERITY_ERROR;
      }

      @Override
      public int getType() {
        return TYPE_UNRESOLVED_DEPENDENCY;
      }

      @Nullable
      @Override
      public String getData() {
        return s;
      }

      @NonNull
      @Override
      public String getMessage() {
        return s;
      }

      @Nullable
      @Override
      public List<String> getMultiLineMessage() {
        return null;
      }
    }).collect(Collectors.toList());

    SyncIssueUsageReporter syncIssueUsageReporter = SyncIssueUsageReporter.Companion.getInstance(module.getProject());
    reportAll(syncIssues, syncIssues.stream().collect(Collectors.toMap(Function.identity(), k -> module)),
              buildFile == null ? ImmutableMap.of() : ImmutableMap.of(module, buildFile), syncIssueUsageReporter);
  }

  @NotNull
  private static Collection<RemotePackage> getRemotePackages(@NotNull ProgressIndicator indicator) {
    AndroidSdkHandler sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler();
    RepositoryPackages packages = sdkHandler.getSdkManager(indicator).getPackages();
    return packages.getRemotePackages().values();
  }

  /**
   * Append a quick fix to add Google Maven repository to solve dependencies in a module in a list of fixes if needed.
   *
   * @param project    the project
   * @param buildFiles Build files where the dependencies are.
   * @param fixes      List of hyperlinks in which the quickfix will be added if the repository is not already used.
   */
  private static void addGoogleMavenRepositoryHyperlink(@NotNull Project project,
                                                        @NotNull List<VirtualFile> buildFiles,
                                                        @NotNull List<NotificationHyperlink> fixes) {
    if (!project.isInitialized()) {
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
}
