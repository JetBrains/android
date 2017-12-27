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

import com.android.builder.model.SyncIssue;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.GradleProjectSyncData;
import com.android.tools.idea.gradle.project.build.GradleBuildState;
import com.android.tools.idea.gradle.project.build.GradleProjectBuilder;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.compatibility.VersionCompatibilityChecker;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.android.tools.idea.gradle.project.sync.setup.module.common.DependencySetupIssues;
import com.android.tools.idea.gradle.project.sync.setup.post.project.DisposedModules;
import com.android.tools.idea.gradle.project.sync.validation.common.CommonModuleValidator;
import com.android.tools.idea.gradle.run.MakeBeforeRunTaskProvider;
import com.android.tools.idea.gradle.variant.conflict.Conflict;
import com.android.tools.idea.gradle.variant.conflict.ConflictSet;
import com.android.tools.idea.gradle.variant.profiles.ProjectProfileSelectionDialog;
import com.android.tools.idea.instantapp.ProvistionTasks;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.testartifacts.junit.AndroidJUnitConfigurationType;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.compiler.options.CompileStepBeforeRun;
import com.intellij.concurrency.JobLauncher;
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
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

import static com.android.tools.idea.gradle.project.build.BuildStatus.SKIPPED;
import static com.android.tools.idea.gradle.util.Projects.getPluginVersionsPerModule;
import static com.android.tools.idea.gradle.util.Projects.storePluginVersionsPerModule;
import static com.android.tools.idea.gradle.variant.conflict.ConflictSet.findConflicts;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_PROJECT_LOADED;

public class PostSyncProjectSetup {
  @NotNull private final Project myProject;
  @NotNull private final IdeInfo myIdeInfo;
  @NotNull private final GradleProjectInfo myGradleProjectInfo;
  @NotNull private final GradleSyncInvoker mySyncInvoker;
  @NotNull private final GradleSyncState mySyncState;
  @NotNull private final DependencySetupIssues myDependencySetupIssues;
  @NotNull private final ProjectSetup myProjectSetup;
  @NotNull private final ModuleSetup myModuleSetup;
  @NotNull private final PluginVersionUpgrade myPluginVersionUpgrade;
  @NotNull private final VersionCompatibilityChecker myVersionCompatibilityChecker;
  @NotNull private final GradleProjectBuilder myProjectBuilder;
  @NotNull private final CommonModuleValidator.Factory myModuleValidatorFactory;
  @NotNull private final RunManagerImpl myRunManager;
  @NotNull private final ProvistionTasks myProvistionTasks;

