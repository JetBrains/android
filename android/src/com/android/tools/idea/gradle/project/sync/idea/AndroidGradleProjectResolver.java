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

import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.NativeAndroidProject;
import com.android.builder.model.Variant;
import com.android.builder.model.level2.GlobalLibraryMap;
import com.android.ide.common.repository.GradleVersion;
import com.android.repository.Revision;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.project.model.*;
import com.android.tools.idea.gradle.project.model.ide.android.IdeNativeAndroidProject;
import com.android.tools.idea.gradle.project.model.ide.android.IdeNativeAndroidProjectImpl;
import com.android.tools.idea.gradle.project.model.ide.android.level2.IdeDependenciesFactory;
import com.android.tools.idea.gradle.project.sync.common.CommandLineArgs;
import com.android.tools.idea.gradle.project.sync.common.VariantSelector;
import com.android.tools.idea.gradle.project.sync.idea.data.model.ImportedModule;
import com.android.tools.idea.gradle.project.sync.idea.data.model.ProjectCleanupModel;
import com.android.tools.idea.gradle.util.AndroidGradleSettings;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.sdk.IdeSdks;
import com.google.common.annotations.VisibleForTesting;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure;
import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.util.PathsList;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.gradle.GradleScript;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.BuildScriptClasspathModel;
import org.jetbrains.plugins.gradle.model.ExternalProject;
import org.jetbrains.plugins.gradle.model.ModuleExtendedModel;
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static com.android.SdkConstants.FN_SETTINGS_GRADLE;
import static com.android.SdkConstants.GRADLE_PLUGIN_RECOMMENDED_VERSION;
import static com.android.tools.idea.gradle.project.sync.SimulatedSyncErrors.simulateRegisteredSyncError;
import static com.android.tools.idea.gradle.project.sync.errors.UnsupportedModelVersionErrorHandler.READ_MIGRATION_GUIDE_MSG;
import static com.android.tools.idea.gradle.project.sync.errors.UnsupportedModelVersionErrorHandler.UNSUPPORTED_MODEL_VERSION_ERROR_PREFIX;
import static com.android.tools.idea.gradle.project.sync.idea.GradleModelVersionCheck.getModelVersion;
import static com.android.tools.idea.gradle.project.sync.idea.GradleModelVersionCheck.isSupportedVersion;
import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.*;
import static com.android.tools.idea.gradle.util.AndroidGradleSettings.ANDROID_HOME_JVM_ARG;
import static com.android.tools.idea.gradle.util.FilePaths.toSystemDependentPath;
import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventCategory.GRADLE_SYNC;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.GRADLE_SYNC_FAILURE;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure.UNSUPPORTED_ANDROID_MODEL_VERSION;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.isInProcessMode;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.util.ExceptionUtil.getRootCause;
import static com.intellij.util.PathUtil.getJarPathForClass;
import static org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil.getModuleConfigPath;

/**
 * Imports Android-Gradle projects into IDEA.
 */
@Order(ExternalSystemConstants.UNORDERED)
public class AndroidGradleProjectResolver extends AbstractProjectResolverExtension {
  private static final Key<Boolean> IS_ANDROID_PROJECT_KEY = Key.create("IS_ANDROID_PROJECT_KEY");

  @NotNull private final CommandLineArgs myCommandLineArgs;
  @NotNull private final ProjectImportErrorHandler myErrorHandler;
  @NotNull private final ProjectFinder myProjectFinder;
  @NotNull private final VariantSelector myVariantSelector;
  @NotNull private final IdeNativeAndroidProject.Factory myNativeAndroidProjectFactory;
  @NotNull private final IdeaJavaModuleModelFactory myIdeaJavaModuleModelFactory;
  @NotNull private final IdeDependenciesFactory myDependenciesFactory;

  @SuppressWarnings("unused")
  // This constructor is used by the IDE. This class is an extension point implementation, registered in plugin.xml.
  public AndroidGradleProjectResolver() {
    this(new CommandLineArgs(false /* do not apply Java library plugin */), new ProjectImportErrorHandler(), new ProjectFinder(),
         new VariantSelector(), new IdeNativeAndroidProjectImpl.FactoryImpl(), new IdeaJavaModuleModelFactory(),
         new IdeDependenciesFactory());
  }

  @VisibleForTesting
  AndroidGradleProjectResolver(@NotNull CommandLineArgs commandLineArgs,
                               @NotNull ProjectImportErrorHandler errorHandler,
                               @NotNull ProjectFinder projectFinder,
                               @NotNull VariantSelector variantSelector,
                               @NotNull IdeNativeAndroidProject.Factory nativeAndroidProjectFactory,
                               @NotNull IdeaJavaModuleModelFactory ideaJavaModuleModelFactory,
                               @NotNull IdeDependenciesFactory dependenciesFactory) {
    myCommandLineArgs = commandLineArgs;
    myErrorHandler = errorHandler;
    myProjectFinder = projectFinder;
    myVariantSelector = variantSelector;
    myNativeAndroidProjectFactory = nativeAndroidProjectFactory;
    myIdeaJavaModuleModelFactory = ideaJavaModuleModelFactory;
    myDependenciesFactory = dependenciesFactory;
  }

