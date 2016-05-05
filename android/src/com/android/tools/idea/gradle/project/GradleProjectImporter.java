/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.project;

import com.android.tools.idea.gradle.*;
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.facet.JavaGradleFacet;
import com.android.tools.idea.gradle.facet.NativeAndroidGradleFacet;
import com.android.tools.idea.gradle.invoker.GradleInvoker;
import com.android.tools.idea.gradle.invoker.GradleTasksExecutor;
import com.android.tools.idea.gradle.project.build.GradleProjectBuilder;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.sdk.IdeSdks;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.externalSystem.service.project.ExternalProjectRefreshCallback;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.CompilerProjectExtension;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.SystemProperties;
import org.jetbrains.android.AndroidPlugin.GuiTestSuiteState;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.newProject.AndroidModuleBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;

import static com.android.SdkConstants.FN_BUILD_GRADLE;
import static com.android.tools.idea.gradle.AndroidProjectKeys.*;
import static com.android.tools.idea.gradle.project.LibraryAttachments.removeLibrariesAndStoreAttachments;
import static com.android.tools.idea.gradle.project.NewProjectImportGradleSyncListener.createTopLevelProjectAndOpen;
import static com.android.tools.idea.gradle.project.SdkSync.syncIdeAndProjectAndroidSdks;
import static com.android.tools.idea.gradle.util.FilePaths.pathToIdeaUrl;
import static com.android.tools.idea.gradle.util.GradleUtil.*;
import static com.android.tools.idea.gradle.util.Projects.*;
import static com.android.tools.idea.project.NewProjects.*;
import static com.android.tools.idea.startup.AndroidStudioInitializer.isAndroidStudio;
import static com.google.common.base.Strings.nullToEmpty;
import static com.intellij.notification.NotificationType.ERROR;
import static com.intellij.openapi.externalSystem.model.ProjectKeys.MODULE;
import static com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode.IN_BACKGROUND_ASYNC;
import static com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode.MODAL_SYNC;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.find;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.findAll;
import static com.intellij.openapi.externalSystem.util.ExternalSystemUtil.ensureToolWindowContentInitialized;
import static com.intellij.openapi.externalSystem.util.ExternalSystemUtil.refreshProject;
import static com.intellij.openapi.project.ProjectTypeService.setProjectType;
import static com.intellij.openapi.ui.Messages.showErrorDialog;
import static com.intellij.openapi.util.io.FileUtil.join;
import static com.intellij.openapi.util.io.FileUtil.writeToFile;
import static com.intellij.openapi.util.io.FileUtilRt.createIfNotExists;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static com.intellij.ui.AppUIUtil.invokeLaterIfProjectAlive;
import static com.intellij.util.ui.UIUtil.invokeAndWaitIfNeeded;
import static org.jetbrains.android.AndroidPlugin.getGuiTestSuiteState;
import static org.jetbrains.android.AndroidPlugin.isGuiTestingMode;

/**
 * Imports an Android-Gradle project without showing the "Import Project" Wizard UI.
 */
public class GradleProjectImporter {
  private static final Logger LOG = Logger.getInstance(GradleProjectImporter.class);

  // When this system property is set, the sync operation always tries to use the cached project data unless any gradle files are modified.
  private static final boolean SYNC_WITH_CACHED_MODEL_ONLY =
    SystemProperties.getBooleanProperty("studio.sync.with.cached.model.only", false);

  private final ImporterDelegate myDelegate;

  /**
   * Flag used by unit tests to selectively disable code which requires an open project or UI updates; this is used
   * by unit tests that do not run all of IntelliJ (e.g. do not extend the IdeaTestCase base)
   */
  public static boolean ourSkipSetupFromTest;

  @NotNull
  public static GradleProjectImporter getInstance() {
    return ServiceManager.getService(GradleProjectImporter.class);
  }

  public GradleProjectImporter() {
    myDelegate = new ImporterDelegate();
  }

  @VisibleForTesting
  GradleProjectImporter(ImporterDelegate delegate) {
    myDelegate = delegate;
  }

