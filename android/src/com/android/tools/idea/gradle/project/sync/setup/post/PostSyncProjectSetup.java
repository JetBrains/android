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
package com.android.tools.idea.gradle.project.sync.setup.post;

import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.project.build.BuildStatus.SKIPPED;
import static com.android.tools.idea.gradle.project.sync.ModuleSetupContext.removeSyncContextDataFrom;
import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.android.tools.idea.gradle.variant.conflict.ConflictSet.findConflicts;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_PROJECT_CACHED_SETUP_FAILED;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.executeProjectChangeAction;
import static com.intellij.openapi.util.io.FileUtil.toCanonicalPath;
import static java.lang.System.currentTimeMillis;

import com.android.annotations.concurrency.Slow;
import com.android.builder.model.SyncIssue;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.ProjectBuildFileChecksums;
import com.android.tools.idea.gradle.project.ProjectStructure;
import com.android.tools.idea.gradle.project.ProjectStructure.AndroidPluginVersionsInProject;
import com.android.tools.idea.gradle.project.RunConfigurationChecker;
import com.android.tools.idea.gradle.project.SupportedModuleChecker;
import com.android.tools.idea.gradle.project.build.GradleBuildState;
import com.android.tools.idea.gradle.project.build.GradleProjectBuilder;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.compatibility.VersionCompatibilityChecker;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.android.tools.idea.gradle.project.sync.setup.module.common.DependencySetupIssues;
import com.android.tools.idea.gradle.project.sync.setup.post.project.DisposedModules;
import com.android.tools.idea.gradle.project.sync.setup.post.upgrade.RecommendedPluginVersionUpgrade;
import com.android.tools.idea.gradle.run.MakeBeforeRunTaskProvider;
import com.android.tools.idea.gradle.variant.conflict.Conflict;
import com.android.tools.idea.gradle.variant.conflict.ConflictSet;
import com.android.tools.idea.gradle.variant.profiles.ProjectProfileSelectionDialog;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.testartifacts.junit.AndroidJUnitConfiguration;
import com.android.tools.idea.testartifacts.junit.AndroidJUnitConfigurationType;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.build.DefaultBuildDescriptor;
import com.intellij.build.SyncViewManager;
import com.intellij.build.events.EventResult;
import com.intellij.build.events.Failure;
import com.intellij.build.events.impl.FailureResultImpl;
import com.intellij.build.events.impl.FinishBuildEventImpl;
import com.intellij.build.events.impl.StartBuildEventImpl;
import com.intellij.build.events.impl.SuccessResultImpl;
import com.intellij.compiler.options.CompileStepBeforeRun;
import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.BeforeRunTaskProvider;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.intellij.openapi.externalSystem.util.DisposeAwareProjectChange;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.Key;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.SystemProperties;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.serialization.PathMacroUtil;

public class PostSyncProjectSetup {
  @NotNull private final Project myProject;
  @NotNull private final IdeInfo myIdeInfo;
  @NotNull private final ProjectStructure myProjectStructure;
  @NotNull private final GradleProjectInfo myGradleProjectInfo;
  @NotNull private final GradleSyncInvoker mySyncInvoker;
  @NotNull private final GradleSyncState mySyncState;
  @NotNull private final DependencySetupIssues myDependencySetupIssues;
  @NotNull private final ProjectSetup myProjectSetup;
  @NotNull private final ModuleSetup myModuleSetup;
  @NotNull private final PluginVersionUpgrade myPluginVersionUpgrade;
  @NotNull private final VersionCompatibilityChecker myVersionCompatibilityChecker;
  @NotNull private final GradleProjectBuilder myProjectBuilder;
  @NotNull private final RunManagerEx myRunManager;

