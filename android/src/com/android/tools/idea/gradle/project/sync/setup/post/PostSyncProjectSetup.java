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

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.project.AndroidGradleProjectComponent;
import com.android.tools.idea.gradle.project.GradleProjectSyncData;
import com.android.tools.idea.gradle.project.build.GradleProjectBuilder;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.compatibility.VersionCompatibilityChecker;
import com.android.tools.idea.gradle.project.sync.messages.SyncMessages;
import com.android.tools.idea.gradle.project.sync.setup.module.common.DependencySetupErrors;
import com.android.tools.idea.gradle.project.sync.setup.post.project.DisposedModules;
import com.android.tools.idea.gradle.project.sync.validation.common.CommonModuleValidator;
import com.android.tools.idea.gradle.run.MakeBeforeRunTaskProvider;
import com.android.tools.idea.gradle.variant.conflict.Conflict;
import com.android.tools.idea.gradle.variant.conflict.ConflictSet;
import com.android.tools.idea.gradle.variant.profiles.ProjectProfileSelectionDialog;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.testartifacts.junit.AndroidJUnitConfigurationType;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.compiler.options.CompileStepBeforeRun;
import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.BeforeRunTaskProvider;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static com.android.tools.idea.gradle.util.Projects.*;
import static com.android.tools.idea.gradle.variant.conflict.ConflictResolution.solveSelectionConflicts;
import static com.android.tools.idea.gradle.variant.conflict.ConflictSet.findConflicts;

public class PostSyncProjectSetup {
  @NotNull private final Project myProject;
  @NotNull private final IdeInfo myIdeInfo;
  @NotNull private final GradleSyncInvoker mySyncInvoker;
  @NotNull private final GradleSyncState mySyncState;
  @NotNull private final DependencySetupErrors myDependencySetupErrors;
  @NotNull private final ProjectSetup myProjectSetup;
  @NotNull private final ModuleSetup myModuleSetup;
  @NotNull private final PluginVersionUpgrade myPluginVersionUpgrade;
  @NotNull private final VersionCompatibilityChecker myVersionCompatibilityChecker;
  @NotNull private final GradleProjectBuilder myProjectBuilder;
  @NotNull private final CommonModuleValidator.Factory myModuleValidatorFactory;