  /**
   * Imports the given Gradle project.
   *
   * @param selectedFile the selected build.gradle or the project's root directory.
   */
  public void importProject(@NotNull VirtualFile selectedFile) {
    VirtualFile projectDir = selectedFile.isDirectory() ? selectedFile : selectedFile.getParent();
    File projectDirPath = virtualToIoFile(projectDir);

    // Sync Android SDKs paths *before* importing project. Studio will freeze if the project has a local.properties file pointing to a SDK
    // path that does not exist. The cause is that having 2 dialogs: one modal (the "Project Import" one) and another from
    // Messages.showErrorDialog (indicating the Android SDK path does not exist) produce a deadlock.
    try {
      LocalProperties localProperties = new LocalProperties(projectDirPath);
      if (isAndroidStudio()) {
        syncIdeAndProjectAndroidSdks(localProperties);
      }
    }
    catch (IOException e) {
      LOG.info("Failed to sync SDKs", e);
      showErrorDialog(e.getMessage(), "Project Import");
      return;
    }

    createProjectFileForGradleProject(selectedFile, null);
  }

  /**
   * Creates IntelliJ project file in the root of the project directory.
   *
   * @param selectedFile build.gradle in the module folder.
   * @param project      existing parent project or {@code null} if a new one should be created.
   */
  private void createProjectFileForGradleProject(@NotNull VirtualFile selectedFile, @Nullable Project project) {
    VirtualFile projectDir = selectedFile.isDirectory() ? selectedFile : selectedFile.getParent();
    File projectDirPath = virtualToIoFile(projectDir);
    try {
      importProject(projectDir.getName(), projectDirPath, true, new NewProjectImportGradleSyncListener() {
        @Override
        public void syncSucceeded(@NotNull Project project) {
          activateProjectView(project);
        }
      }, project, null);
    }
    catch (Exception e) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        throw new RuntimeException(e);
      }
      showErrorDialog(e.getMessage(), "Project Import");
      LOG.error(e);
    }
  }

  /**
   * Requests a project sync with Gradle. If the project import is successful,
   * {@link GradleProjectBuilder#generateSourcesOnly(boolean)} will be invoked at the end.
   *
   * @param project  the given project. This method does nothing if the project is not an Android-Gradle project.
   * @param listener called after the project has been imported.
   */
  public void requestProjectSync(@NotNull Project project, @Nullable GradleSyncListener listener) {
    requestProjectSync(project, true, listener);
  }

  /**
   * Requests a project sync with Gradle.
   *
   * @param project                  the given project. This method does nothing if the project is not an Android-Gradle project.
   * @param generateSourcesOnSuccess indicates whether the IDE should invoke Gradle to generate Java sources after a successful project
   *                                 import.
   * @param listener                 called after the project has been imported.
   */
  public void requestProjectSync(@NotNull Project project,
                                 boolean generateSourcesOnSuccess,
                                 @Nullable GradleSyncListener listener) {
    requestProjectSync(project, false, generateSourcesOnSuccess, false, listener);
  }

  /**
   * Requests a project sync with Gradle.
   *
   * @param project                  the given project. This method does nothing if the project is not an Android-Gradle project.
   * @param useCachedProjectData     indicates whether the IDE should try to use the cached data or invoke Gradle to get the project data.
   *                                 This is just a suggestion and IDE can still invoke Gradle when the cached data is not available or
   *                                 no longer valid.
   * @param generateSourcesOnSuccess indicates whether the IDE should invoke Gradle to generate Java sources after a successful project
   *                                 import. This applies only when the project data is obtained by Gradle invocation and sources are never
   *                                 generated when the cached project data is used.
   * @param cleanProject             indicates whether the project should be cleaned before generating sources. This value is ignored if
   *                                 {@code generateSourcesOnSuccess} is {@code false}.
   * @param listener                 called after the project has been imported.
   */
  public void requestProjectSync(@NotNull Project project,
                                 boolean useCachedProjectData,
                                 boolean generateSourcesOnSuccess,
                                 boolean cleanProject,
                                 @Nullable GradleSyncListener listener) {
    if (GradleSyncState.getInstance(project).isSyncInProgress()) {
      return;
    }
    Runnable syncRequest =
      createSyncRequest(project, IN_BACKGROUND_ASYNC, generateSourcesOnSuccess, cleanProject, useCachedProjectData, listener);
    invokeLaterIfProjectAlive(project, syncRequest);
  }

  public void syncProjectSynchronously(@NotNull Project project,
                                       boolean generateSourcesOnSuccess,
                                       @Nullable GradleSyncListener listener) {
    if (GradleSyncState.getInstance(project).isSyncInProgress()) {
      return;
    }
    Runnable syncRequest = createSyncRequest(project, MODAL_SYNC, generateSourcesOnSuccess, false, false, listener);
    invokeAndWaitIfNeeded(syncRequest);
  }

  @NotNull
  private Runnable createSyncRequest(@NotNull Project project,
                                     @NotNull ProgressExecutionMode executionMode,
                                     boolean generateSourcesOnSuccess,
                                     boolean cleanProject,
                                     boolean useCachedProjectData,
                                     @Nullable GradleSyncListener listener) {
    return () -> {
      if (isBuildInProgress(project)) {
        setSyncRequestedDuringBuild(project, true);
        return;
      }
      try {
        ImportOptions options = new ImportOptions(generateSourcesOnSuccess, cleanProject, false, useCachedProjectData);
        doRequestSync(project, executionMode, options, listener);
      }
      catch (ConfigurationException e) {
        showErrorDialog(project, e.getMessage(), e.getTitle());
      }
    };
  }

  private static boolean isBuildInProgress(@NotNull Project project) {
    IdeFrame frame = ((WindowManagerEx)WindowManager.getInstance()).findFrameFor(project);
    StatusBarEx statusBar = frame == null ? null : (StatusBarEx)frame.getStatusBar();
    if (statusBar == null) {
      return false;
    }
    for (Pair<TaskInfo, ProgressIndicator> backgroundProcess : statusBar.getBackgroundProcesses()) {
      TaskInfo task = backgroundProcess.getFirst();
      if (task instanceof GradleTasksExecutor) {
        ProgressIndicator second = backgroundProcess.getSecond();
        if (second.isRunning()) {
          return true;
        }
      }
    }
    return false;
  }

  private void doRequestSync(@NotNull Project project,
                             @NotNull ProgressExecutionMode progressExecutionMode,
                             @NotNull ImportOptions options,
                             @Nullable GradleSyncListener listener) throws ConfigurationException {
    if (requiresAndroidModel(project) || hasTopLevelGradleBuildFile(project)) {
      FileDocumentManager.getInstance().saveAllDocuments();
      setUpGradleSettings(project);
      resetProject(project);
      setGradleVersionUsed(project, null);
      doImport(project, progressExecutionMode, options, false /* existing project */, listener);
    }
    else {
      Runnable notificationTask = () -> {
        String msg = String.format("The project '%s' is not a Gradle-based project", project.getName());
        AndroidGradleNotification.getInstance(project).showBalloon("Project Sync", msg, ERROR, new OpenMigrationToGradleUrlHyperlink());

        if (listener != null) {
          listener.syncFailed(project, msg);
        }
      };
      Application application = ApplicationManager.getApplication();
      if (application.isDispatchThread()) {
        notificationTask.run();
      }
      else {
        application.invokeLater(notificationTask);
      }
    }
  }

  private static boolean hasTopLevelGradleBuildFile(@NotNull Project project) {
    VirtualFile baseDir = project.getBaseDir();
    VirtualFile gradleBuildFile = baseDir.findChild(FN_BUILD_GRADLE);
    return gradleBuildFile != null && gradleBuildFile.exists() && !gradleBuildFile.isDirectory();
  }

  // See issue: https://code.google.com/p/android/issues/detail?id=64508
  private static void resetProject(@NotNull Project project) {
    executeProjectChanges(project, () -> {
      removeLibrariesAndStoreAttachments(project);

      // Remove all AndroidProjects from module. Otherwise, if re-import/sync fails, editors will not show the proper notification of the
      // failure.
      ModuleManager moduleManager = ModuleManager.getInstance(project);
      for (Module module : moduleManager.getModules()) {
        AndroidFacet facet = AndroidFacet.getInstance(module);
        if (facet != null) {
          facet.setAndroidModel(null);
        }
      }
    });
  }

  /**
   * Imports and opens an Android project that has been created with the "New Project" wizard. This method does not perform any project
   * validation before importing the project (assuming that the wizard properly created the new project.)
   *
   * @param projectName          name of the project.
   * @param projectRootDirPath   the path of the project's root directory.
   * @param listener             called after the project has been imported.
   * @param project              the given project. This method does nothing if the project is not an Android-Gradle project.
   * @param initialLanguageLevel when creating a new project, sets the language level to the given version early on (this is because you
   *                             cannot set a language level later on in the process without telling the user that the language level
   *                             has changed and to re-open the project)
   * @throws IOException            if any file I/O operation fails (e.g. creating the '.idea' directory.)
   * @throws ConfigurationException if any required configuration option is missing (e.g. Gradle home directory path.)
   */
  public void importNewlyCreatedProject(@NotNull String projectName,
                                        @NotNull File projectRootDirPath,
                                        @Nullable GradleSyncListener listener,
                                        @Nullable Project project,
                                        @Nullable LanguageLevel initialLanguageLevel) throws IOException, ConfigurationException {
    doImport(projectName, projectRootDirPath, new ImportOptions(true, false, false, false), listener, project, initialLanguageLevel);
  }

  /**
   * Imports and opens an Android project.
   *
   * @param projectName              name of the project.
   * @param projectRootDirPath       path of the projects' root directory.
   * @param generateSourcesOnSuccess whether to generate sources after sync.
   * @param listener                 called after the project has been imported.
   * @param project                  the given project. This method does nothing if the project is not an Android-Gradle project.
   * @param initialLanguageLevel     when creating a new project, sets the language level to the given version early on (this is because you
   *                                 cannot set a language level later on in the process without telling the user that the language level
   *                                 has changed and to re-open the project)
   * @throws IOException            if any file I/O operation fails (e.g. creating the '.idea' directory.)
   * @throws ConfigurationException if any required configuration option is missing (e.g. Gradle home directory path.)
   */
  public void importProject(@NotNull String projectName,
                            @NotNull File projectRootDirPath,
                            boolean generateSourcesOnSuccess,
                            @Nullable GradleSyncListener listener,
                            @Nullable Project project,
                            @Nullable LanguageLevel initialLanguageLevel) throws IOException, ConfigurationException {
    ImportOptions options = new ImportOptions(generateSourcesOnSuccess, false, true, false);
    doImport(projectName, projectRootDirPath, options, listener, project, initialLanguageLevel);
  }

  private void doImport(@NotNull String projectName,
                        @NotNull File projectRootDirPath,
                        @NotNull ImportOptions options,
                        @Nullable GradleSyncListener listener,
                        @Nullable Project project,
                        @Nullable LanguageLevel initialLanguageLevel) throws IOException, ConfigurationException {
    createTopLevelBuildFileIfNotExisting(projectRootDirPath);
    createIdeaProjectDir(projectRootDirPath);

    Project newProject = project == null ? createProject(projectName, projectRootDirPath.getPath()) : project;
    if (project == null) {
      GradleSettings settings = GradleSettings.getInstance(newProject);
      settings.setGradleVmOptions("");
    }
    setUpProject(newProject, initialLanguageLevel);

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      newProject.save();
    }

    doImport(newProject, MODAL_SYNC, options, true /* new project */,  /* synchronous import */  listener);
  }

  private static void createTopLevelBuildFileIfNotExisting(@NotNull File projectRootDirPath) throws IOException {
    File projectFile = getGradleBuildFilePath(projectRootDirPath);
    if (projectFile.isFile()) {
      return;
    }
    createIfNotExists(projectFile);
    String contents = "// Top-level build file where you can add configuration options common to all sub-projects/modules." +
                      SystemProperties.getLineSeparator();
    writeToFile(projectFile, contents);
  }

  private static void setUpProject(@NotNull Project newProject, @Nullable LanguageLevel initialLanguageLevel) {
    CommandProcessor.getInstance().executeCommand(newProject, () -> ApplicationManager.getApplication().runWriteAction(() -> {
      if (initialLanguageLevel != null) {
        LanguageLevelProjectExtension extension = LanguageLevelProjectExtension.getInstance(newProject);
        if (extension != null) {
          extension.setLanguageLevel(initialLanguageLevel);
        }
      }

      // In practice, it really does not matter where the compiler output folder is. Gradle handles that. This is done just to please
      // IDEA.
      File compilerOutputDirPath = new File(getBaseDirPath(newProject), join(BUILD_DIR_DEFAULT_NAME, "classes"));
      String compilerOutputDirUrl = pathToIdeaUrl(compilerOutputDirPath);
      CompilerProjectExtension compilerProjectExt = CompilerProjectExtension.getInstance(newProject);
      assert compilerProjectExt != null;
      compilerProjectExt.setCompilerOutputUrl(compilerOutputDirUrl);
      setUpGradleSettings(newProject);
      // This allows to customize UI when android project is opened inside IDEA with android plugin.
      setProjectType(newProject, AndroidModuleBuilder.ANDROID_PROJECT_TYPE);
    }), null, null);
  }

  private static void setUpGradleSettings(@NotNull Project project) {
    GradleProjectSettings projectSettings = getGradleProjectSettings(project);
    if (projectSettings == null) {
      projectSettings = new GradleProjectSettings();
    }
    setUpGradleProjectSettings(project, projectSettings);
    GradleSettings gradleSettings = GradleSettings.getInstance(project);
    gradleSettings.setLinkedProjectsSettings(ImmutableList.of(projectSettings));
  }

  private static void setUpGradleProjectSettings(@NotNull Project project, @NotNull GradleProjectSettings settings) {
    settings.setUseAutoImport(false);

    // Workaround to make integration (non-UI) tests pass.
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      Sdk jdk = IdeSdks.getJdk();
      if (jdk != null) {
        settings.setGradleJvm(jdk.getName());
      }
    }

    String basePath = project.getBasePath();
    if (basePath != null) {
      settings.setExternalProjectPath(basePath);
    }
  }

  private void doImport(@NotNull Project project,
                        @NotNull ProgressExecutionMode progressExecutionMode,
                        @NotNull ImportOptions options,
                        boolean newProject,
                        @Nullable GradleSyncListener listener) throws ConfigurationException {
    invokeAndWaitIfNeeded(() -> ensureToolWindowContentInitialized(project, GRADLE_SYSTEM_ID));
    if (isAndroidStudio()) {
      // See https://code.google.com/p/android/issues/detail?id=169743
      clearStoredGradleJvmArgs(project);
    }

    PreSyncChecks.PreSyncCheckResult preSyncCheckResult = PreSyncChecks.canSync(project);
    if (!preSyncCheckResult.isSuccess()) {
      // User should have already warned that something is not right and sync cannot continue.
      GradleSyncState syncState = GradleSyncState.getInstance(project);
      if (syncState.syncStarted(true)) {
        createTopLevelProjectAndOpen(project);
        String cause = nullToEmpty(preSyncCheckResult.getFailureCause());
        syncState.syncFailed(cause);
        if (listener != null) {
          listener.syncFailed(project, cause);
        }
      }
      return;
    }

    if (isAndroidStudio() && isDirectGradleInvocationEnabled(project)) {
      // We cannot do the same when using JPS. We don't have access to the contents of the Message view used by JPS.
      // For now, we can only improve the user experience in Android Studio.
      GradleInvoker.getInstance(project).clearConsoleAndBuildMessages();
    }

    // Prevent IDEA from syncing with Gradle. We want to have full control of syncing.
    project.putUserData(ExternalSystemDataKeys.NEWLY_IMPORTED_PROJECT, true);

    setHasSyncErrors(project, false);
    setHasWrongJdk(project, false);

    if (forceSyncWithCachedModel() || options.useCachedProjectData) {
      GradleProjectSyncData syncData = GradleProjectSyncData.getInstance((project));
      if (syncData != null && syncData.canUseCachedProjectData()) {
        DataNode<ProjectData> cache = getCachedProjectData(project);
        if (cache != null && !isCacheMissingModels(cache, project)) {
          PostProjectSetupTasksExecutor executor = PostProjectSetupTasksExecutor.getInstance(project);
          executor.setGenerateSourcesAfterSync(false, false);
          executor.setUsingCachedProjectData(true);
          executor.setLastSyncTimestamp(syncData.getLastGradleSyncTimestamp());

          ProjectSetUpTask setUpTask = new ProjectSetUpTask(project, newProject, options.importingExistingProject, true, listener);
          setUpTask.onSuccess(cache);
          return;
        }
      }
    }

    // We only update UI on sync when re-importing projects. By "updating UI" we mean updating the "Build Variants" tool window and editor
    // notifications.  It is not safe to do this for new projects because the new project has not been opened yet.
    boolean started = GradleSyncState.getInstance(project).syncStarted(!newProject);
    if (!started) {
      return;
    }

    PostProjectSetupTasksExecutor.getInstance(project).setGenerateSourcesAfterSync(options.generateSourcesOnSuccess, options.cleanProject);
    ProjectSetUpTask setUpTask = new ProjectSetUpTask(project, newProject, options.importingExistingProject, false, listener);
    myDelegate.importProject(project, setUpTask, progressExecutionMode);
  }

  private static boolean forceSyncWithCachedModel() {
    if (SYNC_WITH_CACHED_MODEL_ONLY) {
      return true;
    }
    if (isGuiTestingMode()) {
      GuiTestSuiteState state = getGuiTestSuiteState();
      return state.syncWithCachedModelOnly();
    }
    return false;
  }

  @VisibleForTesting
  static boolean isCacheMissingModels(@NotNull DataNode<ProjectData> cache, @NotNull Project project) {
    Collection<DataNode<ModuleData>> moduleDataNodes = findAll(cache, MODULE);
    if (!moduleDataNodes.isEmpty()) {
      Map<String, DataNode<ModuleData>> moduleDataNodesByName = indexByModuleName(moduleDataNodes);

      ModuleManager moduleManager = ModuleManager.getInstance(project);
      for (Module module : moduleManager.getModules()) {
        DataNode<ModuleData> moduleDataNode = moduleDataNodesByName.get(module.getName());
        if (moduleDataNode == null) {
          // When a Gradle facet is present, there should be a cache node for the module.
          AndroidGradleFacet gradleFacet = AndroidGradleFacet.getInstance(module);
          if (gradleFacet != null) {
            return true;
          }
        }
        else if (isCacheMissingModels(moduleDataNode, module)) {
          return true;
        }
      }
      return false;
    }
    return true;
  }

  @NotNull
  private static Map<String, DataNode<ModuleData>> indexByModuleName(@NotNull Collection<DataNode<ModuleData>> moduleDataNodes) {
    Map<String, DataNode<ModuleData>> mapping = Maps.newHashMap();
    for (DataNode<ModuleData> moduleDataNode : moduleDataNodes) {
      ModuleData data = moduleDataNode.getData();
      mapping.put(data.getExternalName(), moduleDataNode);
    }
    return mapping;
  }

  private static boolean isCacheMissingModels(@NotNull DataNode<ModuleData> cache, @NotNull Module module) {
    AndroidGradleFacet gradleFacet = AndroidGradleFacet.getInstance(module);
    if (gradleFacet != null) {
      DataNode<GradleModel> gradleDataNode = find(cache, GRADLE_MODEL);
      if (gradleDataNode == null) {
        return true;
      }

      AndroidFacet androidFacet = AndroidFacet.getInstance(module);
      if (androidFacet != null) {
        DataNode<AndroidGradleModel> androidDataNode = find(cache, ANDROID_MODEL);
        if (androidDataNode == null) {
          return true;
        }
      }
      else {
        JavaGradleFacet javaFacet = JavaGradleFacet.getInstance(module);
        if (javaFacet != null) {
          DataNode<JavaProject> javaProjectDataNode = find(cache, JAVA_PROJECT);
          if (javaProjectDataNode == null) {
            return true;
          }
        }
      }
    }
    NativeAndroidGradleFacet nativeAndroidFacet = NativeAndroidGradleFacet.getInstance(module);
    if (nativeAndroidFacet != null) {
      DataNode<NativeAndroidGradleModel> nativeAndroidGradleDataNode = find(cache, NATIVE_ANDROID_MODEL);
      if (nativeAndroidGradleDataNode == null) {
        return true;
      }
    }
    return false;
  }

  // Makes it possible to mock invocations to the Gradle Tooling API.
  static class ImporterDelegate {
    void importProject(@NotNull Project project,
                       @NotNull ExternalProjectRefreshCallback callback,
                       @NotNull ProgressExecutionMode progressExecutionMode) throws ConfigurationException {
      try {
        String externalProjectPath = getBaseDirPath(project).getPath();
        refreshProject(project, GRADLE_SYSTEM_ID, externalProjectPath, callback, false /* resolve dependencies */,
                       progressExecutionMode, true /* always report import errors */);
      }
      catch (RuntimeException e) {
        String externalSystemName = GRADLE_SYSTEM_ID.getReadableName();
        throw new ConfigurationException(e.getMessage(), ExternalSystemBundle.message("error.cannot.parse.project", externalSystemName));
      }
    }
  }

  private static class ImportOptions {
    final boolean generateSourcesOnSuccess;
    final boolean cleanProject;
    final boolean importingExistingProject;
    final boolean useCachedProjectData;

    ImportOptions(boolean generateSourcesOnSuccess,
                  boolean cleanProject,
                  boolean importingExistingProject,
                  boolean useCachedProjectData) {
      this.generateSourcesOnSuccess = generateSourcesOnSuccess;
      this.cleanProject = cleanProject;
      this.importingExistingProject = importingExistingProject;
      this.useCachedProjectData = useCachedProjectData;
    }
  }
}
