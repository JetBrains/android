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
import com.google.idea.blaze.base.project.BazelProjectSystemId;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import com.google.idea.blaze.base.projectview.section.sections.UseQuerySyncSection;
import com.google.idea.blaze.base.projectview.section.sections.WorkspaceLocationSection;
import com.google.idea.blaze.base.qsync.QuerySyncManager;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.scopes.ToolWindowScopeRunner;
import com.google.idea.blaze.common.PrintOutput;
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

/** Manages storage for the project's {@link BlazeImportSettings}. */
@State(name = "BlazeImportSettings", storages = @Storage(file = StoragePathMacros.WORKSPACE_FILE))
public class BlazeImportSettingsManager
    implements PersistentStateComponent<BlazeImportSettings>, BazelImportSettingsManager {
  private static final Logger logger = Logger.getInstance(BlazeImportSettingsManager.class);

  private final AtomicReference<ActualImportSettings> importSettings = new AtomicReference<>(null);

  private final Project project;
  @Nullable private BlazeImportSettings loadedImportSettings;

  public BlazeImportSettingsManager(Project project) {
    this.project = project;
  }

  @TestOnly
  public static BlazeImportSettingsManager getInstanceForTestingOnly(Project project) {
    return (BlazeImportSettingsManager) BazelImportSettingsManager.getInstance(project);
  }

  @Nullable
  @Override
  public BlazeImportSettings getState() {
    ActualImportSettings current = importSettings.get();
    if (current == null) {
      return loadedImportSettings;
    }
    return new BlazeImportSettings(
        current.workspaceRoot().toString(),
        current.projectName(),
        current.projectViewFilePath().toString(),
        current.buildSystem());
  }

  @Override
  public void loadState(BlazeImportSettings importSettings) {
    this.loadedImportSettings = importSettings;
  }

  @Nullable
  private ActualImportSettings getImportSettings() {
    synchronized (this) {
      final var result = importSettings.get();
      if (result != null) return result;
      if (!BazelProjectSystemId.isActive(project)) {
        return null;
      }
      initImportSettings(Optional.ofNullable(loadedImportSettings));
      return importSettings.get();
    }
  }

  /** Returns whether the project has import settings. */
  @Override
  public boolean hasImportSettings() {
    return getImportSettings() != null;
  }

  /**
   * Returns the workspace root path if configured and the project is a Blaze/Bazel project, null
   * otherwise.
   */
  @Override
  @Nullable
  public Path getWorkspaceRoot() {
    ActualImportSettings settings = getImportSettings();
    return settings != null ? settings.workspaceRoot() : null;
  }

  /**
   * Returns the project name if configured and the project is a Blaze/Bazel project, null
   * otherwise.
   */
  @Override
  @Nullable
  public String getProjectName() {
    ActualImportSettings settings = getImportSettings();
    return settings != null ? settings.projectName() : null;
  }

  /**
   * Returns the project view file path if configured and the project is a Blaze/Bazel project, null
   * otherwise.
   */
  @Override
  @Nullable
  public Path getProjectViewFilePath() {
    ActualImportSettings settings = getImportSettings();
    return settings != null ? settings.projectViewFilePath() : null;
  }

  /**
   * Returns the build system used by the project, or null if the project is not a Blaze/Bazel
   * project.
   */
  @Override
  @Nullable
  public BuildSystemName getBuildSystem() {
    ActualImportSettings settings = getImportSettings();
    return settings != null ? settings.buildSystem() : null;
  }

  private void initImportSettings(Optional<BlazeImportSettings> loadedImportSettings) {
    loadImportSettings(
            project.getBasePath(),
            project.getName(),
            loadedImportSettings.map(BlazeImportSettings::getProjectName),
            loadedImportSettings.map(BlazeImportSettings::getWorkspaceRoot))
        .ifPresent(
            settings -> {
              // Phase 1: Unconditional physical workspace mapping
              String effectiveRoot = settings.getWorkspaceRoot();
              var actual =
                  new ActualImportSettings(
                      Path.of(effectiveRoot),
                      settings.getProjectName(),
                      Path.of(settings.getProjectViewFile()),
                      settings.getBuildSystem());
              this.importSettings.set(actual);
            });
  }

  public static Optional<BlazeImportSettings> loadImportSettings(
      String projectBasePath,
      String projectName,
      Optional<String> loadedProjectName,
      Optional<String> loadedWorkspaceRoot) {
    // Loaded import settings are previous settings stored in `.idea` directory. Any values that
    // changed in `.bazelproject` file take
    // precedence over previously stored values.

    final var effectiveProjectName =
        loadedProjectName
            .flatMap(it -> isNullOrEmpty(it) ? Optional.empty() : Optional.of(it))
            .orElse(projectName);

    final var projectViewFile =
        Stream.of(
                Path.of(projectBasePath, ".blazeproject"),
                Path.of(projectBasePath, ".bazelproject"))
            .filter(Files::exists)
            .findFirst();
    if (projectViewFile.isEmpty()) {
      return Optional.empty();
    }

    final var projectViewFilePath = projectViewFile.get();
    final var topLevelProjectViewFile = parseTopLevelProjectViewFile(projectViewFilePath.toFile());
    final var topLevelProjectView = Objects.requireNonNull(topLevelProjectViewFile).projectView;

    final var projectViewWorkspaceLocation =
        Optional.ofNullable(topLevelProjectView.getScalarValue(WorkspaceLocationSection.KEY));

    final var workspaceLocation = projectViewWorkspaceLocation.or(() -> loadedWorkspaceRoot);
    if (workspaceLocation.isEmpty()) {
      return Optional.empty();
    }
    final var buildSystem =
        projectViewFilePath.endsWith(".bazelproject")
            ? BuildSystemName.Bazel
            : BuildSystemName.Blaze;

    String workspaceRoot = workspaceLocation.get();
    final var importSettings =
        new BlazeImportSettings(
            workspaceRoot, effectiveProjectName, projectViewFilePath.toString(), buildSystem);

    return Optional.of(importSettings);
  }

  private static ProjectViewSet.ProjectViewFile parseTopLevelProjectViewFile(File projectViewFile) {
    ProjectViewParser parser = new ProjectViewParser(BlazeContext.create(), null);
    parser.parseProjectViewFile(
        projectViewFile, List.of(WorkspaceLocationSection.PARSER, UseQuerySyncSection.PARSER));
    ProjectViewSet projectViewSet = parser.getResult();
    return projectViewSet.getTopLevelProjectViewFile();
  }

  @TestOnly
  public void setImportSettingsForTests(
      Path workspaceRoot,
      String projectName,
      Path projectViewFilePath,
      BuildSystemName buildSystem) {
    this.importSettings.set(
        new ActualImportSettings(workspaceRoot, projectName, projectViewFilePath, buildSystem));
  }

  @TestOnly
  public void setImportSettingsForTests(Path workspaceRoot, BuildSystemName buildSystem) {
    setImportSettingsForTests(
        workspaceRoot, "test-project", workspaceRoot.resolve(".bazelproject"), buildSystem);
  }

  private final AtomicReference<ProjectViewSet> projectViewSet = new AtomicReference<>();

  @Override
  public ProjectViewSet getProjectViewSet() {
    return projectViewSet.get();
  }

  @Override
  public ProjectViewSet reloadProjectView() throws BuildException {
    try {
      // Some IDE actions reload the project view in the EDT. Even though it is not right to do it
      // needs to be handled.
      if (ApplicationManager.getApplication().isDispatchThread()) {
        new Task.Modal(project, "Parsing project view files", false) {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            try {
              reloadProjectViewUnderProgressAndWait();
            } catch (ExecutionException | InterruptedException e) {
              throw new RuntimeException(e);
            }
          }
        }.queue();
      } else {
        reloadProjectViewUnderProgressAndWait();
      }
      return projectViewSet.get();
    } catch (InterruptedException e) {
      throw new BuildException(e);
    } catch (ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  private void reloadProjectViewUnderProgressAndWait()
      throws InterruptedException, ExecutionException {
    // Not logging reading project view files as syncing.
    ProgressiveTaskWithProgressIndicator.builder(project, "Parsing project view files")
        .setCancelable(false)
        .submitTaskWithResult(
            ((Function<ProgressIndicator, Boolean>)
                    indicator ->
                        ToolWindowScopeRunner.runTaskWithToolWindow(
                            project,
                            "Parsing project view files",
                            "Parsing project view files",
                            QuerySyncManager.TaskOrigin.AUTOMATIC,
                            BlazeUserSettings.getInstance(),
                            context -> {
                              final var projectViewFilePath = getProjectViewFilePath();
                              final var workspaceRoot = getWorkspaceRoot();
                              var loadedProjectView =
                                  ProjectViewManager.getInstance(project)
                                      .doLoadProjectView(
                                          context, projectViewFilePath, workspaceRoot);
                              final var migrated =
                                  migrateImportSettingsToProjectViewFile(
                                      Objects.requireNonNull(workspaceRoot).toString(),
                                      Objects.requireNonNull(
                                          loadedProjectView.getTopLevelProjectViewFile()));
                              if (migrated) {
                                context.output(
                                    PrintOutput.output(
                                        "Some project settings have been migrated to .bazelproject"
                                            + " file. Re-parsing..."));
                                loadedProjectView =
                                    ProjectViewManager.getInstance(project)
                                        .doLoadProjectView(
                                            context, projectViewFilePath, workspaceRoot);
                              }
                              projectViewSet.set(loadedProjectView);
                              final var activeSettings = getImportSettings();
                              final var legacySettings =
                                  activeSettings == null
                                      ? Optional.<BlazeImportSettings>empty()
                                      : Optional.of(
                                          new BlazeImportSettings(
                                              activeSettings.workspaceRoot().toString(),
                                              activeSettings.projectName(),
                                              activeSettings.projectViewFilePath().toString(),
                                              activeSettings.buildSystem()));
                              initImportSettings(legacySettings);
                            }))
                ::apply)
        .get();
  }

  public static String createLocationHash(String projectName) {
    String uuid = UUID.randomUUID().toString();
    uuid = uuid.substring(0, Math.min(uuid.length(), 8));
    return projectName.replaceAll("[^a-zA-Z0-9]", "") + "-" + uuid;
  }

  private static record ActualImportSettings(
      Path workspaceRoot,
      String projectName,
      Path projectViewFilePath,
      BuildSystemName buildSystem) {}
}