  @NotNull
  public static PostSyncProjectSetup getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, PostSyncProjectSetup.class);
  }

  @SuppressWarnings("unused") // Instantiated by IDEA
  public PostSyncProjectSetup(@NotNull Project project,
                              @NotNull IdeInfo ideInfo,
                              @NotNull AndroidSdks androidSdks,
                              @NotNull GradleSyncInvoker syncInvoker,
                              @NotNull GradleSyncState syncState,
                              @NotNull SyncMessages syncMessages,
                              @NotNull DependencySetupErrors dependencySetupErrors,
                              @NotNull VersionCompatibilityChecker versionCompatibilityChecker,
                              @NotNull GradleProjectBuilder projectBuilder) {
    this(project, ideInfo, syncInvoker, syncState, dependencySetupErrors, new ProjectSetup(project), new ModuleSetup(project),
         new PluginVersionUpgrade(project), versionCompatibilityChecker, projectBuilder, new CommonModuleValidator.Factory());
  }

  @VisibleForTesting
  PostSyncProjectSetup(@NotNull Project project,
                       @NotNull IdeInfo ideInfo,
                       @NotNull GradleSyncInvoker syncInvoker,
                       @NotNull GradleSyncState syncState,
                       @NotNull DependencySetupErrors dependencySetupErrors,
                       @NotNull ProjectSetup projectSetup,
                       @NotNull ModuleSetup moduleSetup,
                       @NotNull PluginVersionUpgrade pluginVersionUpgrade,
                       @NotNull VersionCompatibilityChecker versionCompatibilityChecker,
                       @NotNull GradleProjectBuilder projectBuilder,
                       @NotNull CommonModuleValidator.Factory moduleValidatorFactory) {
    myProject = project;
    myIdeInfo = ideInfo;
    mySyncInvoker = syncInvoker;
    mySyncState = syncState;
    myDependencySetupErrors = dependencySetupErrors;
    myProjectSetup = projectSetup;
    myModuleSetup = moduleSetup;
    myPluginVersionUpgrade = pluginVersionUpgrade;
    myVersionCompatibilityChecker = versionCompatibilityChecker;
    myProjectBuilder = projectBuilder;
    myModuleValidatorFactory = moduleValidatorFactory;
  }

  /**
   * Invoked after a project has been synced with Gradle.
   */
  public void setUpProject(@NotNull Request request, @Nullable ProgressIndicator progressIndicator) {
    // Force a refresh after a sync.
    // https://code.google.com/p/android/issues/detail?id=229633
    ApplicationManager.getApplication()
      .runWriteAction(() -> ProjectRootManagerEx.getInstanceEx(myProject).makeRootsChange(EmptyRunnable.INSTANCE, false, true));

    boolean syncFailed = mySyncState.lastSyncFailedOrHasIssues();

    if (syncFailed && request.isUsingCachedGradleModels()) {
      onCachedModelsSetupFailure(request);
      return;
    }

    myDependencySetupErrors.reportErrors();
    myVersionCompatibilityChecker.checkAndReportComponentIncompatibilities(myProject);

    CommonModuleValidator moduleValidator = myModuleValidatorFactory.create(myProject);
    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    for (Module module : moduleManager.getModules()) {
      moduleValidator.validate(module);
    }
    moduleValidator.fixAndReportFoundIssues();

    if (syncFailed) {
      myProjectSetup.setUpProject(progressIndicator, true /* sync failed */);
      // Notify "sync end" event first, to register the timestamp. Otherwise the cache (GradleProjectSyncData) will store the date of the
      // previous sync, and not the one from the sync that just ended.
      mySyncState.syncEnded();
      return;
    }

    if (myPluginVersionUpgrade.checkAndPerformUpgrade()) {
      // Plugin version was upgraded and a sync was triggered.
      return;
    }

    new ProjectStructureUsageTracker(myProject).trackProjectStructure();

    DisposedModules.getInstance(myProject).deleteImlFilesForDisposedModules();
    removeAllModuleCompiledArtifacts(myProject);

    findAndShowVariantConflicts();
    myProjectSetup.setUpProject(progressIndicator, false /* sync successful */);

    // For Android Studio, use "Gradle-Aware Make" to run JUnit tests.
    // For IDEA, use regular "Make".
    boolean androidStudio = myIdeInfo.isAndroidStudio();
    String taskName = androidStudio ? MakeBeforeRunTaskProvider.TASK_NAME : ExecutionBundle.message("before.launch.compile.step");
    setMakeStepInJunitRunConfigurations(taskName);

    notifySyncFinished(request);
    attemptToGenerateSources(request);

    TemplateManager.getInstance().refreshDynamicTemplateMenu(myProject);

    myModuleSetup.setUpModules(null);
  }

  private void onCachedModelsSetupFailure(@NotNull Request request) {
    // Sync with cached model failed (e.g. when Studio has a newer embedded builder-model interfaces and the cache is using an older
    // version of such interfaces.
    long syncTimestamp = request.getLastSyncTimestamp();
    if (syncTimestamp < 0) {
      syncTimestamp = System.currentTimeMillis();
    }
    mySyncState.syncSkipped(syncTimestamp);
    mySyncInvoker.requestProjectSyncAndSourceGeneration(myProject, null);
  }

  private void notifySyncFinished(@NotNull Request request) {
    // Notify "sync end" event first, to register the timestamp. Otherwise the cache (GradleProjectSyncData) will store the date of the
    // previous sync, and not the one from the sync that just ended.
    if (request.isUsingCachedGradleModels()) {
      mySyncState.syncSkipped(System.currentTimeMillis());
    }
    else {
      mySyncState.syncEnded();
      GradleProjectSyncData.save(myProject);
    }
  }

  private void findAndShowVariantConflicts() {
    ConflictSet conflicts = findConflicts(myProject);

    List<Conflict> structureConflicts = conflicts.getStructureConflicts();
    if (!structureConflicts.isEmpty() && SystemProperties.getBooleanProperty("enable.project.profiles", false)) {
      ProjectProfileSelectionDialog dialog = new ProjectProfileSelectionDialog(myProject, structureConflicts);
      dialog.show();
    }

    List<Conflict> selectionConflicts = conflicts.getSelectionConflicts();
    if (!selectionConflicts.isEmpty()) {
      boolean atLeastOneSolved = solveSelectionConflicts(selectionConflicts);
      if (atLeastOneSolved) {
        conflicts = findConflicts(myProject);
      }
    }
    conflicts.showSelectionConflicts();
  }

  private void setMakeStepInJunitRunConfigurations(@NotNull String makeTaskName) {
    ConfigurationType junitConfigurationType = AndroidJUnitConfigurationType.getInstance();
    BeforeRunTaskProvider<BeforeRunTask>[] taskProviders = Extensions.getExtensions(BeforeRunTaskProvider.EXTENSION_POINT_NAME, myProject);

    BeforeRunTaskProvider targetProvider = null;
    for (BeforeRunTaskProvider<? extends BeforeRunTask> provider : taskProviders) {
      if (makeTaskName.equals(provider.getName())) {
        targetProvider = provider;
        break;
      }
    }

    if (targetProvider != null) {
      RunManagerImpl runManager = RunManagerImpl.getInstanceImpl(myProject);
      // Set the correct "Make step" in the "JUnit Run Configuration" template.
      for (ConfigurationFactory configurationFactory : junitConfigurationType.getConfigurationFactories()) {
        RunnerAndConfigurationSettings template = runManager.getConfigurationTemplate(configurationFactory);
        RunConfiguration runConfiguration = template.getConfiguration();
        setMakeStepInJUnitConfiguration(targetProvider, runConfiguration);
      }

      // Set the correct "Make step" in existing JUnit Configurations.
      for (RunConfiguration runConfiguration : runManager.getConfigurationsList(junitConfigurationType)) {
        setMakeStepInJUnitConfiguration(targetProvider, runConfiguration);
      }
    }
  }

  private void setMakeStepInJUnitConfiguration(@NotNull BeforeRunTaskProvider targetProvider, @NotNull RunConfiguration runConfiguration) {
    // Only "make" steps of beforeRunTasks should be overridden (see http://b.android.com/194704 and http://b.android.com/227280)
    List<BeforeRunTask> newBeforeRunTasks = new LinkedList<>();
    RunManagerImpl runManager = RunManagerImpl.getInstanceImpl(myProject);
    for (BeforeRunTask beforeRunTask : runManager.getBeforeRunTasks(runConfiguration)) {
      if (beforeRunTask.getProviderId().equals(CompileStepBeforeRun.ID)) {
        BeforeRunTask task = targetProvider.createTask(runConfiguration);
        if (task != null) {
          task.setEnabled(true);
          newBeforeRunTasks.add(task);
        }
      }
      else {
        newBeforeRunTasks.add(beforeRunTask);
      }
    }
    runManager.setBeforeRunTasks(runConfiguration, newBeforeRunTasks, false);
  }

  private void attemptToGenerateSources(@NotNull Request request) {
    if (!request.isGenerateSourcesAfterSync()) {
      return;
    }
    boolean cleanProjectAfterSync = request.isCleanProjectAfterSync();
    if (!cleanProjectAfterSync) {
      // Figure out if the plugin version changed. If it did, force a clean.
      // See: https://code.google.com/p/android/issues/detail?id=216616
      Map<String, GradleVersion> previousPluginVersionsPerModule = getPluginVersionsPerModule(myProject);
      storePluginVersionsPerModule(myProject);
      if (previousPluginVersionsPerModule != null && !previousPluginVersionsPerModule.isEmpty()) {

        Map<String, GradleVersion> currentPluginVersionsPerModule = getPluginVersionsPerModule(myProject);
        assert currentPluginVersionsPerModule != null;

        for (Map.Entry<String, GradleVersion> entry : currentPluginVersionsPerModule.entrySet()) {
          String modulePath = entry.getKey();
          GradleVersion previous = previousPluginVersionsPerModule.get(modulePath);
          if (previous == null || entry.getValue().compareTo(previous) != 0) {
            cleanProjectAfterSync = true;
            break;
          }
        }
      }
    }
    myProjectBuilder.generateSourcesOnly(cleanProjectAfterSync);
  }

  public static class Request {
    @NotNull public static final Request DEFAULT_REQUEST = new Request() {
      @Override
      @NotNull
      public Request setCleanProjectAfterSync(boolean cleanProjectAfterSync) {
        throw new UnsupportedOperationException();
      }

      @Override
      @NotNull
      public Request setGenerateSourcesAfterSync(boolean generateSourcesAfterSync) {
        throw new UnsupportedOperationException();
      }

      @Override
      @NotNull
      public Request setLastSyncTimestamp(long lastSyncTimestamp) {
        throw new UnsupportedOperationException();
      }

      @Override
      @NotNull
      public Request setUsingCachedGradleModels(boolean usingCachedGradleModels) {
        throw new UnsupportedOperationException();
      }
    };

    private boolean myUsingCachedGradleModels;
    private boolean myCleanProjectAfterSync;
    private boolean myGenerateSourcesAfterSync = true;
    private long myLastSyncTimestamp = -1L;

    boolean isUsingCachedGradleModels() {
      return myUsingCachedGradleModels;
    }

    @NotNull
    public Request setUsingCachedGradleModels(boolean usingCachedGradleModels) {
      myUsingCachedGradleModels = usingCachedGradleModels;
      return this;
    }

    boolean isCleanProjectAfterSync() {
      return myCleanProjectAfterSync;
    }

    @NotNull
    public Request setCleanProjectAfterSync(boolean cleanProjectAfterSync) {
      myCleanProjectAfterSync = cleanProjectAfterSync;
      return this;
    }

    boolean isGenerateSourcesAfterSync() {
      return myGenerateSourcesAfterSync;
    }

    @NotNull
    public Request setGenerateSourcesAfterSync(boolean generateSourcesAfterSync) {
      myGenerateSourcesAfterSync = generateSourcesAfterSync;
      return this;
    }

    long getLastSyncTimestamp() {
      return myLastSyncTimestamp;
    }

    @NotNull
    public Request setLastSyncTimestamp(long lastSyncTimestamp) {
      myLastSyncTimestamp = lastSyncTimestamp;
      return this;
    }
  }
}
