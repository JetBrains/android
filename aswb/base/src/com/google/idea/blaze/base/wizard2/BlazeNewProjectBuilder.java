/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.wizard2;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.base.logging.EventLoggingService;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectView;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.ProjectViewStorageManager;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import com.google.idea.blaze.base.projectview.section.sections.UseQuerySyncSection;
import com.google.idea.blaze.base.qsync.QuerySync;
import com.google.idea.blaze.base.qsync.settings.QuerySyncSettings;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

/** Contains the state to build a new project throughout the new project wizard process. */
public final class BlazeNewProjectBuilder {
  // The import wizard should keep this many items around for fields that care about history
  public static final int HISTORY_SIZE = 8;

  // Stored in user settings as the last imported workspace
  private static final String LAST_IMPORTED_BLAZE_WORKSPACE =
      "blaze-wizard.last-imported-workspace";
  private static final String LAST_IMPORTED_BAZEL_WORKSPACE =
      "blaze-wizard.last-imported-bazel-workspace";
  private static final String HISTORY_SEPARATOR = "::";

  private static String lastImportedWorkspaceKey(BuildSystemName buildSystemName) {
    switch (buildSystemName) {
      case Blaze:
        return LAST_IMPORTED_BLAZE_WORKSPACE;
      case Bazel:
        return LAST_IMPORTED_BAZEL_WORKSPACE;
      default:
        throw new RuntimeException("Unrecognized build system type: " + buildSystemName);
    }
  }

  private final BlazeWizardUserSettings userSettings;
  @Nullable private WorkspaceTypeData workspaceData;
  private BlazeSelectProjectViewOption projectViewOption;
  private File projectViewFile;
  private ProjectView projectView;
  private ProjectViewSet projectViewSet;
  private String projectName;
  private String projectDataDirectory;
  private WorkspaceRoot workspaceRoot;

  public BlazeNewProjectBuilder() {
    this.userSettings = BlazeWizardUserSettingsStorage.getInstance().copyUserSettings();
  }

  public BlazeWizardUserSettings getUserSettings() {
    return userSettings;
  }

  public String getLastImportedWorkspace(BuildSystemName buildSystemName) {
    List<String> workspaceHistory = getWorkspaceHistory(buildSystemName);
    return workspaceHistory.isEmpty() ? "" : workspaceHistory.get(0);
  }

  public List<String> getWorkspaceHistory(BuildSystemName buildSystemName) {
    String value = userSettings.get(lastImportedWorkspaceKey(buildSystemName), "");
    return Strings.isNullOrEmpty(value)
        ? ImmutableList.of()
        : Arrays.asList(value.split(HISTORY_SEPARATOR));
  }

  private void writeWorkspaceHistory(BuildSystemName buildSystemName, String newValue) {
    List<String> history = Lists.newArrayList(getWorkspaceHistory(buildSystemName));
    history.remove(newValue);
    history.add(0, newValue);
    while (history.size() > HISTORY_SIZE) {
      history.remove(history.size() - 1);
    }
    userSettings.put(
        lastImportedWorkspaceKey(buildSystemName), Joiner.on(HISTORY_SEPARATOR).join(history));
  }

  @Nullable
  public WorkspaceTypeData getWorkspaceData() {
    return workspaceData;
  }

  public BlazeSelectProjectViewOption getProjectViewOption() {
    return projectViewOption;
  }

  public String getProjectName() {
    return projectName;
  }

  public ProjectView getProjectView() {
    return projectView;
  }

  public ProjectViewSet getProjectViewSet() {
    return projectViewSet;
  }

  public String getProjectDataDirectory() {
    return projectDataDirectory;
  }

  @Nullable
  public BuildSystemName getBuildSystem() {
    return workspaceData != null ? workspaceData.buildSystem() : null;
  }

  public String getBuildSystemName() {
    BuildSystemName buildSystemName = getBuildSystem();
    return buildSystemName != null ? buildSystemName.getName() : Blaze.defaultBuildSystemName();
  }

  @CanIgnoreReturnValue
  public BlazeNewProjectBuilder setWorkspaceData(WorkspaceTypeData workspaceData) {
    this.workspaceData = workspaceData;
    return this;
  }