  @Override
  @NotNull
  public DataNode<ModuleData> createModule(@NotNull IdeaModule gradleModule, @NotNull DataNode<ProjectData> projectDataNode) {
    AndroidProject androidProject = resolverCtx.getExtraProject(gradleModule, AndroidProject.class);
    if (androidProject != null && !isSupportedVersion(androidProject)) {
      AndroidStudioEvent.Builder event = AndroidStudioEvent.newBuilder();
      // @formatter:off
      event.setCategory(GRADLE_SYNC)
           .setKind(GRADLE_SYNC_FAILURE)
           .setGradleSyncFailure(UNSUPPORTED_ANDROID_MODEL_VERSION)
           .setGradleVersion(androidProject.getModelVersion());
      // @formatter:on

      UsageTracker.getInstance().log(event);

      String msg = getUnsupportedModelVersionErrorMsg(getModelVersion(androidProject));
      throw new IllegalStateException(msg);
    }
    if (isAndroidGradleProject()) {
      return doCreateModule(gradleModule, projectDataNode);
    }
    else {
      return nextResolver.createModule(gradleModule, projectDataNode);
    }
  }

  @NotNull
  private DataNode<ModuleData> doCreateModule(@NotNull IdeaModule gradleModule, @NotNull DataNode<ProjectData> projectDataNode) {
    String moduleName = gradleModule.getName();
    if (moduleName == null) {
      throw new IllegalStateException("Module with undefined name detected: " + gradleModule);
    }

    String projectPath = projectDataNode.getData().getLinkedExternalProjectPath();
    String moduleConfigPath = getModuleConfigPath(resolverCtx, gradleModule, projectPath);

    String gradlePath = gradleModule.getGradleProject().getPath();
    String moduleId = isEmpty(gradlePath) || ":".equals(gradlePath) ? moduleName : gradlePath;
    ProjectSystemId owner = GradleConstants.SYSTEM_ID;
    String typeId = StdModuleTypes.JAVA.getId();

    ModuleData moduleData = new ModuleData(moduleId, owner, typeId, moduleName, moduleConfigPath, moduleConfigPath);

    ExternalProject externalProject = resolverCtx.getExtraProject(gradleModule, ExternalProject.class);
    if (externalProject != null) {
      moduleData.setDescription(externalProject.getDescription());
    }
    return projectDataNode.createChild(ProjectKeys.MODULE, moduleData);
  }

