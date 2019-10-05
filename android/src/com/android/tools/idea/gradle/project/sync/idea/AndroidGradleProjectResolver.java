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
package com.android.tools.idea.gradle.project.sync.idea;

import static com.android.SdkConstants.FN_SETTINGS_GRADLE;
import static com.android.tools.idea.gradle.project.sync.SimulatedSyncErrors.simulateRegisteredSyncError;
import static com.android.tools.idea.gradle.project.sync.errors.GradleDistributionInstallErrorHandler.COULD_NOT_INSTALL_GRADLE_DISTRIBUTION_PATTERN;
import static com.android.tools.idea.gradle.project.sync.errors.UnsupportedModelVersionErrorHandler.READ_MIGRATION_GUIDE_MSG;
import static com.android.tools.idea.gradle.project.sync.errors.UnsupportedModelVersionErrorHandler.UNSUPPORTED_MODEL_VERSION_ERROR_PREFIX;
import static com.android.tools.idea.gradle.project.sync.idea.GradleModelVersionCheck.getModelVersion;
import static com.android.tools.idea.gradle.project.sync.idea.GradleModelVersionCheck.isSupportedVersion;
import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.ANDROID_MODEL;
import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.GRADLE_MODULE_MODEL;
import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.JAVA_MODULE_MODEL;
import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.NDK_MODEL;
import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.PROJECT_CLEANUP_MODEL;
import static com.android.tools.idea.gradle.util.AndroidGradleSettings.ANDROID_HOME_JVM_ARG;
import static com.android.tools.idea.gradle.util.GradleBuilds.BUILD_SRC_FOLDER_NAME;
import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.android.tools.idea.gradle.variant.view.BuildVariantUpdater.MODULE_WITH_BUILD_VARIANT_SWITCHED_FROM_UI;
import static com.android.tools.idea.io.FilePaths.toSystemDependentPath;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventCategory.GRADLE_SYNC;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.GRADLE_SYNC_FAILURE_DETAILS;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure.UNSUPPORTED_ANDROID_MODEL_VERSION;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.isInProcessMode;
import static com.intellij.util.ExceptionUtil.getRootCause;
import static com.intellij.util.PathUtil.getJarPathForClass;
import static java.util.Collections.emptyList;

import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.NativeAndroidProject;
import com.android.builder.model.ProjectSyncIssues;
import com.android.builder.model.SyncIssue;
import com.android.builder.model.Variant;
import com.android.builder.model.level2.GlobalLibraryMap;
import com.android.ide.common.gradle.model.IdeBaseArtifact;
import com.android.ide.common.gradle.model.IdeNativeAndroidProject;
import com.android.ide.common.gradle.model.IdeNativeAndroidProjectImpl;
import com.android.ide.common.gradle.model.IdeNativeVariantAbi;
import com.android.ide.common.gradle.model.level2.IdeDependenciesFactory;
import com.android.ide.common.repository.GradleVersion;
import com.android.ide.gradle.model.GradlePluginModel;
import com.android.ide.gradle.model.sources.SourcesAndJavadocArtifacts;
import com.android.repository.Revision;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.LibraryFilePaths;
import com.android.tools.idea.gradle.plugin.LatestKnownPluginVersionProvider;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.project.model.IdeaJavaModuleModelFactory;
import com.android.tools.idea.gradle.project.model.JavaModuleModel;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.SelectedVariantCollector;
import com.android.tools.idea.gradle.project.sync.SelectedVariants;
import com.android.tools.idea.gradle.project.sync.SyncActionOptions;
import com.android.tools.idea.gradle.project.sync.common.CommandLineArgs;
import com.android.tools.idea.gradle.project.sync.common.VariantSelector;
import com.android.tools.idea.gradle.project.sync.idea.data.model.ProjectCleanupModel;
import com.android.tools.idea.gradle.project.sync.idea.svs.AndroidExtraModelProvider;
import com.android.tools.idea.gradle.project.sync.idea.svs.VariantGroup;
import com.android.tools.idea.gradle.util.AndroidGradleSettings;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.stats.UsageTrackerUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure;
import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.util.PathsList;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipException;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.gradle.GradleScript;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.kapt.idea.KaptGradleModel;
import org.jetbrains.kotlin.kapt.idea.KaptSourceSetModel;
import org.jetbrains.plugins.gradle.model.BuildScriptClasspathModel;
import org.jetbrains.plugins.gradle.model.ModuleExtendedModel;
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider;
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension;

