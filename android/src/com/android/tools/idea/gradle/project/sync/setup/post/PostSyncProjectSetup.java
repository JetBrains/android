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

import com.android.builder.model.AndroidProject;
import com.android.builder.model.NativeAndroidProject;
import com.android.ide.common.repository.GradleVersion;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.gradle.customizer.dependency.Dependency;
import com.android.tools.idea.gradle.customizer.dependency.DependencySet;
import com.android.tools.idea.gradle.customizer.dependency.LibraryDependency;
import com.android.tools.idea.gradle.customizer.dependency.ModuleDependency;
import com.android.tools.idea.gradle.project.AndroidGradleProjectComponent;
import com.android.tools.idea.gradle.project.GradleProjectSyncData;
import com.android.tools.idea.gradle.project.LibraryAttachments;
import com.android.tools.idea.gradle.project.build.GradleProjectBuilder;
import com.android.tools.idea.gradle.project.facet.java.JavaFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.compatibility.VersionCompatibilityChecker;
import com.android.tools.idea.gradle.project.sync.messages.SyncMessage;
import com.android.tools.idea.gradle.project.sync.messages.SyncMessages;
import com.android.tools.idea.gradle.project.sync.setup.module.android.DependenciesModuleSetupStep;
import com.android.tools.idea.gradle.project.sync.setup.module.common.DependencySetupErrors;
import com.android.tools.idea.gradle.project.sync.setup.post.project.ProjectSetup;
import com.android.tools.idea.gradle.project.sync.setup.post.upgrade.PluginVersionUpgrade;
import com.android.tools.idea.gradle.project.sync.validation.common.CommonModuleValidator;
import com.android.tools.idea.gradle.run.MakeBeforeRunTaskProvider;
import com.android.tools.idea.gradle.project.sync.hyperlink.InstallPlatformHyperlink;
import com.android.tools.idea.gradle.variant.conflict.Conflict;
import com.android.tools.idea.gradle.variant.conflict.ConflictSet;
import com.android.tools.idea.gradle.variant.profiles.ProjectProfileSelectionDialog;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.testartifacts.scopes.TestArtifactSearchScopes;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.compiler.options.CompileStepBeforeRun;
import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.BeforeRunTaskProvider;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.junit.JUnitConfigurationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.ui.OrderRoot;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SystemProperties;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

import static com.android.SdkConstants.FN_ANNOTATIONS_JAR;
import static com.android.SdkConstants.FN_FRAMEWORK_LIBRARY;
import static com.android.tools.idea.gradle.project.LibraryAttachments.getStoredLibraryAttachments;
import static com.android.tools.idea.gradle.project.sync.messages.MessageType.ERROR;
import static com.android.tools.idea.gradle.service.notification.errors.AbstractSyncErrorHandler.FAILED_TO_SYNC_GRADLE_PROJECT_ERROR_GROUP_FORMAT;
import static com.android.tools.idea.gradle.util.FilePaths.getJarFromJarUrl;
import static com.android.tools.idea.gradle.util.FilePaths.pathToUrl;
import static com.android.tools.idea.gradle.util.GradleUtil.*;
import static com.android.tools.idea.gradle.util.Projects.*;
import static com.android.tools.idea.gradle.variant.conflict.ConflictResolution.solveSelectionConflicts;
import static com.android.tools.idea.gradle.variant.conflict.ConflictSet.findConflicts;
import static com.android.tools.idea.project.NewProjects.createRunConfigurations;
import static com.android.tools.idea.startup.ExternalAnnotationsSupport.attachJdkAnnotations;
import static com.intellij.openapi.roots.OrderRootType.CLASSES;
import static com.intellij.openapi.roots.OrderRootType.SOURCES;
import static com.intellij.openapi.util.io.FileUtil.delete;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static com.intellij.util.ExceptionUtil.rethrowAllAsUnchecked;
import static org.jetbrains.android.util.AndroidUtils.isAndroidStudio;