  @Override
  public void populateModuleContentRoots(@NotNull IdeaModule gradleModule, @NotNull DataNode<ModuleData> ideModule) {
    if (!IdeInfo.getInstance().isAndroidStudio() && !isAndroidGradleProject()) {
      nextResolver.populateModuleContentRoots(gradleModule, ideModule);
      return;
    }

    ImportedModule importedModule = new ImportedModule(gradleModule);
    ideModule.createChild(IMPORTED_MODULE, importedModule);

    // do not derive module root dir based on *.iml file location
    File moduleRootDirPath = toSystemDependentPath(ideModule.getData().getLinkedExternalProjectPath());

    AndroidProject androidProject = resolverCtx.getExtraProject(gradleModule, AndroidProject.class);

    boolean androidProjectWithoutVariants = false;
    String moduleName = gradleModule.getName();

    if (androidProject != null) {
      Variant selectedVariant = myVariantSelector.findVariantToSelect(androidProject);
      if (selectedVariant == null) {
        // If an Android project does not have variants, it would be impossible to build. This is a possible but invalid use case.
        // For now we are going to treat this case as a Java library module, because everywhere in the IDE (e.g. run configurations,
        // editors, test support, variants tool window, project building, etc.) we have the assumption that there is at least one variant
        // per Android project, and changing that in the code base is too risky, for very little benefit.
        // See https://code.google.com/p/android/issues/detail?id=170722
        androidProjectWithoutVariants = true;
      }
      else {
        String variantName = selectedVariant.getName();
        AndroidModuleModel model =
          new AndroidModuleModel(moduleName, moduleRootDirPath, androidProject, variantName, myDependenciesFactory);
        ideModule.createChild(ANDROID_MODEL, model);
      }
    }

    NativeAndroidProject nativeAndroidProject = resolverCtx.getExtraProject(gradleModule, NativeAndroidProject.class);
    if (nativeAndroidProject != null) {
      IdeNativeAndroidProject copy = myNativeAndroidProjectFactory.create(nativeAndroidProject);
      NdkModuleModel ndkModuleModel = new NdkModuleModel(moduleName, moduleRootDirPath, copy);
      ideModule.createChild(NDK_MODEL, ndkModuleModel);
    }

    File gradleSettingsFile = new File(moduleRootDirPath, FN_SETTINGS_GRADLE);
    if (gradleSettingsFile.isFile() && androidProject == null && nativeAndroidProject == null &&
        // if the module has artifacts, it is a Java library module.
        // https://code.google.com/p/android/issues/detail?id=226802
        !hasArtifacts(gradleModule)) {
      // This is just a root folder for a group of Gradle projects. We don't set an IdeaGradleProject so the JPS builder won't try to
      // compile it using Gradle. We still need to create the module to display files inside it.
      createJavaProject(gradleModule, ideModule, false);
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
    GradleModuleModel gradleModuleModel = new GradleModuleModel(moduleName, gradleProject, buildFilePath, gradleVersion);
    ideModule.createChild(GRADLE_MODULE_MODEL, gradleModuleModel);

    if (nativeAndroidProject == null && (androidProject == null || androidProjectWithoutVariants)) {
      // This is a Java lib module.
      createJavaProject(gradleModule, ideModule, androidProjectWithoutVariants);
    }
  }

  private boolean hasArtifacts(@NotNull IdeaModule gradleModule) {
    ModuleExtendedModel javaModel = resolverCtx.getExtraProject(gradleModule, ModuleExtendedModel.class);
    return javaModel != null && !javaModel.getArtifacts().isEmpty();
  }

  private void createJavaProject(@NotNull IdeaModule gradleModule,
                                 @NotNull DataNode<ModuleData> ideModule,
                                 boolean androidProjectWithoutVariants) {
    ModuleExtendedModel model = resolverCtx.getExtraProject(gradleModule, ModuleExtendedModel.class);
    JavaModuleModel javaModuleModel = myIdeaJavaModuleModelFactory.create(gradleModule, model, androidProjectWithoutVariants);
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

  /**
   * Set map from project path to build directory for all modules.
   * It will be used to check if a {@link AndroidLibrary} is sub-module that wraps local aar.
   */
  private void populateModuleBuildDirs(@NotNull IdeaProject ideaProject) {
    for (IdeaModule ideaModule : ideaProject.getChildren()) {
      GradleProject gradleProject = ideaModule.getGradleProject();
      if (gradleProject != null) {
        myDependenciesFactory.findAndAddBuildFolderPath(gradleProject);
      }
    }
  }

  /**
   * Find and set global library map.
   */
  private void populateGlobalLibraryMap(@NotNull IdeaProject ideaProject) {
    GlobalLibraryMap globalLibraryMap = null;
    // Since GlobalLibraryMap is requested on each module, we need to find the map that was
    // requested at the last, which is the one that contains the most of items.
    for (IdeaModule ideaModule : ideaProject.getChildren()) {
      GlobalLibraryMap moduleMap = resolverCtx.getExtraProject(ideaModule, GlobalLibraryMap.class);
      if (globalLibraryMap == null ||
          (moduleMap != null && moduleMap.getLibraries().size() > globalLibraryMap.getLibraries().size())) {
        globalLibraryMap = moduleMap;
      }
    }
    // GlobalLibraryMap will be null for pre 3.0 plugins, or for 3.0 plugin with VERSION_3 model.
    if (globalLibraryMap != null) {
      myDependenciesFactory.setupGlobalLibraryMap(globalLibraryMap);
    }
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
    return modelClasses;
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
    return Collections.emptyList();
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
  public ExternalSystemException getUserFriendlyError(@NotNull Throwable error,
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
               .setKind(GRADLE_SYNC_FAILURE)
               .setGradleSyncFailure(GradleSyncFailure.UNSUPPORTED_GRADLE_VERSION);
          // @formatter:on;
          UsageTracker.getInstance().log(event);

          return new ExternalSystemException("The project is using an unsupported version of Gradle.");
        }
      }
    }
    ExternalSystemException userFriendlyError = myErrorHandler.getUserFriendlyError(error, projectPath, buildFilePath);
    assert userFriendlyError != null;
    return userFriendlyError;
  }

  private static boolean isUsingUnsupportedGradleVersion(@Nullable String errorMessage) {
    return "org.gradle.api.artifacts.result.ResolvedComponentResult".equals(errorMessage) ||
           "org.gradle.api.artifacts.result.ResolvedModuleVersionResult".equals(errorMessage);
  }

  @NotNull
  private static String getUnsupportedModelVersionErrorMsg(@Nullable GradleVersion modelVersion) {
    StringBuilder builder = new StringBuilder();
    builder.append(UNSUPPORTED_MODEL_VERSION_ERROR_PREFIX);
    String recommendedVersion = String.format("The recommended version is %1$s.", GRADLE_PLUGIN_RECOMMENDED_VERSION);
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

  @Override
  public void enhanceRemoteProcessing(@NotNull SimpleJavaParameters parameters) {
    PathsList classPath = parameters.getClassPath();
    classPath.add(getJarPathForClass(getClass()));
    classPath.add(getJarPathForClass(Revision.class));
    classPath.add(getJarPathForClass(AndroidGradleSettings.class));
    classPath.add(getJarPathForClass(AndroidProject.class));
  }
}
