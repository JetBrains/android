/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.builder.model.AndroidProject;
import com.android.builder.model.NativeAndroidProject;
import com.android.ide.common.repository.GradleVersion;
import com.android.repository.Revision;
import com.android.repository.api.ProgressIndicator;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.GradleSyncState;
import com.android.tools.idea.gradle.customizer.android.DependenciesModuleCustomizer;
import com.android.tools.idea.gradle.customizer.dependency.LibraryDependency;
import com.android.tools.idea.gradle.messages.Message;
import com.android.tools.idea.gradle.messages.ProjectSyncMessages;
import com.android.tools.idea.gradle.project.build.GradleProjectBuilder;
import com.android.tools.idea.gradle.run.MakeBeforeRunTaskProvider;
import com.android.tools.idea.gradle.service.notification.hyperlink.*;
import com.android.tools.idea.gradle.testing.TestArtifactSearchScopes;
import com.android.tools.idea.gradle.variant.conflict.Conflict;
import com.android.tools.idea.gradle.variant.conflict.ConflictSet;
import com.android.tools.idea.gradle.variant.profiles.ProjectProfileSelectionDialog;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.sdk.VersionCheck;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.BeforeRunTaskProvider;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.junit.JUnitConfigurationType;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.ui.OrderRoot;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.NonNavigatable;
import com.intellij.util.SystemProperties;
import org.jetbrains.android.AndroidPlugin;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.gradle.customizer.AbstractDependenciesModuleCustomizer.pathToUrl;
import static com.android.tools.idea.gradle.messages.CommonMessageGroupNames.FAILED_TO_SET_UP_SDK;
import static com.android.tools.idea.gradle.messages.CommonMessageGroupNames.UNHANDLED_SYNC_ISSUE_TYPE;
import static com.android.tools.idea.gradle.messages.Message.Type.ERROR;
import static com.android.tools.idea.gradle.project.LibraryAttachments.getStoredLibraryAttachments;
import static com.android.tools.idea.gradle.project.ProjectDiagnostics.findAndReportStructureIssues;
import static com.android.tools.idea.gradle.project.ProjectJdkChecks.hasCorrectJdkVersion;
import static com.android.tools.idea.gradle.service.notification.errors.AbstractSyncErrorHandler.FAILED_TO_SYNC_GRADLE_PROJECT_ERROR_GROUP_FORMAT;
import static com.android.tools.idea.gradle.util.FilePaths.getJarFromJarUrl;
import static com.android.tools.idea.gradle.util.GradleUtil.*;
import static com.android.tools.idea.gradle.util.Projects.*;
import static com.android.tools.idea.gradle.variant.conflict.ConflictResolution.solveSelectionConflicts;
import static com.android.tools.idea.gradle.variant.conflict.ConflictSet.findConflicts;
import static com.android.tools.idea.startup.AndroidStudioInitializer.isAndroidStudio;
import static com.android.tools.idea.startup.ExternalAnnotationsSupport.attachJdkAnnotations;
import static com.intellij.notification.NotificationType.INFORMATION;
import static com.intellij.openapi.roots.OrderRootType.CLASSES;
import static com.intellij.openapi.roots.OrderRootType.SOURCES;
import static com.intellij.openapi.util.io.FileUtil.delete;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static com.intellij.util.ExceptionUtil.rethrowAllAsUnchecked;
import static org.jetbrains.android.sdk.AndroidSdkUtils.*;

public class PostProjectSetupTasksExecutor {
  /**
   * Whether a message indicating that "a new SDK Tools version is available" is already shown.
   */
  private static boolean ourNewSdkVersionToolsInfoAlreadyShown;

  /**
   * Whether we've checked for build expiration
   */
  private static boolean ourCheckedExpiration;

  private static final boolean DEFAULT_GENERATE_SOURCES_AFTER_SYNC = true;
  private static final boolean DEFAULT_CLEAN_PROJECT_AFTER_SYNC = false;
  private static final boolean DEFAULT_USING_CACHED_PROJECT_DATA = false;
  private static final long DEFAULT_LAST_SYNC_TIMESTAMP = -1;

  @NotNull private final Project myProject;

