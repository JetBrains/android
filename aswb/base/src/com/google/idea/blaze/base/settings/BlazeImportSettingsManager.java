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
package com.google.idea.blaze.base.settings;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.idea.blaze.base.projectview.ProjectViewManager.migrateImportSettingsToProjectViewFile;

import com.google.idea.blaze.base.async.executor.ProgressiveTaskWithProgressIndicator;
import com.google.idea.blaze.base.project.QuerySyncConversionUtility;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import com.google.idea.blaze.base.projectview.section.sections.UseQuerySyncSection;
import com.google.idea.blaze.base.projectview.section.sections.WorkspaceLocationSection;
import com.google.idea.blaze.base.qsync.QuerySyncManager;
import com.google.idea.blaze.base.qsync.settings.QuerySyncSettings;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.scopes.ToolWindowScopeRunner;
import com.google.idea.blaze.exception.BuildException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

/**
 * Manages storage for the project's {@link BlazeImportSettings}.
 */
@State(name = "BlazeImportSettings", storages = @Storage(file = StoragePathMacros.WORKSPACE_FILE))
public class BlazeImportSettingsManager implements PersistentStateComponent<BlazeImportSettings> {
  private static final Logger logger = Logger.getInstance(BlazeImportSettingsManager.class);


  private final AtomicReference<BlazeImportSettings> importSettings = new AtomicReference<>(null);

  private final Project project;
  private final QuerySyncConversionUtility querySyncConversionUtility;
  @Nullable private BlazeImportSettings loadedImportSettings;

  public BlazeImportSettingsManager(Project project) {
    this.project = project;
    this.querySyncConversionUtility = project.getService(QuerySyncConversionUtility.class);
  }

  public static BlazeImportSettingsManager getInstance(Project project) {
    return project.getService(BlazeImportSettingsManager.class);
  }

  @Nullable
  @Override
  public BlazeImportSettings getState() {
    BlazeImportSettings existingImportSettings = importSettings.get();
    return existingImportSettings != null ? existingImportSettings : loadedImportSettings;
  }

  @Override
  public void loadState(BlazeImportSettings importSettings) {
    this.loadedImportSettings = importSettings;
  }

  @Nullable
  public BlazeImportSettings getImportSettings() {
    synchronized (this) {
      final var result = importSettings.get();
      if (result != null) return result;
      BlazeImportSettingsManager.getInstance(project).initImportSettings(
        Optional.ofNullable(BlazeImportSettingsManager.getInstance(project).loadedImportSettings));
      return importSettings.get();
    }
  }

  private void initImportSettings(Optional<BlazeImportSettings> loadedImportSettings) {
    final var projectBasePath = project.getBasePath();
    if (projectBasePath == null) {
      // For example the default project accessed from the Settings dialog.
      return;
    }
    final var defaultProjectType = QuerySyncSettings.getInstance().useQuerySync() ? BlazeImportSettings.ProjectType.QUERY_SYNC
                                                                                  : BlazeImportSettings.ProjectType.ASPECT_SYNC;
    // Loaded import settings are previous settings stored in `.idea` directory. Any values that changed in `.bazelproject` file take
    // precedence over previously stored values.

    final var projectName =
      loadedImportSettings
        .map(BlazeImportSettings::getProjectName)
        .flatMap(it -> isNullOrEmpty(it) ? Optional.empty() : Optional.of(it))
        .orElse(project.getName());
    final var locationHash =
      loadedImportSettings.map(BlazeImportSettings::getLocationHash).orElseGet(() -> createLocationHash(projectName));

    final var projectViewFile =
      Stream.of(Path.of(projectBasePath, ".blazeproject"), Path.of(projectBasePath, ".bazelproject"))
        .filter(Files::exists)
        .findFirst();
    if (projectViewFile.isEmpty()) {
      return;
    }

    final var projectViewFilePath = projectViewFile.get();
    final var topLevelProjectViewFile = parseTopLevelProjectViewFile(projectViewFilePath.toFile());
    final var topLevelProjectView = Objects.requireNonNull(topLevelProjectViewFile).projectView;

    final var projectViewProjectType =
      Optional.ofNullable(topLevelProjectView.getScalarValue(UseQuerySyncSection.KEY))
        .map(querySync -> (querySync
                           ? BlazeImportSettings.ProjectType.QUERY_SYNC
                           : BlazeImportSettings.ProjectType.ASPECT_SYNC));
    final var projectViewWorkspaceLocation = Optional.ofNullable(topLevelProjectView.getScalarValue(WorkspaceLocationSection.KEY));

    final var workspaceLocation = projectViewWorkspaceLocation.or(() -> loadedImportSettings.map(BlazeImportSettings::getWorkspaceRoot));
    if (workspaceLocation.isEmpty()) {
      return;
    }
    final var projectType =
      projectViewProjectType.or(() -> loadedImportSettings.map(BlazeImportSettings::getProjectType)).orElse(defaultProjectType);
    final var buildSystem = projectViewFilePath.endsWith(".bazelproject") ? BuildSystemName.Bazel : BuildSystemName.Blaze;

    String workspaceRoot = workspaceLocation.get();
    final var importSettings =
      new BlazeImportSettings(workspaceRoot, projectName, projectBasePath, locationHash, projectViewFilePath.toString(),
                              buildSystem, projectType);

    if (querySyncConversionUtility.canConvert(projectViewFilePath)) {
      importSettings.setProjectType(BlazeImportSettings.ProjectType.QUERY_SYNC);
      querySyncConversionUtility.backupExistingProjectDirectories();
    }

    this.importSettings.set(importSettings);
  }