  @CanIgnoreReturnValue
  public BlazeNewProjectBuilder setProjectViewOption(
      BlazeSelectProjectViewOption projectViewOption) {
    this.projectViewOption = projectViewOption;
    return this;
  }

  @CanIgnoreReturnValue
  public BlazeNewProjectBuilder setProjectView(ProjectView projectView) {
    this.projectView = projectView;
    return this;
  }

  @CanIgnoreReturnValue
  public BlazeNewProjectBuilder setProjectViewFile(File projectViewFile) {
    this.projectViewFile = projectViewFile;
    return this;
  }

  @CanIgnoreReturnValue
  public BlazeNewProjectBuilder setProjectViewSet(ProjectViewSet projectViewSet) {
    this.projectViewSet = projectViewSet;
    return this;
  }

  @CanIgnoreReturnValue
  public BlazeNewProjectBuilder setProjectName(String projectName) {
    this.projectName = projectName;
    return this;
  }

  @CanIgnoreReturnValue
  public BlazeNewProjectBuilder setProjectDataDirectory(String projectDataDirectory) {
    this.projectDataDirectory = projectDataDirectory;
    return this;
  }

  /** Commits the project. May report errors. */
  public void commit() throws BlazeProjectCommitException {
    this.workspaceRoot = workspaceData.workspaceRoot();

    projectViewOption.commit();

    BuildSystemName buildSystemName = workspaceData.buildSystem();
    writeWorkspaceHistory(buildSystemName, workspaceRoot.toString());

    if (!StringUtil.isEmpty(projectDataDirectory)) {
      File projectDataDir = new File(projectDataDirectory);
      if (!projectDataDir.exists()) {
        if (!projectDataDir.mkdirs()) {
          throw new BlazeProjectCommitException(
              "Unable to create the project directory: " + projectDataDirectory);
        }
      }
    }

    try {
      ProjectViewStorageManager.getInstance()
          .writeProjectView(ProjectViewParser.projectViewToString(projectView), projectViewFile);
    } catch (IOException e) {
      throw new BlazeProjectCommitException("Could not create project view file", e);
    }
    BlazeImportSettingsManager.setPendingProjectSettings(getImportSettings());
  }

  /**
   * Commits the project data. This method mustn't fail, because the project has already been
   * created.
   */
  void commitToProject(Project project) {
    BlazeWizardUserSettingsStorage.getInstance().commit(userSettings);
    EventLoggingService.getInstance()
        .logEvent(getClass(), "blaze-project-created", ImmutableMap.copyOf(userSettings.values));

    BlazeImportSettingsManager.getInstance(project).setImportSettings(getImportSettings());
    // Initial sync of the project happens in BlazeSyncStartupActivity
  }

  /**
   * Checks if a new project should be query sync project.
   *
   * <p>There are two ways to get query sync enabled for new project: via blaze project view and via
   * query sync settings. blaze project view file take higher priority.
   */
  private boolean isQuerySyncProject() {
    ProjectViewParser projectViewParser =
        new ProjectViewParser(BlazeContext.create(), new WorkspacePathResolverImpl(workspaceRoot));
    projectViewParser.parseProjectView(projectViewFile);
    return projectViewParser
        .getResult()
        .getScalarValue(UseQuerySyncSection.KEY)
        .orElse(useQuerySyncDefault());
  }

  /** Checks if query sync for new project is enabled via experiment or settings page. */
  private boolean useQuerySyncDefault() {
    if (QuerySync.useByDefault()) {
      return QuerySyncSettings.getInstance().useQuerySync();
    } else {
      return QuerySync.isLegacyExperimentEnabled()
          || QuerySyncSettings.getInstance().useQuerySyncBeta();
    }
  }

  private BlazeImportSettings getImportSettings() {
    return new BlazeImportSettings(
        workspaceRoot.directory().getPath(),
        projectName,
        projectDataDirectory,
        Optional.ofNullable(projectViewFile).map(File::getPath).orElse(null),
        getBuildSystem(),
        isQuerySyncProject() ? ProjectType.QUERY_SYNC : ProjectType.ASPECT_SYNC);
  }
}