  private volatile boolean myGenerateSourcesAfterSync = DEFAULT_GENERATE_SOURCES_AFTER_SYNC;
  private boolean myCleanProjectAfterSync = DEFAULT_CLEAN_PROJECT_AFTER_SYNC;
  private volatile boolean myUsingCachedProjectData = DEFAULT_USING_CACHED_PROJECT_DATA;
  private volatile long myLastSyncTimestamp = DEFAULT_LAST_SYNC_TIMESTAMP;

  @NotNull
  public static PostProjectSetupTasksExecutor getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, PostProjectSetupTasksExecutor.class);
  }

  public PostProjectSetupTasksExecutor(@NotNull Project project) {
    myProject = project;
  }

  /**
   * Invoked after a project has been synced with Gradle.
   */
  public void onProjectSyncCompletion() {
    if (lastGradleSyncFailed(myProject) && myUsingCachedProjectData) {
      // Sync with cached model failed (e.g. when Studio has a newer embedded builder-model interfaces and the cache is using an older
      // version of such interfaces.
      myUsingCachedProjectData = false;
      GradleProjectImporter.getInstance().requestProjectSync(myProject, null);
      return;
    }

    ProjectSyncMessages messages = ProjectSyncMessages.getInstance(myProject);
    messages.reportDependencySetupErrors();
    messages.reportComponentIncompatibilities();

    findAndReportStructureIssues(myProject);

    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    for (Module module : moduleManager.getModules()) {
      if (!hasCorrectJdkVersion(module)) {
        // we already displayed the error, no need to check each module.
        break;
      }
    }

    if (hasErrors(myProject) || lastGradleSyncFailed(myProject)) {
      addSdkLinkIfNecessary();
      checkSdkToolsVersion(myProject);
      updateGradleSyncState();
      return;
    }

    if (shouldForcePluginVersionUpgrade() || myProject.isDisposed()) {
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
    enforceExternalBuild(myProject);

    AndroidGradleProjectComponent.getInstance(myProject).checkForSupportedModules();

    findAndShowVariantConflicts();
    checkSdkToolsVersion(myProject);
    addSdkLinkIfNecessary();

    if (GradleExperimentalSettings.getInstance().LOAD_ALL_TEST_ARTIFACTS) {
      TestArtifactSearchScopes.initializeScopes(myProject);
      //FileColorConfigurationUtil.createAndroidTestFileColorConfigurationIfNotExist(myProject);
      // Before sync, android test files are just considered as normal test file which has different FileColor configuration.
      // If there is any opening tab for android test file, the tab color will not change unless we refresh it.
      //UISettings.getInstance().fireUISettingsChanged();
    }

    // For Android Studio, use "Gradle-Aware Make" to run JUnit tests.
    // For IDEA, use regular "Make".
    String taskName = isAndroidStudio() ? MakeBeforeRunTaskProvider.TASK_NAME : ExecutionBundle.message("before.launch.compile.step");
    setMakeStepInJunitRunConfigurations(taskName);
    updateGradleSyncState();

    String pluginVersionTooOld = shouldRecommendPluginVersionUpgrade();
    if (pluginVersionTooOld != null) {
      boolean upgrade = new PluginVersionRecommendedUpdateDialog(myProject, pluginVersionTooOld).showAndGet();
      if (upgrade) {
        if (updateGradlePluginVersionAndNotifyFailure(myProject, GRADLE_PLUGIN_LATEST_VERSION, GRADLE_LATEST_VERSION, false)) {
          // plugin version updated and a project sync was requested. No need to continue.
          return;
        }
      }
    }

    if (myGenerateSourcesAfterSync) {
      GradleProjectBuilder.getInstance(myProject).generateSourcesOnly(myCleanProjectAfterSync);
    }

    // set default value back.
    myGenerateSourcesAfterSync = DEFAULT_GENERATE_SOURCES_AFTER_SYNC;
    myCleanProjectAfterSync = DEFAULT_CLEAN_PROJECT_AFTER_SYNC;

    TemplateManager.getInstance().refreshDynamicTemplateMenu(myProject);

    disposeModulesMarkedForRemoval();
  }

  private boolean shouldForcePluginVersionUpgrade() {
    Module module = getAppAndroidModule(myProject);
    if (module != null) {
      AndroidProject androidProject = getAndroidProject(module);
      assert androidProject != null;
      logProjectVersion(androidProject);
      GradleVersion current = GradleVersion.parse(androidProject.getModelVersion());
      String latest = GRADLE_PLUGIN_LATEST_VERSION;
      if (!isUsingExperimentalPlugin(module) && isForcedPluginVersionUpgradeNecessary(current, latest)) {
        updateGradleSyncState(); // Update the sync state before starting a new one.

        boolean update = new PluginVersionForcedUpdateDialog(myProject).showAndGet();
        if (update) {
          updateGradlePluginVersionAndNotifyFailure(myProject, latest, null, true);
          return true;
        }
        else {
          String[] text = {
            "The project is using an incompatible version of the Android Gradle plugin.",
            "Please update your project to use version " + GRADLE_PLUGIN_LATEST_VERSION + "."
          };
          Message msg = new Message(UNHANDLED_SYNC_ISSUE_TYPE, ERROR, text);
          NotificationHyperlink quickFix = new SearchInBuildFilesHyperlink(GRADLE_PLUGIN_NAME);
          ProjectSyncMessages.getInstance(myProject).add(msg, quickFix);
          invalidateLastSync(myProject, "Failed");
          return true;
        }
      }
    }
    else {
      Logger.getInstance(PostProjectSetupTasksExecutor.class).warn("Unable to obtain application's Android Project");
    }
    return false;
  }

  @VisibleForTesting
  static boolean isForcedPluginVersionUpgradeNecessary(@NotNull GradleVersion current, @NotNull String latest) {
    if (current.getPreviewType() != null) {
      // current is a "preview" (alpha, beta, etc.)
      return current.compareTo(latest) < 0;
    }
    return false;
  }

  /**
   * Indicates whether the IDE should recommend the user to upgrade the Android Gradle plugin.
   * @return the current version of the plugin being used if an upgrade recommendation is needed, {@code null} otherwise.
   */
  @Nullable
  private String shouldRecommendPluginVersionUpgrade() {
    if (ApplicationManager.getApplication().isUnitTestMode() || AndroidPlugin.isGuiTestingMode()) {
      return null;
    }
    Module module = getAppAndroidModule(myProject);
    if (module != null) {
      AndroidProject androidProject = getAndroidProject(module);
      assert androidProject != null;
      logProjectVersion(androidProject);
      if (!isUsingExperimentalPlugin(module)) {
        GradleVersion latestInstantRunImprovements = new GradleVersion(2, 1, 0);
        String current = androidProject.getModelVersion();
        if (latestInstantRunImprovements.compareTo(current) > 0) {
          return current;
        }
      }
    }
    return null;
  }

  @Nullable
  private static Module getAppAndroidModule(@NotNull Project project) {
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      AndroidProject androidProject = getAndroidProject(module);
      if (androidProject != null && !androidProject.isLibrary()) {
        return module;
      }
    }
    return null;
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
        if (isAndroidSdk(sdk)) {
          androidSdks.add(sdk);
        }
        continue;
      }

      NativeAndroidProject nativeAndroidProject = getNativeAndroidProject(module);
      if (nativeAndroidProject != null) {
        // Native modules does not need any jdk entry.
        continue;
      }

      Sdk jdk = IdeSdks.getJdk();
      model.setSdk(jdk);
    }

    for (Sdk sdk : androidSdks) {
      refreshLibrariesIn(sdk);
    }

    removeAllModuleCompiledArtifacts(myProject);
  }

  private static void adjustInterModuleDependencies(@NotNull Module module, @NotNull IdeModifiableModelsProvider modelsProvider) {
    // Verifies that inter-module dependencies between Android modules are correctly set. If module A depends on module B, and module B
    // does not contain sources but exposes an AAR as an artifact, the IDE should set the dependency in the 'exploded AAR' instead of trying
    // to find the library in module B. The 'exploded AAR' is in the 'build' folder of module A.
    // See: https://code.google.com/p/android/issues/detail?id=162634
    AndroidProject androidProject = getAndroidProject(module);
    if (androidProject == null) {
      return;
    }

    ModifiableRootModel modifiableModel = modelsProvider.getModifiableRootModel(module);
    for (Module dependency : modifiableModel.getModuleDependencies()) {
      AndroidProject dependencyAndroidProject = getAndroidProject(dependency);
      if (dependencyAndroidProject == null) {
        LibraryDependency backup = getModuleCompiledArtifact(dependency);
        if (backup != null) {
          DependenciesModuleCustomizer.updateLibraryDependency(module, modelsProvider, backup, androidProject);
        }
      }
    }
  }

  // After a sync, the contents of an IDEA SDK does not get refreshed. This is an issue when an IDEA SDK is corrupt (e.g. missing libraries
  // like android.jar) and then it is restored by installing the missing platform from within the IDE (using a "quick fix.") After the
  // automatic project sync (triggered by the SDK restore) the contents of the SDK are not refreshed, and references to Android classes are
  // not found in editors. Removing and adding the libraries effectively refreshes the contents of the IDEA SDK, and references in editors
  // work again.
  private static void refreshLibrariesIn(@NotNull Sdk sdk) {
    VirtualFile[] libraries = sdk.getRootProvider().getFiles(CLASSES);

    SdkModificator sdkModificator = sdk.getSdkModificator();
    sdkModificator.removeRoots(CLASSES);
    sdkModificator.commitChanges();

    sdkModificator = sdk.getSdkModificator();
    for (VirtualFile library : libraries) {
      sdkModificator.addRoot(library, CLASSES);
    }
    sdkModificator.commitChanges();
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

  private void addSdkLinkIfNecessary() {
    ProjectSyncMessages messages = ProjectSyncMessages.getInstance(myProject);

    int sdkErrorCount = messages.getMessageCount(FAILED_TO_SET_UP_SDK);
    if (sdkErrorCount > 0) {
      // If we have errors due to platforms not being installed, we add an extra message that prompts user to open Android SDK manager and
      // install any missing platforms.
      String text = "Open Android SDK Manager and install all missing platforms.";
      Message hint = new Message(FAILED_TO_SET_UP_SDK, Message.Type.INFO, NonNavigatable.INSTANCE, text);
      messages.add(hint, new OpenAndroidSdkManagerHyperlink());
    }
  }

  private static void checkSdkToolsVersion(@NotNull Project project) {
    if (project.isDisposed() || ourNewSdkVersionToolsInfoAlreadyShown) {
      return;
    }

    // Piggy-back off of the SDK update check (which is called from a handful of places) to also see if this is an expired preview build
    checkExpiredPreviewBuild(project);

    File androidHome = IdeSdks.getAndroidSdkPath();
    if (androidHome != null && !VersionCheck.isCompatibleVersion(androidHome)) {
      InstallSdkToolsHyperlink hyperlink = new InstallSdkToolsHyperlink(VersionCheck.MIN_TOOLS_REV);
      String message = "Version " + VersionCheck.MIN_TOOLS_REV + " is available.";
      AndroidGradleNotification.getInstance(project).showBalloon("Android SDK Tools", message, INFORMATION, hyperlink);
      ourNewSdkVersionToolsInfoAlreadyShown = true;
    }
  }

  private static void checkExpiredPreviewBuild(@NotNull Project project) {
    if (project.isDisposed() || ourCheckedExpiration) {
      return;
    }

    String ideVersion = ApplicationInfo.getInstance().getFullVersion();
    if (ideVersion.contains("Preview") || ideVersion.contains("Beta") || ideVersion.contains("RC")) {
      // Expire preview builds two months after their build date (which is going to be roughly six weeks after release; by
      // then will definitely have updated the build
      Calendar expirationDate = (Calendar)ApplicationInfo.getInstance().getBuildDate().clone();
      expirationDate.add(Calendar.MONTH, 2);

      Calendar now = Calendar.getInstance();
      if (now.after(expirationDate)) {
        OpenUrlHyperlink hyperlink = new OpenUrlHyperlink("http://tools.android.com/download/studio/", "Show Available Versions");
        String message =
          String.format("This preview build (%1$s) is old; please update to a newer preview or a stable version.", ideVersion);
        AndroidGradleNotification.getInstance(project).showBalloon("Old Preview Build", message, INFORMATION, hyperlink);
        // If we show an expiration message, don't also show a second balloon regarding available SDKs
        ourNewSdkVersionToolsInfoAlreadyShown = true;
      }
    }
    ourCheckedExpiration = true;
  }

  private static void logProjectVersion(@NotNull AndroidProject androidProject) {
    String message = String.format("Gradle model version: %1$s, latest version for IDE: %2$s",
                                   androidProject.getModelVersion(), GRADLE_PLUGIN_LATEST_VERSION);
    Logger.getInstance(PostProjectSetupTasksExecutor.class).info(message);
  }

  private void ensureValidSdks() {
    boolean checkJdkVersion = true;
    Collection<Sdk> invalidAndroidSdks = Sets.newHashSet();
    ModuleManager moduleManager = ModuleManager.getInstance(myProject);

    for (Module module : moduleManager.getModules()) {
      AndroidFacet androidFacet = AndroidFacet.getInstance(module);
      if (androidFacet != null && androidFacet.getAndroidModel() != null) {
        Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
        if (sdk != null && !invalidAndroidSdks.contains(sdk) && (isMissingAndroidLibrary(sdk) || shouldRemoveAnnotationsJar(sdk))) {
          // First try to recreate SDK; workaround for issue 78072
          AndroidSdkAdditionalData additionalData = getAndroidSdkAdditionalData(sdk);
          AndroidSdkData sdkData = AndroidSdkData.getSdkData(sdk);
          if (additionalData != null && sdkData != null) {
            IAndroidTarget target = additionalData.getBuildTarget(sdkData);
            if (target == null) {
              AndroidSdkHandler sdkHandler = sdkData.getSdkHandler();
              ProgressIndicator logger = new StudioLoggerProgressIndicator(getClass());
              sdkHandler.getSdkManager(logger).loadSynchronously(0, logger, null, null);
              target =
                sdkHandler.getAndroidTargetManager(logger).getTargetFromHashString(additionalData.getBuildTargetHashString(), logger);
            }
            if (target != null) {
              SdkModificator sdkModificator = sdk.getSdkModificator();
              sdkModificator.removeAllRoots();
              for (OrderRoot orderRoot : getLibraryRootsForTarget(target, sdk.getHomePath(), true)) {
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

        AndroidGradleModel androidModel = AndroidGradleModel.get(androidFacet);
        assert androidModel != null;
        if (checkJdkVersion && !hasCorrectJdkVersion(module, androidModel)) {
          // we already displayed the error, no need to check each module.
          checkJdkVersion = false;
        }
      }
    }

    if (!invalidAndroidSdks.isEmpty()) {
      reinstallMissingPlatforms(invalidAndroidSdks);
    }
  }

  private static boolean isMissingAndroidLibrary(@NotNull Sdk sdk) {
    if (isAndroidSdk(sdk)) {
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
  private static boolean shouldRemoveAnnotationsJar(@NotNull Sdk sdk) {
    if (isAndroidSdk(sdk)) {
      AndroidSdkAdditionalData additionalData = getAndroidSdkAdditionalData(sdk);
      AndroidSdkData sdkData = AndroidSdkData.getSdkData(sdk);
      boolean needsAnnotationsJar = false;
      if (additionalData != null && sdkData != null) {
        IAndroidTarget target = additionalData.getBuildTarget(sdkData);
        if (target != null) {
          needsAnnotationsJar = needsAnnotationsJarInClasspath(target);
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
    ProjectSyncMessages messages = ProjectSyncMessages.getInstance(myProject);

    List<AndroidVersion> versionsToInstall = Lists.newArrayList();
    List<String> missingPlatforms = Lists.newArrayList();

    for (Sdk sdk : invalidAndroidSdks) {
      AndroidSdkAdditionalData additionalData = getAndroidSdkAdditionalData(sdk);
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
      Message msg = new Message(group, ERROR, text);
      messages.add(msg, new InstallPlatformHyperlink(versionsToInstall.toArray(new AndroidVersion[versionsToInstall.size()])));
    }
  }

  private void setMakeStepInJunitRunConfigurations(@NotNull String makeTaskName) {
    RunManagerImpl runManager = RunManagerImpl.getInstanceImpl(myProject);
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
        RunnerAndConfigurationSettings template = runManager.getConfigurationTemplate(configurationFactory);
        RunConfiguration runConfiguration = template.getConfiguration();
        setMakeStepInJUnitConfiguration(targetProvider, runConfiguration);
      }

      // Set the correct "Make step" in existing JUnit Configurations.
      RunConfiguration[] junitRunConfigurations = runManager.getConfigurations(junitConfigurationType);
      for (RunConfiguration runConfiguration : junitRunConfigurations) {
        setMakeStepInJUnitConfiguration(targetProvider, runConfiguration);
      }
    }
  }

  private void setMakeStepInJUnitConfiguration(@NotNull BeforeRunTaskProvider targetProvider, @NotNull RunConfiguration runConfiguration) {
    RunManagerImpl runManager = RunManagerImpl.getInstanceImpl(myProject);
    BeforeRunTask task = targetProvider.createTask(runConfiguration);
    if (task != null) {
      task.setEnabled(true);
      runManager.setBeforeRunTasks(runConfiguration, Collections.singletonList(task), false);
    }
  }

  private void updateGradleSyncState() {
    if (!myUsingCachedProjectData) {
      // Notify "sync end" event first, to register the timestamp. Otherwise the cache (GradleProjectSyncData) will store the date of the
      // previous sync, and not the one from the sync that just ended.
      GradleSyncState.getInstance(myProject).syncEnded();
      GradleProjectSyncData.save(myProject);
    }
    else {
      long lastSyncTimestamp = myLastSyncTimestamp;
      if (lastSyncTimestamp == DEFAULT_LAST_SYNC_TIMESTAMP) {
        lastSyncTimestamp = System.currentTimeMillis();
      }
      GradleSyncState.getInstance(myProject).syncSkipped(lastSyncTimestamp);
    }

    // set default value back.
    myUsingCachedProjectData = DEFAULT_USING_CACHED_PROJECT_DATA;
    myLastSyncTimestamp = DEFAULT_LAST_SYNC_TIMESTAMP;
  }

  /**
   * Indicates whether the IDE should generate sources after project sync.
   *
   * @param generateSourcesAfterSync {@code true} if sources should be generated after sync, {@code false otherwise}.
   * @param cleanProjectAfterSync    if {@code true}, the project should be cleaned before generating sources. This value is ignored if
   *                                 {@code generateSourcesAfterSync} is {@code false}.
   */
  public void setGenerateSourcesAfterSync(boolean generateSourcesAfterSync, boolean cleanProjectAfterSync) {
    myGenerateSourcesAfterSync = generateSourcesAfterSync;
    myCleanProjectAfterSync = cleanProjectAfterSync;
  }

  public void setLastSyncTimestamp(long lastSyncTimestamp) {
    myLastSyncTimestamp = lastSyncTimestamp;
  }

  public void setUsingCachedProjectData(boolean usingCachedProjectData) {
    myUsingCachedProjectData = usingCachedProjectData;
  }

  private static class InstallSdkToolsHyperlink extends NotificationHyperlink {
    @NotNull private final Revision myVersion;

    InstallSdkToolsHyperlink(@NotNull Revision version) {
      super("install.build.tools", "Install Tools " + version);
      myVersion = version;
    }

    @Override
    protected void execute(@NotNull Project project) {
      List<String> requested = Lists.newArrayList();
      if (myVersion.getMajor() == 23) {
        Revision minBuildToolsRev = new Revision(20, 0, 0);
        requested.add(DetailsTypes.getBuildToolsPath(minBuildToolsRev));
      }
      requested.add(FD_TOOLS);
      ModelWizardDialog dialog = SdkQuickfixUtils.createDialogForPaths(project, requested);
      if (dialog != null && dialog.showAndGet()) {
        GradleProjectImporter.getInstance().requestProjectSync(project, null);
      }
    }
  }
}