public class PostSyncProjectSetup {
  @NotNull private final Project myProject;
  @NotNull private final AndroidSdks myAndroidSdks;
  @NotNull private final GradleSyncInvoker mySyncInvoker;
  @NotNull private final GradleSyncState mySyncState;
  @NotNull private final DependencySetupErrors myDependencySetupErrors;
  @NotNull private final ProjectSetup myProjectSetup;
  @NotNull private final PluginVersionUpgrade myPluginVersionUpgrade;
  @NotNull private final SyncMessages mySyncMessages;
  @NotNull private final DependenciesModuleSetupStep myDependenciesModuleSetupStep;
  @NotNull private final VersionCompatibilityChecker myVersionCompatibilityChecker;
  @NotNull private final GradleProjectBuilder myProjectBuilder;
  @NotNull private final CommonModuleValidator.Factory myModuleValidatorFactory;
  @NotNull private final RunManagerImpl myRunManager;

  @NotNull
  public static PostSyncProjectSetup getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, PostSyncProjectSetup.class);
  }

  @SuppressWarnings("unused") // Instantiated by IDEA
  public PostSyncProjectSetup(@NotNull Project project,
                              @NotNull AndroidSdks androidSdks,
                              @NotNull GradleSyncInvoker syncInvoker,
                              @NotNull GradleSyncState syncState,
                              @NotNull SyncMessages syncMessages,
                              @NotNull DependencySetupErrors dependencySetupErrors,
                              @NotNull VersionCompatibilityChecker versionCompatibilityChecker,
                              @NotNull GradleProjectBuilder projectBuilder) {
    this(project, androidSdks, syncInvoker, syncState, dependencySetupErrors, new ProjectSetup(project), new PluginVersionUpgrade(project),
         syncMessages, DependenciesModuleSetupStep.getInstance(), versionCompatibilityChecker, projectBuilder,
         new CommonModuleValidator.Factory(), RunManagerImpl.getInstanceImpl(project));
  }

  @VisibleForTesting
  PostSyncProjectSetup(@NotNull Project project,
                       @NotNull AndroidSdks androidSdks,
                       @NotNull GradleSyncInvoker syncInvoker,
                       @NotNull GradleSyncState syncState,
                       @NotNull DependencySetupErrors dependencySetupErrors,
                       @NotNull ProjectSetup projectSetup,
                       @NotNull PluginVersionUpgrade pluginVersionUpgrade,
                       @NotNull SyncMessages syncMessages,
                       @NotNull DependenciesModuleSetupStep dependenciesModuleSetupStep,
                       @NotNull VersionCompatibilityChecker versionCompatibilityChecker,
                       @NotNull GradleProjectBuilder projectBuilder,
                       @NotNull CommonModuleValidator.Factory moduleValidatorFactory,
                       @NotNull RunManagerImpl runManager) {
    myProject = project;
    myAndroidSdks = androidSdks;
    mySyncInvoker = syncInvoker;
    mySyncState = syncState;
    myDependencySetupErrors = dependencySetupErrors;
    myProjectSetup = projectSetup;
    myPluginVersionUpgrade = pluginVersionUpgrade;
    mySyncMessages = syncMessages;
    myDependenciesModuleSetupStep = dependenciesModuleSetupStep;
    myVersionCompatibilityChecker = versionCompatibilityChecker;
    myProjectBuilder = projectBuilder;
    myModuleValidatorFactory = moduleValidatorFactory;
    myRunManager = runManager;
  }

  /**
   * Invoked after a project has been synced with Gradle.
   */
  public void setUpProject(@NotNull Request request, @Nullable ProgressIndicator progressIndicator) {
    // This will be true if sync failed because of an exception thrown by Gradle. GradleSyncState will know that sync stopped.
    boolean lastSyncFailed = mySyncState.lastSyncFailed();

    // This will be true if sync was successful but there were sync issues found (e.g. unresolved dependencies.)
    // GradleSyncState still thinks that sync is still being executed.
    boolean hasSyncErrors = mySyncState.getSummary().hasSyncErrors();

    boolean syncFailed = lastSyncFailed || hasSyncErrors;

    if (syncFailed && request.isUsingCachedGradleModels()) {
      onCachedModelsSetupFailure(request);
      return;
    }

    myDependencySetupErrors.reportErrors();
    myVersionCompatibilityChecker.checkAndReportComponentIncompatibilities(myProject);

    CommonModuleValidator moduleValidator = myModuleValidatorFactory.create(myProject);
    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
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

    executeProjectChanges(myProject, () -> {
      IdeModifiableModelsProvider modelsProvider = new IdeModifiableModelsProviderImpl(myProject);
      try {
        attachSourcesToLibraries(modelsProvider);
        adjustModuleStructures(modelsProvider);
        modelsProvider.commit();
      }
      catch (Throwable t) {
        modelsProvider.dispose();
        rethrowAllAsUnchecked(t);
      }
      ensureValidSdks();
    });

    AndroidGradleProjectComponent.getInstance(myProject).checkForSupportedModules();

    findAndShowVariantConflicts();
    myProjectSetup.setUpProject(progressIndicator, false /* sync successful */);

    TestArtifactSearchScopes.initializeScopes(myProject);

    // For Android Studio, use "Gradle-Aware Make" to run JUnit tests.
    // For IDEA, use regular "Make".
    String taskName = isAndroidStudio() ? MakeBeforeRunTaskProvider.TASK_NAME : ExecutionBundle.message("before.launch.compile.step");
    setMakeStepInJunitRunConfigurations(taskName);

    notifySyncFinished(request);

    if (request.isGenerateSourcesAfterSync()) {
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

    TemplateManager.getInstance().refreshDynamicTemplateMenu(myProject);

    disposeModulesMarkedForRemoval();

    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    for (Module module : moduleManager.getModules()) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null && facet.isAppProject()) {
        createRunConfigurations(facet);
      }
    }
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

  private void disposeModulesMarkedForRemoval() {
    final Collection<Module> modulesToDispose = getModulesToDisposePostSync(myProject);
    if (modulesToDispose == null || modulesToDispose.isEmpty()) {
      return;
    }
    ApplicationManager.getApplication().runWriteAction(() -> {
      ModuleManager moduleManager = ModuleManager.getInstance(myProject);
      List<File> imlFilesToRemove = Lists.newArrayList();
      ModifiableModuleModel moduleModel = moduleManager.getModifiableModel();
      try {
        for (Module module : modulesToDispose) {
          File imlFile = new File(toSystemDependentName(module.getModuleFilePath()));
          imlFilesToRemove.add(imlFile);
          moduleModel.disposeModule(module);
        }
      }
      finally {
        setModulesToDisposePostSync(myProject, null);
        moduleModel.commit();
      }
      for (File imlFile : imlFilesToRemove) {
        if (imlFile.isFile()) {
          delete(imlFile);
        }
      }
    });
  }

  private void adjustModuleStructures(@NotNull IdeModifiableModelsProvider modelsProvider) {
    Set<Sdk> androidSdks = Sets.newHashSet();

    for (Module module : modelsProvider.getModules()) {
      ModifiableRootModel model = modelsProvider.getModifiableRootModel(module);
      adjustInterModuleDependencies(module, modelsProvider);

      Sdk sdk = model.getSdk();
      if (sdk != null) {
        if (myAndroidSdks.isAndroidSdk(sdk)) {
          androidSdks.add(sdk);
        }
        continue;
      }

      NativeAndroidProject nativeAndroidProject = getNativeAndroidProject(module);
      if (nativeAndroidProject != null) {
        // Native modules does not need any jdk entry.
        continue;
      }

      Sdk jdk = IdeSdks.getInstance().getJdk();
      model.setSdk(jdk);
    }

    for (Sdk sdk : androidSdks) {
      myAndroidSdks.refreshLibrariesIn(sdk);
    }

    removeAllModuleCompiledArtifacts(myProject);
  }

  private void adjustInterModuleDependencies(@NotNull Module module, @NotNull IdeModifiableModelsProvider modelsProvider) {
    // Verifies that inter-module dependencies between Android modules are correctly set. If module A depends on module B, and module B
    // does not contain sources but exposes an AAR as an artifact, the IDE should set the dependency in the 'exploded AAR' instead of trying
    // to find the library in module B. The 'exploded AAR' is in the 'build' folder of module A.
    // See: https://code.google.com/p/android/issues/detail?id=162634
    AndroidProject androidProject = getAndroidProject(module);
    if (androidProject == null) {
      return;
    }

    updateAarDependencies(module, modelsProvider, androidProject);
  }

  // See: https://code.google.com/p/android/issues/detail?id=163888
  private void updateAarDependencies(@NotNull Module module,
                                     @NotNull IdeModifiableModelsProvider modelsProvider,
                                     @NotNull AndroidProject androidProject) {
    ModifiableRootModel modifiableModel = modelsProvider.getModifiableRootModel(module);
    for (Module dependency : modifiableModel.getModuleDependencies()) {
      updateTransitiveDependencies(module, modelsProvider, androidProject, dependency);
    }
  }

  // See: https://code.google.com/p/android/issues/detail?id=213627
  private void updateTransitiveDependencies(@NotNull Module module,
                                            @NotNull IdeModifiableModelsProvider modelsProvider,
                                            @NotNull AndroidProject androidProject,
                                            @Nullable Module dependency) {
    if (dependency == null) {
      return;
    }

    JavaFacet javaFacet = JavaFacet.getInstance(dependency);
    if (javaFacet != null
        // BUILDABLE == false -> means this is an AAR-based module, not a regular Java lib module
        && javaFacet.getConfiguration().BUILDABLE) {
      // Ignore Java lib modules. They are already set up properly.
      return;
    }
    AndroidProject dependencyAndroidProject = getAndroidProject(dependency);
    if (dependencyAndroidProject != null) {
      AndroidModuleModel androidModel = AndroidModuleModel.get(dependency);
      if (androidModel != null) {
        DependencySet dependencies = Dependency.extractFrom(androidModel);

        for (LibraryDependency libraryDependency : dependencies.onLibraries()) {
          myDependenciesModuleSetupStep
            .updateLibraryDependency(module, modelsProvider, libraryDependency, androidModel.getAndroidProject());
        }

        Project project = module.getProject();
        for (ModuleDependency moduleDependency : dependencies.onModules()) {
          Module module1 = moduleDependency.getModule(project);
          updateTransitiveDependencies(module, modelsProvider, androidProject, module1);
        }
      }
    }
    else {
      LibraryDependency backup = getModuleCompiledArtifact(dependency);
      if (backup != null) {
        myDependenciesModuleSetupStep.updateLibraryDependency(module, modelsProvider, backup, androidProject);
      }
    }
  }

  private void attachSourcesToLibraries(@NotNull IdeModifiableModelsProvider modelsProvider) {
    LibraryAttachments storedLibraryAttachments = getStoredLibraryAttachments(myProject);

    for (Library library : modelsProvider.getAllLibraries()) {
      Set<String> sourcePaths = Sets.newHashSet();

      for (VirtualFile file : library.getFiles(SOURCES)) {
        sourcePaths.add(file.getUrl());
      }

      Library.ModifiableModel libraryModel = modelsProvider.getModifiableLibraryModel(library);

      // Find the source attachment based on the location of the library jar file.
      for (VirtualFile classFile : library.getFiles(CLASSES)) {
        VirtualFile sourceJar = findSourceJarForJar(classFile);
        if (sourceJar != null) {
          String url = pathToUrl(sourceJar.getPath());
          if (!sourcePaths.contains(url)) {
            libraryModel.addRoot(url, SOURCES);
            sourcePaths.add(url);
          }
        }
      }

      if (storedLibraryAttachments != null) {
        storedLibraryAttachments.addUrlsTo(libraryModel);
      }
    }
    if (storedLibraryAttachments != null) {
      storedLibraryAttachments.removeFromProject();
    }
  }

  @Nullable
  private static VirtualFile findSourceJarForJar(@NotNull VirtualFile jarFile) {
    // We need to get the real jar file. The one that we received is just a wrapper around a URL. Getting the parent from this file returns
    // null.
    File jarFilePath = getJarFromJarUrl(jarFile.getUrl());
    return jarFilePath != null ? findSourceJarForLibrary(jarFilePath) : null;
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

  private void ensureValidSdks() {
    Collection<Sdk> invalidAndroidSdks = Sets.newHashSet();
    ModuleManager moduleManager = ModuleManager.getInstance(myProject);

    for (Module module : moduleManager.getModules()) {
      AndroidFacet androidFacet = AndroidFacet.getInstance(module);
      if (androidFacet != null && androidFacet.getAndroidModel() != null) {
        Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
        if (sdk != null && !invalidAndroidSdks.contains(sdk) && (isMissingAndroidLibrary(sdk) || shouldRemoveAnnotationsJar(sdk))) {
          // First try to recreate SDK; workaround for issue 78072
          AndroidSdkAdditionalData additionalData = myAndroidSdks.getAndroidSdkAdditionalData(sdk);
          AndroidSdkData sdkData = AndroidSdkData.getSdkData(sdk);
          if (additionalData != null && sdkData != null) {
            IAndroidTarget target = additionalData.getBuildTarget(sdkData);
            if (target == null) {
              AndroidSdkHandler sdkHandler = sdkData.getSdkHandler();
              StudioLoggerProgressIndicator logger = new StudioLoggerProgressIndicator(getClass());
              sdkHandler.getSdkManager(logger).loadSynchronously(0, logger, null, null);
              target =
                sdkHandler.getAndroidTargetManager(logger).getTargetFromHashString(additionalData.getBuildTargetHashString(), logger);
            }
            if (target != null) {
              SdkModificator sdkModificator = sdk.getSdkModificator();
              sdkModificator.removeAllRoots();
              for (OrderRoot orderRoot : myAndroidSdks.getLibraryRootsForTarget(target, sdk, true)) {
                sdkModificator.addRoot(orderRoot.getFile(), orderRoot.getType());
              }
              attachJdkAnnotations(sdkModificator);
              sdkModificator.commitChanges();
            }
          }

          // If attempting to fix up the roots in the SDK fails, install the target over again
          // (this is a truly corrupt install, as opposed to an incorrectly synced SDK which the
          // above workaround deals with)
          if (isMissingAndroidLibrary(sdk)) {
            invalidAndroidSdks.add(sdk);
          }
        }
      }
    }

    if (!invalidAndroidSdks.isEmpty()) {
      reinstallMissingPlatforms(invalidAndroidSdks);
    }
  }

  private boolean isMissingAndroidLibrary(@NotNull Sdk sdk) {
    if (myAndroidSdks.isAndroidSdk(sdk)) {
      for (VirtualFile library : sdk.getRootProvider().getFiles(CLASSES)) {
        // This code does not through the classes in the Android SDK. It iterates through a list of 3 files in the IDEA SDK: android.jar,
        // annotations.jar and res folder.
        if (library.getName().equals(FN_FRAMEWORK_LIBRARY) && library.exists()) {
          return false;
        }
      }
    }
    return true;
  }

  /*
   * Indicates whether annotations.jar should be removed from the given SDK (if it is an Android SDK.)
   * There are 2 issues:
   * 1. annotations.jar is not needed for API level 16 and above. The annotations are already included in android.jar. Until recently, the
   *    IDE added annotations.jar to the IDEA Android SDK definition unconditionally.
   * 2. Because annotations.jar is in the classpath, the IDE locks the file on Windows making automatic updates of SDK Tools fail. The
   *    update not only fails, it corrupts the 'tools' folder in the SDK.
   * From now on, creating IDEA Android SDKs will not include annotations.jar if API level is 16 or above, but we still need to remove
   * this jar from existing IDEA Android SDKs.
   */
  private boolean shouldRemoveAnnotationsJar(@NotNull Sdk sdk) {
    if (myAndroidSdks.isAndroidSdk(sdk)) {
      AndroidSdkAdditionalData additionalData = myAndroidSdks.getAndroidSdkAdditionalData(sdk);
      AndroidSdkData sdkData = AndroidSdkData.getSdkData(sdk);
      boolean needsAnnotationsJar = false;
      if (additionalData != null && sdkData != null) {
        IAndroidTarget target = additionalData.getBuildTarget(sdkData);
        if (target != null) {
          needsAnnotationsJar = myAndroidSdks.needsAnnotationsJarInClasspath(target);
        }
      }
      for (VirtualFile library : sdk.getRootProvider().getFiles(CLASSES)) {
        // This code does not through the classes in the Android SDK. It iterates through a list of 3 files in the IDEA SDK: android.jar,
        // annotations.jar and res folder.
        if (library.getName().equals(FN_ANNOTATIONS_JAR) && library.exists() && !needsAnnotationsJar) {
          return true;
        }
      }
    }
    return false;
  }

  private void reinstallMissingPlatforms(@NotNull Collection<Sdk> invalidAndroidSdks) {
    List<AndroidVersion> versionsToInstall = Lists.newArrayList();
    List<String> missingPlatforms = Lists.newArrayList();

    for (Sdk sdk : invalidAndroidSdks) {
      AndroidSdkAdditionalData additionalData = myAndroidSdks.getAndroidSdkAdditionalData(sdk);
      if (additionalData != null) {
        String platform = additionalData.getBuildTargetHashString();
        if (platform != null) {
          missingPlatforms.add("'" + platform + "'");
          AndroidVersion version = AndroidTargetHash.getPlatformVersion(platform);
          if (version != null) {
            versionsToInstall.add(version);
          }
        }
      }
    }

    if (!versionsToInstall.isEmpty()) {
      String group = String.format(FAILED_TO_SYNC_GRADLE_PROJECT_ERROR_GROUP_FORMAT, myProject.getName());
      String text = "Missing Android platform(s) detected: " + Joiner.on(", ").join(missingPlatforms);
      SyncMessage msg = new SyncMessage(group, ERROR, text);
      msg.add(new InstallPlatformHyperlink(versionsToInstall));
      mySyncMessages.report(msg);
    }
  }

  private void setMakeStepInJunitRunConfigurations(@NotNull String makeTaskName) {
    ConfigurationType junitConfigurationType = JUnitConfigurationType.getInstance();
    BeforeRunTaskProvider<BeforeRunTask>[] taskProviders = Extensions.getExtensions(BeforeRunTaskProvider.EXTENSION_POINT_NAME, myProject);

    BeforeRunTaskProvider targetProvider = null;
    for (BeforeRunTaskProvider<? extends BeforeRunTask> provider : taskProviders) {
      if (makeTaskName.equals(provider.getName())) {
        targetProvider = provider;
        break;
      }
    }

    if (targetProvider != null) {
      // Set the correct "Make step" in the "JUnit Run Configuration" template.
      for (ConfigurationFactory configurationFactory : junitConfigurationType.getConfigurationFactories()) {
        RunnerAndConfigurationSettings template = myRunManager.getConfigurationTemplate(configurationFactory);
        RunConfiguration runConfiguration = template.getConfiguration();
        setMakeStepInJUnitConfiguration(targetProvider, runConfiguration);
      }

      // Set the correct "Make step" in existing JUnit Configurations.
      RunConfiguration[] junitRunConfigurations = myRunManager.getConfigurations(junitConfigurationType);
      for (RunConfiguration runConfiguration : junitRunConfigurations) {
        setMakeStepInJUnitConfiguration(targetProvider, runConfiguration);
      }
    }
  }

  private void setMakeStepInJUnitConfiguration(@NotNull BeforeRunTaskProvider targetProvider, @NotNull RunConfiguration runConfiguration) {
    // Only "make" steps of beforeRunTasks should be overridden (see http://b.android.com/194704 and http://b.android.com/227280)
    List<BeforeRunTask> newBeforeRunTasks = new LinkedList<>();
    for (BeforeRunTask beforeRunTask : myRunManager.getBeforeRunTasks(runConfiguration)) {
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
    myRunManager.setBeforeRunTasks(runConfiguration, newBeforeRunTasks, false);
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