  private ProjectViewSet.ProjectViewFile parseTopLevelProjectViewFile(File projectViewFile) {
    ProjectViewParser parser = new ProjectViewParser(BlazeContext.create(), null);
    parser.parseProjectView(projectViewFile,
                            List.of(WorkspaceLocationSection.PARSER, UseQuerySyncSection.PARSER));
    ProjectViewSet projectViewSet = parser.getResult();
    return projectViewSet.getTopLevelProjectViewFile();
  }

  public void initProjectView() {
    try {
      reloadProjectView();
    }
    catch (BuildException e) {
      throw new RuntimeException(e);
    }
  }

  @TestOnly
  public void setImportSettings(BlazeImportSettings importSettings) {
    this.importSettings.set(importSettings);
  }

  private final AtomicReference<ProjectViewSet> projectViewSet = new AtomicReference<>();

  public ProjectViewSet getProjectViewSet() {
    return projectViewSet.get();
  }

  public ProjectViewSet reloadProjectView() throws BuildException {
    try {
      // Some IDE actions reload the project view in the EDT. Even though it is not right to do it needs to be handled.
      if (ApplicationManager.getApplication().isDispatchThread()) {
        new Task.Modal(project, "Parsing project view files", false) {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            try {
              reloadProjectViewUnderProgressAndWait();
            }
            catch (ExecutionException | InterruptedException e) {
              throw new RuntimeException(e);
            }
          }
        }.queue();
      }
      else {
        reloadProjectViewUnderProgressAndWait();
      }
      return projectViewSet.get();
    }
    catch (InterruptedException e) {
      throw new BuildException(e);
    }
    catch (ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  private void reloadProjectViewUnderProgressAndWait() throws InterruptedException, ExecutionException {
    // Not logging reading project view files as syncing.
    ProgressiveTaskWithProgressIndicator.builder(project, "Parsing project view files")
      .setCancelable(false)
      .submitTaskWithResult(((Function<ProgressIndicator, Boolean>)indicator ->
        ToolWindowScopeRunner.runTaskWithToolWindow(project, "Parsing project view files",
                                                    "Parsing project view files", QuerySyncManager.TaskOrigin.AUTOMATIC,
                                                    BlazeUserSettings.getInstance(), context -> {
            final var importSettings1 = getImportSettings();
            final var loadedProjectView = ProjectViewManager.getInstance(project).doLoadProjectView(context, importSettings1);
            migrateImportSettingsToProjectViewFile(project, importSettings1,
                                                   Objects.requireNonNull(loadedProjectView.getTopLevelProjectViewFile()));
            projectViewSet.set(loadedProjectView);
          }
        ))::apply).get();
  }

  public static String createLocationHash(String projectName) {
    String uuid = UUID.randomUUID().toString();
    uuid = uuid.substring(0, Math.min(uuid.length(), 8));
    return projectName.replaceAll("[^a-zA-Z0-9]", "") + "-" + uuid;
  }
}