/**
 * Imports Android-Gradle projects into IDEA.
 */
@Order(ExternalSystemConstants.UNORDERED)
public class AndroidGradleProjectResolver extends AbstractProjectResolverExtension {
  private static final Key<Boolean> IS_ANDROID_PROJECT_KEY = Key.create("IS_ANDROID_PROJECT_KEY");
  private static final Logger LOG = Logger.getInstance(AndroidGradleProjectResolver.class);

  @NotNull private final CommandLineArgs myCommandLineArgs;
  @NotNull private final ProjectFinder myProjectFinder;
  @NotNull private final VariantSelector myVariantSelector;
  @NotNull private final IdeNativeAndroidProject.Factory myNativeAndroidProjectFactory;
  @NotNull private final IdeaJavaModuleModelFactory myIdeaJavaModuleModelFactory;
  @NotNull private final IdeDependenciesFactory myDependenciesFactory;
  @NotNull private final ModalityState myModality;

  @SuppressWarnings("unused")
  // This constructor is used by the IDE. This class is an extension point implementation, registered in plugin.xml.
  public AndroidGradleProjectResolver() {
    this(new CommandLineArgs(), new ProjectFinder(), new VariantSelector(), new IdeNativeAndroidProjectImpl.FactoryImpl(),
         new IdeaJavaModuleModelFactory(), new IdeDependenciesFactory());
  }

  @VisibleForTesting
  AndroidGradleProjectResolver(@NotNull CommandLineArgs commandLineArgs,
                               @NotNull ProjectFinder projectFinder,
                               @NotNull VariantSelector variantSelector,
                               @NotNull IdeNativeAndroidProject.Factory nativeAndroidProjectFactory,
                               @NotNull IdeaJavaModuleModelFactory ideaJavaModuleModelFactory,
                               @NotNull IdeDependenciesFactory dependenciesFactory) {
    myCommandLineArgs = commandLineArgs;
    myProjectFinder = projectFinder;
    myVariantSelector = variantSelector;
    myNativeAndroidProjectFactory = nativeAndroidProjectFactory;
    myIdeaJavaModuleModelFactory = ideaJavaModuleModelFactory;
    myDependenciesFactory = dependenciesFactory;
    myModality = ModalityState.defaultModalityState();
  }

  @Override
  @NotNull
  public DataNode<ModuleData> createModule(@NotNull IdeaModule gradleModule, @NotNull DataNode<ProjectData> projectDataNode) {
    AndroidProject androidProject = resolverCtx.getExtraProject(gradleModule, AndroidProject.class);
    if (androidProject != null && !isSupportedVersion(androidProject)) {
      AndroidStudioEvent.Builder event = AndroidStudioEvent.newBuilder();
      // @formatter:off
      event.setCategory(GRADLE_SYNC)
           .setKind(GRADLE_SYNC_FAILURE_DETAILS)
           .setGradleSyncFailure(UNSUPPORTED_ANDROID_MODEL_VERSION)
           .setGradleVersion(androidProject.getModelVersion());
      // @formatter:on
      UsageTrackerUtils.withProjectId(event, myProjectFinder.findProject(resolverCtx));
      UsageTracker.log(event);

      String msg = getUnsupportedModelVersionErrorMsg(getModelVersion(androidProject));
      throw new IllegalStateException(msg);
    }
    return nextResolver.createModule(gradleModule, projectDataNode);
  }