  @NotNull
  public static PostSyncProjectSetup getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, PostSyncProjectSetup.class);
  }

  @SuppressWarnings("unused") // Instantiated by IDEA
  public PostSyncProjectSetup(@NotNull Project project,
                              @NotNull IdeInfo ideInfo,
                              @NotNull GradleProjectInfo gradleProjectInfo,
                              @NotNull GradleSyncInvoker syncInvoker,
                              @NotNull GradleSyncState syncState,
                              @NotNull GradleSyncMessages syncMessages,
                              @NotNull DependencySetupIssues dependencySetupIssues,
                              @NotNull PluginVersionUpgrade pluginVersionUpgrade,
                              @NotNull VersionCompatibilityChecker versionCompatibilityChecker,
                              @NotNull GradleProjectBuilder projectBuilder) {
    this(project, ideInfo, gradleProjectInfo, syncInvoker, syncState, dependencySetupIssues, new ProjectSetup(project),
         new ModuleSetup(project), pluginVersionUpgrade, versionCompatibilityChecker, projectBuilder, new CommonModuleValidator.Factory(),
         RunManagerImpl.getInstanceImpl(project), new ProvistionTasks());
  }

  @VisibleForTesting
  PostSyncProjectSetup(@NotNull Project project,
                       @NotNull IdeInfo ideInfo,
                       @NotNull GradleProjectInfo gradleProjectInfo,
                       @NotNull GradleSyncInvoker syncInvoker,
                       @NotNull GradleSyncState syncState,
                       @NotNull DependencySetupIssues dependencySetupIssues,
                       @NotNull ProjectSetup projectSetup,
                       @NotNull ModuleSetup moduleSetup,
                       @NotNull PluginVersionUpgrade pluginVersionUpgrade,
                       @NotNull VersionCompatibilityChecker versionCompatibilityChecker,
                       @NotNull GradleProjectBuilder projectBuilder,
                       @NotNull CommonModuleValidator.Factory moduleValidatorFactory,
                       @NotNull RunManagerImpl runManager,
                       @NotNull ProvistionTasks provistionTasks) {
    myProject = project;
    myIdeInfo = ideInfo;
    myGradleProjectInfo = gradleProjectInfo;
    mySyncInvoker = syncInvoker;
    mySyncState = syncState;
    myDependencySetupIssues = dependencySetupIssues;
    myProjectSetup = projectSetup;
    myModuleSetup = moduleSetup;
    myPluginVersionUpgrade = pluginVersionUpgrade;
    myVersionCompatibilityChecker = versionCompatibilityChecker;
    myProjectBuilder = projectBuilder;
    myModuleValidatorFactory = moduleValidatorFactory;
    myRunManager = runManager;
    myProvistionTasks = provistionTasks;
  }

  /**
   * Invoked after a project has been synced with Gradle.
   */
  public void setUpProject(@NotNull Request request, @NotNull ProgressIndicator progressIndicator) {
    myGradleProjectInfo.setNewOrImportedProject(false);
    boolean syncFailed = mySyncState.lastSyncFailedOrHasIssues();

    if (syncFailed && request.isUsingCachedGradleModels()) {
      onCachedModelsSetupFailure(request);
      return;
    }

    myDependencySetupIssues.reportIssues();
    myVersionCompatibilityChecker.checkAndReportComponentIncompatibilities(myProject);

    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    List<Module> modules = Arrays.asList(moduleManager.getModules());
    CommonModuleValidator moduleValidator = myModuleValidatorFactory.create(myProject);
    JobLauncher.getInstance().invokeConcurrentlyUnderProgress(modules, progressIndicator, true, module -> {
      moduleValidator.validate(module);
      return true;
    });
    moduleValidator.fixAndReportFoundIssues();

    if (syncFailed) {
      failTestsIfSyncIssuesPresent();

      myProjectSetup.setUpProject(progressIndicator, true /* sync failed */);
      // Notify "sync end" event first, to register the timestamp. Otherwise the cache (GradleProjectSyncData) will store the date of the
      // previous sync, and not the one from the sync that just ended.
      mySyncState.syncFailed("");
      return;
    }

    if (!request.isSkipAndroidPluginUpgrade() && myPluginVersionUpgrade.checkAndPerformUpgrade()) {
      // Plugin version was upgraded and a sync was triggered.
      return;
    }

    new ProjectStructureUsageTracker(myProject).trackProjectStructure();

    DisposedModules.getInstance(myProject).deleteImlFilesForDisposedModules();

    findAndShowVariantConflicts();
    myProjectSetup.setUpProject(progressIndicator, false /* sync successful */);

    // For Android Studio, use "Gradle-Aware Make" to run JUnit tests.
    // For IDEA, use regular "Make".
    boolean androidStudio = myIdeInfo.isAndroidStudio();
    String taskName = androidStudio ? MakeBeforeRunTaskProvider.TASK_NAME : ExecutionBundle.message("before.launch.compile.step");
    setMakeStepInJunitRunConfigurations(taskName);

    myProvistionTasks.addInstantAppProvisionTaskToRunConfigurations(myProject);

    notifySyncFinished(request);
    attemptToGenerateSources(request);

    TemplateManager.getInstance().refreshDynamicTemplateMenu(myProject);

    myModuleSetup.setUpModules(null);
  }

  public void onCachedModelsSetupFailure(@NotNull Request request) {
    // Sync with cached model failed (e.g. when Studio has a newer embedded builder-model interfaces and the cache is using an older
    // version of such interfaces.
    long syncTimestamp = request.getLastSyncTimestamp();
    if (syncTimestamp < 0) {
      syncTimestamp = System.currentTimeMillis();
    }
    mySyncState.syncSkipped(syncTimestamp);
    // TODO add a new trigger for this?
    mySyncInvoker.requestProjectSyncAndSourceGeneration(myProject, TRIGGER_PROJECT_LOADED, null);
  }

  private void failTestsIfSyncIssuesPresent() {
    if (ApplicationManager.getApplication().isUnitTestMode() && mySyncState.getSummary().hasSyncErrors()) {
      StringBuilder buffer = new StringBuilder();
      buffer.append("Sync issues found!").append('\n');
      myGradleProjectInfo.forEachAndroidModule(facet -> {
        AndroidModel androidModel = facet.getAndroidModel();
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
    // Notify "sync end" event first, to register the timestamp. Otherwise the cache (GradleProjectSyncData) will store the date of the
    // previous sync, and not the one from the sync that just ended.
    if (request.isUsingCachedGradleModels()) {
      long timestamp = System.currentTimeMillis();
      mySyncState.syncSkipped(timestamp);
      GradleBuildState.getInstance(myProject).buildFinished(SKIPPED);
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
    if (cleanProjectAfterSync) {
      myProjectBuilder.cleanAndGenerateSources();
      return;
    }
    myProjectBuilder.generateSources();
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
    private boolean mySkipAndroidPluginUpgrade;
    private long myLastSyncTimestamp = -1L;

    public boolean isUsingCachedGradleModels() {
      return myUsingCachedGradleModels;
    }

    @NotNull
    public Request setUsingCachedGradleModels(boolean usingCachedGradleModels) {
      myUsingCachedGradleModels = usingCachedGradleModels;
      return this;
    }

    public boolean isCleanProjectAfterSync() {
      return myCleanProjectAfterSync;
    }

    @NotNull
    public Request setCleanProjectAfterSync(boolean cleanProjectAfterSync) {
      myCleanProjectAfterSync = cleanProjectAfterSync;
      return this;
    }

    public boolean isGenerateSourcesAfterSync() {
      return myGenerateSourcesAfterSync;
    }

    @NotNull
    public Request setGenerateSourcesAfterSync(boolean generateSourcesAfterSync) {
      myGenerateSourcesAfterSync = generateSourcesAfterSync;
      return this;
    }

    public long getLastSyncTimestamp() {
      return myLastSyncTimestamp;
    }

    public boolean isSkipAndroidPluginUpgrade() {
      return mySkipAndroidPluginUpgrade;
    }

    @TestOnly
    public void setSkipAndroidPluginUpgrade() {
      mySkipAndroidPluginUpgrade = true;
    }

    @NotNull
    public Request setLastSyncTimestamp(long lastSyncTimestamp) {
      myLastSyncTimestamp = lastSyncTimestamp;
      return this;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Request request = (Request)o;
      return myUsingCachedGradleModels == request.myUsingCachedGradleModels &&
             myCleanProjectAfterSync == request.myCleanProjectAfterSync &&
             myGenerateSourcesAfterSync == request.myGenerateSourcesAfterSync &&
             myLastSyncTimestamp == request.myLastSyncTimestamp;
    }

    @Override
    public int hashCode() {
      return Objects.hash(myUsingCachedGradleModels, myCleanProjectAfterSync, myGenerateSourcesAfterSync, myLastSyncTimestamp);
    }
  }
}