  @NotNull
  public static PostSyncProjectSetup getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, PostSyncProjectSetup.class);
  }

  @SuppressWarnings("unused") // Instantiated by IDEA
  public PostSyncProjectSetup(@NotNull Project project,
                              @NotNull IdeInfo ideInfo,
                              @NotNull ProjectStructure projectStructure,
                              @NotNull GradleProjectInfo gradleProjectInfo,
                              @NotNull GradleSyncInvoker syncInvoker,
                              @NotNull GradleSyncState syncState,
                              @NotNull GradleSyncMessages syncMessages,
                              @NotNull DependencySetupIssues dependencySetupIssues,
                              @NotNull PluginVersionUpgrade pluginVersionUpgrade,
                              @NotNull VersionCompatibilityChecker versionCompatibilityChecker,
                              @NotNull GradleProjectBuilder projectBuilder) {
    this(project, ideInfo, projectStructure, gradleProjectInfo, syncInvoker, syncState, dependencySetupIssues, new ProjectSetup(project),
         new ModuleSetup(project), pluginVersionUpgrade, versionCompatibilityChecker, projectBuilder, RunManagerEx.getInstanceEx(project));
  }

  @VisibleForTesting
  PostSyncProjectSetup(@NotNull Project project,
                       @NotNull IdeInfo ideInfo,
                       @NotNull ProjectStructure projectStructure,
                       @NotNull GradleProjectInfo gradleProjectInfo,
                       @NotNull GradleSyncInvoker syncInvoker,
                       @NotNull GradleSyncState syncState,
                       @NotNull DependencySetupIssues dependencySetupIssues,
                       @NotNull ProjectSetup projectSetup,
                       @NotNull ModuleSetup moduleSetup,
                       @NotNull PluginVersionUpgrade pluginVersionUpgrade,
                       @NotNull VersionCompatibilityChecker versionCompatibilityChecker,
                       @NotNull GradleProjectBuilder projectBuilder,
                       @NotNull RunManagerEx runManager) {
    myProject = project;
    myIdeInfo = ideInfo;
    myProjectStructure = projectStructure;
    myGradleProjectInfo = gradleProjectInfo;
    mySyncInvoker = syncInvoker;
    mySyncState = syncState;
    myDependencySetupIssues = dependencySetupIssues;
    myProjectSetup = projectSetup;
    myModuleSetup = moduleSetup;
    myPluginVersionUpgrade = pluginVersionUpgrade;
    myVersionCompatibilityChecker = versionCompatibilityChecker;
    myProjectBuilder = projectBuilder;
    myRunManager = runManager;
  }

  /**
   * Invoked after a project has been synced with Gradle.
   */
  @Slow
  public void setUpProject(@NotNull Request request,
                           @Nullable ExternalSystemTaskId taskId,
                           @Nullable GradleSyncListener syncListener) {
    try {
      removeSyncContextDataFrom(myProject);

      myGradleProjectInfo.setNewProject(false);
      myGradleProjectInfo.setImportedProject(false);
      boolean syncFailed = mySyncState.lastSyncFailed();

      if (syncFailed && request.usingCachedGradleModels) {
        onCachedModelsSetupFailure(taskId, request);
        return;
      }

      myDependencySetupIssues.reportIssues();
      myVersionCompatibilityChecker.checkAndReportComponentIncompatibilities(myProject);

      if (mySyncState.lastSyncFailed()) {
        failTestsIfSyncIssuesPresent();

        myProjectSetup.setUpProject(true /* sync failed */);
        // Notify "sync end" event first, to register the timestamp. Otherwise the cache (ProjectBuildFileChecksums) will store the date of the
        // previous sync, and not the one from the sync that just ended.
        mySyncState.syncFailed("", null, syncListener);
        finishFailedSync(taskId, myProject);
        return;
      }

      // Needed internally for development of Android support lib.
      boolean skipAgpUpgrade = SystemProperties.getBooleanProperty("studio.skip.agp.upgrade", false);

      if (!skipAgpUpgrade && !request.skipAndroidPluginUpgrade) {
        if (StudioFlags.BALLOON_UPGRADE_NOTIFICATION.get()) {
          if (myPluginVersionUpgrade.isForcedUpgradable()) {
            // Do force upgrade anyway.
            if (myPluginVersionUpgrade.performForcedUpgrade()) {
              finishSuccessfulSync(taskId);
              return;
            }
          }
          else {
            RecommendedPluginVersionUpgrade.checkUpgrade(myProject);
          }
        }
        else {
          // TODO(b/127454467): remove after StudioFlags.BALLOON_UPGRADE_NOTIFICATION is removed.
          if (myPluginVersionUpgrade.checkAndPerformUpgrade()) {
            // Plugin version was upgraded and a sync was triggered.
            finishSuccessfulSync(taskId);
            return;
          }
        }
      }

      if (StudioFlags.RECOMMENDATION_ENABLED.get()) {
        MemorySettingsPostSyncChecker.checkSettings(myProject, new TimeBasedMemorySettingsCheckerReminder());
      }

      new ProjectStructureUsageTracker(myProject).trackProjectStructure();

      DisposedModules.getInstance(myProject).deleteImlFilesForDisposedModules();
      SupportedModuleChecker.getInstance().checkForSupportedModules(myProject);

      findAndShowVariantConflicts();
      myProjectSetup.setUpProject(false /* sync successful */);

      modifyJUnitRunConfigurations();
      RunConfigurationChecker.getInstance(myProject).ensureRunConfigsInvokeBuild();

      AndroidPluginVersionsInProject agpVersions = myProjectStructure.getAndroidPluginVersions();
      myProjectStructure.analyzeProjectStructure();
      boolean cleanProjectAfterSync = myProjectStructure.getAndroidPluginVersions().haveVersionsChanged(agpVersions);

      attemptToGenerateSources(request, cleanProjectAfterSync);
      updateJavaLanguageLevel();
      notifySyncFinished(request);

      myModuleSetup.setUpModules(null);

      finishSuccessfulSync(taskId);
    }
    catch (Throwable t) {
      mySyncState.syncFailed("setup project failed: " + t.getMessage(), t, syncListener);
      finishFailedSync(taskId, myProject);
    }
  }

  @VisibleForTesting
  void updateJavaLanguageLevel() {
    executeProjectChangeAction(true, new DisposeAwareProjectChange(myProject) {
      @Override
      public void execute() {
        if (myProject.isOpen()) {
          LanguageLevel langLevel = getMaxJavaLanguageLevel(myProject);
          if (langLevel != null) {
            LanguageLevelProjectExtension ext = LanguageLevelProjectExtension.getInstance(myProject);
            if (langLevel != ext.getLanguageLevel()) {
              ext.setLanguageLevel(langLevel);
            }
          }
        }
      }
    });
  }

  @VisibleForTesting
  @Nullable
  static LanguageLevel getMaxJavaLanguageLevel(@NotNull Project project) {
    LanguageLevel maxLangLevel = null;

    Module[] modules = ModuleManager.getInstance(project).getModules();
    for (Module module : modules) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null) {
        AndroidModuleModel androidModel = AndroidModuleModel.get(facet);
        if (androidModel != null) {
          LanguageLevel langLevel = androidModel.getJavaLanguageLevel();
          if (langLevel != null && (maxLangLevel == null || maxLangLevel.compareTo(langLevel) < 0)) {
            maxLangLevel = langLevel;
          }
        }
      }
    }
    return maxLangLevel;
  }

  private void finishSuccessfulSync(@Nullable ExternalSystemTaskId taskId) {
    if (taskId == null) {
      return;
    }

    String message = "synced successfully";
    // Even if the sync was successful it may have warnings or non error messages, need to put in the correct kind of result
    EventResult result;
    GradleSyncMessages messages = GradleSyncMessages.getInstance(myProject);
    List<Failure> failures = messages.showEvents(taskId);

    if (failures.isEmpty()) {
      result = new SuccessResultImpl();
    }
    else {
      result = new FailureResultImpl(failures);
    }

    FinishBuildEventImpl finishBuildEvent = new FinishBuildEventImpl(taskId, null, currentTimeMillis(), message, result);
    ApplicationManager.getApplication().invokeLater(() -> {
      if (!myProject.isDisposed()) {
        ServiceManager.getService(myProject, SyncViewManager.class).onEvent(taskId, finishBuildEvent);
      }
    });
  }

  public static void finishFailedSync(@Nullable ExternalSystemTaskId taskId, @NotNull Project project) {
    if (taskId != null) {
      String message = "sync failed";
      GradleSyncMessages messages = GradleSyncMessages.getInstance(project);
      List<Failure> failures = messages.showEvents(taskId);
      FailureResultImpl failureResult = new FailureResultImpl(failures);
      FinishBuildEventImpl finishBuildEvent = new FinishBuildEventImpl(taskId, null, currentTimeMillis(), message, failureResult);
      ServiceManager.getService(project, SyncViewManager.class).onEvent(taskId, finishBuildEvent);
    }
  }

  public void onCachedModelsSetupFailure(@Nullable ExternalSystemTaskId taskId, @NotNull Request request) {
    finishFailedSync(taskId, myProject);
    // Sync with cached model failed (e.g. when Studio has a newer embedded builder-model interfaces and the cache is using an older
    // version of such interfaces.
    long syncTimestamp = request.lastSyncTimestamp;
    if (syncTimestamp < 0) {
      syncTimestamp = currentTimeMillis();
    }
    mySyncState.syncSkipped(syncTimestamp, null);
    // TODO add a new trigger for this?
    mySyncInvoker.requestProjectSync(myProject, TRIGGER_PROJECT_CACHED_SETUP_FAILED);
  }

  private void failTestsIfSyncIssuesPresent() {
    if (ApplicationManager.getApplication().isUnitTestMode() && GradleSyncMessages.getInstance(myProject).getErrorCount() > 0) {
      StringBuilder buffer = new StringBuilder();
      buffer.append("Sync issues found!").append('\n');
      myGradleProjectInfo.forEachAndroidModule(facet -> {
        AndroidModel androidModel = facet.getConfiguration().getModel();
        if (androidModel instanceof AndroidModuleModel) {
          Collection<SyncIssue> issues = ((AndroidModuleModel)androidModel).getSyncIssues();
          if (issues != null && !issues.isEmpty()) {
            buffer.append("Module '").append(facet.getModule().getName()).append("':").append('\n');
            for (SyncIssue issue : issues) {
              buffer.append(issue.getMessage()).append('\n');
            }
          }
        }
      });
      throw new IllegalStateException(buffer.toString());
    }
  }

  private void notifySyncFinished(@NotNull Request request) {
    // Notify "sync end" event first, to register the timestamp. Otherwise the cache (ProjectBuildFileChecksums) will store the date of the
    // previous sync, and not the one from the sync that just ended.
    if (request.usingCachedGradleModels) {
      long timestamp = currentTimeMillis();
      mySyncState.syncSkipped(timestamp, null);
      GradleBuildState.getInstance(myProject).buildFinished(SKIPPED);
    }
    else {
      if (mySyncState.lastSyncFailed()) {
        mySyncState.syncFailed("", null, null);
      }
      else {
        mySyncState.syncSucceeded();
      }
      ProjectBuildFileChecksums.saveToDisk(myProject);
    }
  }

  private void findAndShowVariantConflicts() {
    ConflictSet conflicts = findConflicts(myProject);

    List<Conflict> structureConflicts = conflicts.getStructureConflicts();
    if (!structureConflicts.isEmpty() && SystemProperties.getBooleanProperty("enable.project.profiles", false)) {
      ProjectProfileSelectionDialog dialog = new ProjectProfileSelectionDialog(myProject, structureConflicts);
      dialog.show();
    }

    conflicts.showSelectionConflicts();
  }

  private void modifyJUnitRunConfigurations() {
    ConfigurationType junitConfigurationType = AndroidJUnitConfigurationType.getInstance();
    BeforeRunTaskProvider<BeforeRunTask>[] taskProviders = BeforeRunTaskProvider.EXTENSION_POINT_NAME.getExtensions(myProject);
    RunManagerEx runManager = RunManagerEx.getInstanceEx(myProject);

    // For Android Studio, use "Gradle-Aware Make" to run JUnit tests.
    // For IDEA, use regular "Make".
    Key<? extends BeforeRunTask> makeTaskId = myIdeInfo.isAndroidStudio() ? MakeBeforeRunTaskProvider.ID : CompileStepBeforeRun.ID;
    BeforeRunTaskProvider targetProvider = null;
    for (BeforeRunTaskProvider<? extends BeforeRunTask> provider : taskProviders) {
      if (makeTaskId.equals(provider.getId())) {
        targetProvider = provider;
        break;
      }
    }

    if (targetProvider != null) {
      // Store current before run tasks in each configuration to reset them after modifying the template, since modifying
      Map<RunConfiguration, List<BeforeRunTask>> currentTasks = new HashMap<>();
      for (RunConfiguration runConfiguration : myRunManager.getConfigurationsList(junitConfigurationType)) {
        currentTasks.put(runConfiguration, new ArrayList<>(runManager.getBeforeRunTasks(runConfiguration)));
      }

      // Fix the "JUnit Run Configuration" templates.
      for (ConfigurationFactory configurationFactory : junitConfigurationType.getConfigurationFactories()) {
        RunnerAndConfigurationSettings template = myRunManager.getConfigurationTemplate(configurationFactory);
        AndroidJUnitConfiguration runConfiguration = (AndroidJUnitConfiguration)template.getConfiguration();
        // Set the correct "Make step" in the "JUnit Run Configuration" template.
        setMakeStepInJUnitConfiguration(targetProvider, runConfiguration);
        runConfiguration.setWorkingDirectory("$" + PathMacroUtil.MODULE_DIR_MACRO_NAME + "$");
      }

      // Fix existing JUnit Configurations.
      for (RunConfiguration runConfiguration : myRunManager.getConfigurationsList(junitConfigurationType)) {
        // Keep the previous configurations in existing run configurations
        runManager.setBeforeRunTasks(runConfiguration, currentTasks.get(runConfiguration));
      }
    }
  }

  private void setMakeStepInJUnitConfiguration(@NotNull BeforeRunTaskProvider targetProvider, @NotNull RunConfiguration runConfiguration) {
    // Only "make" steps of beforeRunTasks should be overridden (see http://b.android.com/194704 and http://b.android.com/227280)
    List<BeforeRunTask> newBeforeRunTasks = new LinkedList<>();
    RunManagerEx runManager = RunManagerEx.getInstanceEx(myProject);
    for (BeforeRunTask beforeRunTask : runManager.getBeforeRunTasks(runConfiguration)) {
      if (beforeRunTask.getProviderId().equals(CompileStepBeforeRun.ID)) {
        if (runManager.getBeforeRunTasks(runConfiguration, MakeBeforeRunTaskProvider.ID).isEmpty()) {
          BeforeRunTask task = targetProvider.createTask(runConfiguration);
          if (task != null) {
            task.setEnabled(true);
            newBeforeRunTasks.add(task);
          }
        }
      }
      else {
        newBeforeRunTasks.add(beforeRunTask);
      }
    }
    runManager.setBeforeRunTasks(runConfiguration, newBeforeRunTasks);
  }

  private void attemptToGenerateSources(@NotNull Request request, boolean cleanProjectAfterSync) {
    if (!request.generateSourcesAfterSync) {
      return;
    }
    if (cleanProjectAfterSync) {
      myProjectBuilder.cleanAndGenerateSources();
      return;
    }
    myProjectBuilder.generateSources();
  }

  /**
   * Create a new {@link ExternalSystemTaskId} to be used while doing project setup from cache and adds a StartBuildEvent to build view.
   *
   * @param project
   * @return
   */
  @NotNull
  public static ExternalSystemTaskId createProjectSetupFromCacheTaskWithStartMessage(Project project) {
    // Create taskId
    ExternalSystemTaskId taskId = ExternalSystemTaskId.create(GRADLE_SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, project);

    // Create StartBuildEvent
    String workingDir = toCanonicalPath(getBaseDirPath(project).getPath());
    DefaultBuildDescriptor buildDescriptor = new DefaultBuildDescriptor(taskId, "Project setup", workingDir, currentTimeMillis());
    SyncViewManager syncManager = ServiceManager.getService(project, SyncViewManager.class);
    syncManager.onEvent(taskId, new StartBuildEventImpl(buildDescriptor, "reading from cache..."));
    return taskId;
  }

  public static class Request {
    public boolean usingCachedGradleModels;
    public boolean cleanProjectAfterSync;
    public boolean generateSourcesAfterSync = true;
    public boolean skipAndroidPluginUpgrade;
    public long lastSyncTimestamp = -1L;

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Request request = (Request)o;
      return usingCachedGradleModels == request.usingCachedGradleModels &&
             cleanProjectAfterSync == request.cleanProjectAfterSync &&
             generateSourcesAfterSync == request.generateSourcesAfterSync &&
             lastSyncTimestamp == request.lastSyncTimestamp;
    }

    @Override
    public int hashCode() {
      return Objects.hash(usingCachedGradleModels, cleanProjectAfterSync, generateSourcesAfterSync, lastSyncTimestamp);
    }
  }
}