  @Override
  public boolean requiresTaskRunning() {
    Project project = myProjectFinder.findProject(resolverCtx);
    // This tells IDEAs infrastructure to allow AGP to run tasks if we are running compound sync.
    return project != null && shouldGenerateSources(project);
  }

  @Override
  public void buildFinished(@Nullable GradleConnectionException exception) {
    if (exception != null) {
      // We don't actually want to report the errors that are coming from the task running phase of the Gradle process, these will be
      // reporting during a build. This may result in extra red symbols in the IDE however since we can already get in to a state with
      // red symbols (via generated source) this is something that we can accept.
      // This this allows us to make sync only fail if configuration or model building has failed. So configured project iff Setup IDE.
      LOG.info("Exception thrown during task execution", exception);
    }

    Project project = myProjectFinder.findProject(resolverCtx);
    if (project == null) {
      return;
    }

    // We only want to notify the sourceGeneration listeners if source generation has been requested.
    if (!shouldGenerateSources(project)) {
      return;
    }

    // Since this is running in the Gradle connection thread we need to pass back to the UI thread to call the listeners as they may
    // require reading or writing and we want to provide the same context as the other listeners.
    // If we start these from the connection thread deadlocks can occur.
    Runnable runnable = () -> {
      // Since this is run on the UI thread we need to check whether the project has been disposed.
      if (!project.isDisposed()) {
        GradleSyncState.getInstance(project).sourceGenerationFinished();

        GradleSyncListener syncListener = project.getUserData(GradleSyncExecutor.LISTENER_KEY);
        if (syncListener == null) {
          return;
        }

        syncListener.sourceGenerationFinished(project);
      }
    };
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      runnable.run();
    }
    else {
      ApplicationManager.getApplication().invokeAndWait(runnable, myModality);
    }
  }

  private void populateSourcesAndJavadocModel(@NotNull IdeaModule gradleModule) {
    Project project = myProjectFinder.findProject(resolverCtx);
    SourcesAndJavadocArtifacts artifacts = resolverCtx.getExtraProject(gradleModule, SourcesAndJavadocArtifacts.class);
    if (artifacts != null && project != null) {
      LibraryFilePaths.getInstance(project).populate(artifacts);
    }
  }

  @Override
  public void populateModuleContentRoots(@NotNull IdeaModule gradleModule, @NotNull DataNode<ModuleData> ideModule) {
    if (!isAndroidGradleProject()) {
      nextResolver.populateModuleContentRoots(gradleModule, ideModule);
      return;
    }

    populateSourcesAndJavadocModel(gradleModule);

    // do not derive module root dir based on *.iml file location
    File moduleRootDirPath = toSystemDependentPath(ideModule.getData().getLinkedExternalProjectPath());

    AndroidProject androidProject = resolverCtx.getExtraProject(gradleModule, AndroidProject.class);

    // This model was introduced in Android Gradle Plugin 3.6 that contain the sync issues that have been produced by the project,
    // this model is requested last to ensure that all SyncIssues are collected and gathered. This replaces the SyncIssuses that are
    // contained within the AndroidProject. These sync issues are immutable unlike the ones in AndroidProject which can be changed in
    // the plugin after the model has been requested.
    ProjectSyncIssues projectSyncIssues = resolverCtx.getExtraProject(gradleModule, ProjectSyncIssues.class);

    boolean androidProjectWithoutVariants = false;
    // This stores the sync issues that should be attached to a Java module if we have a AndroidProject without variants.
    String moduleName = gradleModule.getName();

    VariantGroup variantGroup = resolverCtx.getExtraProject(gradleModule, VariantGroup.class);

    // This model is used to work out whether Kapt is enabled.
    KaptGradleModel kaptGradleModel = resolverCtx.getExtraProject(gradleModule, KaptGradleModel.class);

    if (androidProject != null) {
      Variant selectedVariant = myVariantSelector.findVariantToSelect(androidProject);
      if (selectedVariant == null && variantGroup != null) {
        List<Variant> variants = variantGroup.getVariants();
        // If we have single variant sync enabled the Variant model comes separately, select the first one.
        // All are added to the AndroidModuleModel below.
        if (!variants.isEmpty()) {
          selectedVariant = variants.get(0);
        }
      }
      if (selectedVariant == null) {
        // If an Android project does not have variants, it would be impossible to build. This is a possible but invalid use case.
        // For now we are going to treat this case as a Java library module, because everywhere in the IDE (e.g. run configurations,
        // editors, test support, variants tool window, project building, etc.) we have the assumption that there is at least one variant
        // per Android project, and changing that in the code base is too risky, for very little benefit.
        // See https://code.google.com/p/android/issues/detail?id=170722
        androidProjectWithoutVariants = true;
      }
      else {
        AndroidModuleModel model =
          AndroidModuleModel.create(moduleName, moduleRootDirPath, androidProject, selectedVariant.getName(), myDependenciesFactory,
                                    (variantGroup == null) ? null : variantGroup.getVariants(), projectSyncIssues);
        populateKaptKotlinGeneratedSourceDir(gradleModule, model);
        ideModule.createChild(ANDROID_MODEL, model);
      }
    }

    NativeAndroidProject nativeAndroidProject = resolverCtx.getExtraProject(gradleModule, NativeAndroidProject.class);
    if (nativeAndroidProject != null) {
      IdeNativeAndroidProject copy = myNativeAndroidProjectFactory.create(nativeAndroidProject);
      List<IdeNativeVariantAbi> ideNativeVariantAbis = new ArrayList<>();
      if (variantGroup != null) {
        ideNativeVariantAbis.addAll(variantGroup.getNativeVariants().stream().map(IdeNativeVariantAbi::new).collect(Collectors.toList()));
      }

      NdkModuleModel ndkModuleModel = new NdkModuleModel(moduleName, moduleRootDirPath, copy, ideNativeVariantAbis);
      ideModule.createChild(NDK_MODEL, ndkModuleModel);
    }

    File gradleSettingsFile = new File(moduleRootDirPath, FN_SETTINGS_GRADLE);
    if (gradleSettingsFile.isFile() && androidProject == null && nativeAndroidProject == null &&
        // if the module has artifacts, it is a Java library module.
        // https://code.google.com/p/android/issues/detail?id=226802
        !hasArtifacts(gradleModule)) {
      // This is just a root folder for a group of Gradle projects. We don't set an IdeaGradleProject so the JPS builder won't try to
      // compile it using Gradle. We still need to create the module to display files inside it.
      createJavaProject(gradleModule, ideModule, emptyList(), false);
      return;
    }

    BuildScriptClasspathModel buildScriptModel = resolverCtx.getExtraProject(BuildScriptClasspathModel.class);
    String gradleVersion = buildScriptModel != null ? buildScriptModel.getGradleVersion() : null;

    GradleProject gradleProject = gradleModule.getGradleProject();
    GradleScript buildScript = null;
    try {
      buildScript = gradleProject.getBuildScript();
    }
    catch (UnsupportedOperationException ignore) {
    }
    File buildFilePath = buildScript != null ? buildScript.getSourceFile() : null;
    // Note: currently getModelVersion() matches the AGP version and it is the only way to get the AGP version.
    // Note: agpVersion is currently not available for Java modules.
    String agpVersion = androidProject != null ? androidProject.getModelVersion() : null;
    GradlePluginModel gradlePluginModel = resolverCtx.getExtraProject(gradleModule, GradlePluginModel.class);
    // We need to make a copy of the Collection since it originates from the Gradle classloader
    List<String> gradlePluginList = new ArrayList<>();
    if (gradlePluginModel != null) {
      gradlePluginList.addAll(gradlePluginModel.getGradlePluginList());
    }
    GradleModuleModel gradleModuleModel =
      new GradleModuleModel(moduleName, gradleProject, gradlePluginList, buildFilePath, gradleVersion, agpVersion, kaptGradleModel);
    ideModule.createChild(GRADLE_MODULE_MODEL, gradleModuleModel);

    if (nativeAndroidProject == null && (androidProject == null || androidProjectWithoutVariants)) {
      // For Java modules we need a list, either extract this from the ProjectSyncIssues or get it from the AndroidProject.
      Collection<SyncIssue> issues = ImmutableList.of();
      if (projectSyncIssues != null) {
        issues = projectSyncIssues.getSyncIssues();
      }
      else if (androidProject != null) {
        issues = androidProject.getSyncIssues();
      }

      // This is a Java lib module.
      createJavaProject(gradleModule, ideModule, issues, androidProjectWithoutVariants);
      // Populate ContentRootDataNode for buildSrc module. This DataNode is required to setup classpath buildscript.
      if (BUILD_SRC_FOLDER_NAME.equals(gradleModule.getGradleProject().getName())) {
        nextResolver.populateModuleContentRoots(gradleModule, ideModule);
      }
    }
  }

  private boolean hasArtifacts(@NotNull IdeaModule gradleModule) {
    ModuleExtendedModel javaModel = resolverCtx.getExtraProject(gradleModule, ModuleExtendedModel.class);
    return javaModel != null && !javaModel.getArtifacts().isEmpty();
  }

  private void createJavaProject(@NotNull IdeaModule gradleModule,
                                 @NotNull DataNode<ModuleData> ideModule,
                                 @NotNull Collection<SyncIssue> syncIssues,
                                 boolean androidProjectWithoutVariants) {
    ModuleExtendedModel model = resolverCtx.getExtraProject(gradleModule, ModuleExtendedModel.class);
    JavaModuleModel javaModuleModel = myIdeaJavaModuleModelFactory.create(gradleModule, syncIssues, model, androidProjectWithoutVariants);
    ideModule.createChild(JAVA_MODULE_MODEL, javaModuleModel);
  }

  @Override
  public void populateModuleCompileOutputSettings(@NotNull IdeaModule gradleModule, @NotNull DataNode<ModuleData> ideModule) {
    if (!isAndroidGradleProject()) {
      nextResolver.populateModuleCompileOutputSettings(gradleModule, ideModule);
    }
  }

  @Override
  public void populateModuleDependencies(@NotNull IdeaModule gradleModule,
                                         @NotNull DataNode<ModuleData> ideModule,
                                         @NotNull DataNode<ProjectData> ideProject) {
    if (!isAndroidGradleProject()) {
      // For plain Java projects (non-Gradle) we let the framework populate dependencies
      nextResolver.populateModuleDependencies(gradleModule, ideModule, ideProject);
    }
  }

  // Indicates it is an "Android" project if at least one module has an AndroidProject.
  private boolean isAndroidGradleProject() {
    Boolean isAndroidGradleProject = resolverCtx.getUserData(IS_ANDROID_PROJECT_KEY);
    if (isAndroidGradleProject != null) {
      return isAndroidGradleProject;
    }
    isAndroidGradleProject = resolverCtx.hasModulesWithModel(AndroidProject.class) ||
                             resolverCtx.hasModulesWithModel(NativeAndroidProject.class);
    return resolverCtx.putUserDataIfAbsent(IS_ANDROID_PROJECT_KEY, isAndroidGradleProject);
  }

  @Override
  public void populateProjectExtraModels(@NotNull IdeaProject gradleProject, @NotNull DataNode<ProjectData> projectDataNode) {
    populateModuleBuildDirs(gradleProject);
    populateGlobalLibraryMap(gradleProject);
    if (isAndroidGradleProject()) {
      projectDataNode.createChild(PROJECT_CLEANUP_MODEL, ProjectCleanupModel.getInstance());
    }
    super.populateProjectExtraModels(gradleProject, projectDataNode);
  }

  private void populateKaptKotlinGeneratedSourceDir(@NotNull IdeaModule gradleModule, @NotNull AndroidModuleModel androidModuleModel) {
    KaptGradleModel kaptGradleModel = resolverCtx.getExtraProject(gradleModule, KaptGradleModel.class);
    if (kaptGradleModel == null || !kaptGradleModel.isEnabled()) {
      return;
    }

    for (KaptSourceSetModel sourceSetModel : kaptGradleModel.getSourceSets()) {
      Variant variant = androidModuleModel.findVariantByName(sourceSetModel.getSourceSetName());
      File kotlinGenSourceDir = sourceSetModel.getGeneratedKotlinSourcesDirFile();
      if (variant != null && kotlinGenSourceDir != null) {
        AndroidArtifact mainArtifact = variant.getMainArtifact();
        if (mainArtifact instanceof IdeBaseArtifact) {
          ((IdeBaseArtifact)mainArtifact).addGeneratedSourceFolder(kotlinGenSourceDir);
        }
      }
    }
  }

  /**
   * Set map from project path to build directory for all modules.
   * It will be used to check if a {@link AndroidLibrary} is sub-module that wraps local aar.
   */
  private void populateModuleBuildDirs(@NotNull IdeaProject rootIdeaProject) {
    // Set root build id.
    for (IdeaModule ideaModule : rootIdeaProject.getChildren()) {
      GradleProject gradleProject = ideaModule.getGradleProject();
      if (gradleProject != null) {
        String rootBuildId = gradleProject.getProjectIdentifier().getBuildIdentifier().getRootDir().getPath();
        myDependenciesFactory.setRootBuildId(rootBuildId);
        break;
      }
    }

    // Set build folder for root and included projects.
    List<IdeaProject> ideaProjects = new ArrayList<>();
    ideaProjects.add(rootIdeaProject);
    ideaProjects.addAll(resolverCtx.getModels().getIncludedBuilds());
    for (IdeaProject ideaProject : ideaProjects) {
      for (IdeaModule ideaModule : ideaProject.getChildren()) {
        GradleProject gradleProject = ideaModule.getGradleProject();
        if (gradleProject != null) {
          try {
            String buildId = gradleProject.getProjectIdentifier().getBuildIdentifier().getRootDir().getPath();
            myDependenciesFactory.findAndAddBuildFolderPath(buildId, gradleProject.getPath(), gradleProject.getBuildDirectory());
          }
          catch (UnsupportedOperationException exception) {
            // getBuildDirectory is not available for Gradle older than 2.0.
            // For older versions of gradle, there's no way to get build directory.
          }
        }
      }
    }
  }

  /**
   * Find and set global library map.
   */
  private void populateGlobalLibraryMap(@NotNull IdeaProject rootIdeaProject) {
    List<GlobalLibraryMap> globalLibraryMaps = new ArrayList<>();

    // Request GlobalLibraryMap for root and included projects.
    List<IdeaProject> ideaProjects = new ArrayList<>();
    ideaProjects.add(rootIdeaProject);
    ideaProjects.addAll(resolverCtx.getModels().getIncludedBuilds());

    for (IdeaProject ideaProject : ideaProjects) {
      GlobalLibraryMap mapOfCurrentBuild = null;
      // Since GlobalLibraryMap is requested on each module, we need to find the map that was
      // requested at the last, which is the one that contains the most of items.
      for (IdeaModule ideaModule : ideaProject.getChildren()) {
        GlobalLibraryMap moduleMap = resolverCtx.getExtraProject(ideaModule, GlobalLibraryMap.class);
        if (mapOfCurrentBuild == null ||
            (moduleMap != null && moduleMap.getLibraries().size() > mapOfCurrentBuild.getLibraries().size())) {
          mapOfCurrentBuild = moduleMap;
        }
      }
      if (mapOfCurrentBuild != null) {
        globalLibraryMaps.add(mapOfCurrentBuild);
      }
    }
    myDependenciesFactory.setUpGlobalLibraryMap(globalLibraryMaps);
  }

  @Override
  @NotNull
  public Set<Class> getExtraProjectModelClasses() {
    // Use LinkedHashSet to maintain insertion order.
    // GlobalLibraryMap should be requested after AndroidProject.
    Set<Class> modelClasses = new LinkedHashSet<>();
    modelClasses.add(AndroidProject.class);
    modelClasses.add(NativeAndroidProject.class);
    modelClasses.add(GlobalLibraryMap.class);
    modelClasses.add(GradlePluginModel.class);
    modelClasses.add(ProjectSyncIssues.class);
    return modelClasses;
  }

  @NotNull
  @Override
  public ProjectImportModelProvider getModelProvider() {
    return configureAndGetExtraModelProvider();
  }

  @Override
  public void preImportCheck() {
    simulateRegisteredSyncError();
  }

  @Override
  @NotNull
  public List<Pair<String, String>> getExtraJvmArgs() {
    if (isInProcessMode(GRADLE_SYSTEM_ID)) {
      List<Pair<String, String>> args = new ArrayList<>();

      if (!IdeInfo.getInstance().isAndroidStudio()) {
        LocalProperties localProperties = getLocalProperties();
        if (localProperties.getAndroidSdkPath() == null) {
          File androidHomePath = IdeSdks.getInstance().getAndroidSdkPath();
          // In Android Studio, the Android SDK home path will never be null. It may be null when running in IDEA.
          if (androidHomePath != null) {
            args.add(Pair.create(ANDROID_HOME_JVM_ARG, androidHomePath.getPath()));
          }
        }
      }
      return args;
    }
    return emptyList();
  }

  @NotNull
  private LocalProperties getLocalProperties() {
    File projectDir = toSystemDependentPath(resolverCtx.getProjectPath());
    try {
      return new LocalProperties(projectDir);
    }
    catch (IOException e) {
      String msg = String.format("Unable to read local.properties file in project '%1$s'", projectDir.getPath());
      throw new ExternalSystemException(msg, e);
    }
  }

  @Override
  @NotNull
  public List<String> getExtraCommandLineArgs() {
    Project project = myProjectFinder.findProject(resolverCtx);
    return myCommandLineArgs.get(project);
  }

  @NotNull
  @Override
  public ExternalSystemException getUserFriendlyError(@Nullable BuildEnvironment buildEnvironment,
                                                      @NotNull Throwable error,
                                                      @NotNull String projectPath,
                                                      @Nullable String buildFilePath) {
    String msg = error.getMessage();
    if (msg != null && !msg.contains(UNSUPPORTED_MODEL_VERSION_ERROR_PREFIX)) {
      Throwable rootCause = getRootCause(error);
      if (rootCause instanceof ClassNotFoundException) {
        msg = rootCause.getMessage();
        // Project is using an old version of Gradle (and most likely an old version of the plug-in.)
        if (isUsingUnsupportedGradleVersion(msg)) {
          AndroidStudioEvent.Builder event = AndroidStudioEvent.newBuilder();
          // @formatter:off
          event.setCategory(GRADLE_SYNC)
               .setKind(GRADLE_SYNC_FAILURE_DETAILS)
               .setGradleSyncFailure(GradleSyncFailure.UNSUPPORTED_GRADLE_VERSION);
          // @formatter:on;
          UsageTrackerUtils.withProjectId(event, myProjectFinder.findProject(resolverCtx));
          UsageTracker.log(event);

          return new ExternalSystemException("The project is using an unsupported version of Gradle.");
        }
      }
      else if (rootCause instanceof ZipException) {
        if (COULD_NOT_INSTALL_GRADLE_DISTRIBUTION_PATTERN.matcher(msg).matches()) {
          return new ExternalSystemException(msg);
        }
      }
    }
    return super.getUserFriendlyError(buildEnvironment, error, projectPath, buildFilePath);
  }

  private static boolean isUsingUnsupportedGradleVersion(@Nullable String errorMessage) {
    return "org.gradle.api.artifacts.result.ResolvedComponentResult".equals(errorMessage) ||
           "org.gradle.api.artifacts.result.ResolvedModuleVersionResult".equals(errorMessage);
  }

  @NotNull
  private static String getUnsupportedModelVersionErrorMsg(@Nullable GradleVersion modelVersion) {
    StringBuilder builder = new StringBuilder();
    builder.append(UNSUPPORTED_MODEL_VERSION_ERROR_PREFIX);
    String recommendedVersion = String.format("The recommended version is %1$s.", LatestKnownPluginVersionProvider.INSTANCE.get());
    if (modelVersion != null) {
      builder.append(String.format(" (%1$s).", modelVersion.toString())).append(" ").append(recommendedVersion);
      if (modelVersion.getMajor() == 0 && modelVersion.getMinor() <= 8) {
        // @formatter:off
        builder.append("\n\nStarting with version 0.9.0 incompatible changes were introduced in the build language.\n")
               .append(READ_MIGRATION_GUIDE_MSG)
               .append(" to learn how to update your project.");
        // @formatter:on
      }
    }
    else {
      builder.append(". ").append(recommendedVersion);
    }
    return builder.toString();
  }

  @NotNull
  private AndroidExtraModelProvider configureAndGetExtraModelProvider() {
    // Here we set up the options for the sync and pass them to the AndroidExtraModelProvider which will decide which will use them
    // to decide which models to request from Gradle.
    Project project = myProjectFinder.findProject(resolverCtx);
    SelectedVariants selectedVariants = null;
    boolean isSingleVariantSync = false;
    boolean shouldGenerateSources = false;
    Collection<String> cachedSourcesAndJavadoc = null;
    String moduleWithVariantSwitched = null;

    if (project != null) {
      isSingleVariantSync = shouldOnlySyncSingleVariant(project);
      shouldGenerateSources = shouldGenerateSources(project);
      if (isSingleVariantSync) {
        SelectedVariantCollector variantCollector = new SelectedVariantCollector(project);
        selectedVariants = variantCollector.collectSelectedVariants();
        moduleWithVariantSwitched = project.getUserData(MODULE_WITH_BUILD_VARIANT_SWITCHED_FROM_UI);
        project.putUserData(MODULE_WITH_BUILD_VARIANT_SWITCHED_FROM_UI, null);
      }
      cachedSourcesAndJavadoc = LibraryFilePaths.getInstance(project).retrieveCachedLibs();
    }

    SyncActionOptions options = new SyncActionOptions();
    options.setModuleIdWithVariantSwitched(moduleWithVariantSwitched);
    options.setSingleVariantSyncEnabled(isSingleVariantSync);
    options.setShouldGenerateSources(shouldGenerateSources);
    options.setSelectedVariants(selectedVariants);
    options.setCachedSourcesAndJavadoc(cachedSourcesAndJavadoc);
    return new AndroidExtraModelProvider(options);
  }

  private static boolean shouldGenerateSources(@NotNull Project project) {
    Boolean generateSourcesRequested = project.getUserData(GradleSyncExecutor.SOURCE_GENERATION_KEY);
    return generateSourcesRequested != null && generateSourcesRequested;
  }

  private static boolean shouldOnlySyncSingleVariant(@NotNull Project project) {
    Boolean shouldOnlySyncSingleVariant = project.getUserData(GradleSyncExecutor.SINGLE_VARIANT_KEY);
    return shouldOnlySyncSingleVariant != null && shouldOnlySyncSingleVariant;
  }

  @Override
  public void enhanceRemoteProcessing(@NotNull SimpleJavaParameters parameters) {
    PathsList classPath = parameters.getClassPath();
    classPath.add(getJarPathForClass(getClass()));
    classPath.add(getJarPathForClass(Revision.class));
    classPath.add(getJarPathForClass(AndroidGradleSettings.class));
    classPath.add(getJarPathForClass(AndroidProject.class));
  }
}
