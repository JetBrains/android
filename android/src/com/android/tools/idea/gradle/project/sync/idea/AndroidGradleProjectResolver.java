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

import static com.android.SdkConstants.GRADLE_PLUGIN_MINIMUM_VERSION;
import static com.android.tools.idea.flags.StudioFlags.DISABLE_FORCED_UPGRADES;
import static com.android.tools.idea.gradle.project.sync.Modules.createUniqueModuleId;
import static com.android.tools.idea.gradle.project.sync.SimulatedSyncErrors.simulateRegisteredSyncError;
import static com.android.tools.idea.gradle.project.sync.errors.GradleDistributionInstallIssueCheckerKt.COULD_NOT_INSTALL_GRADLE_DISTRIBUTION_PREFIX;
import static com.android.tools.idea.gradle.project.sync.errors.UnsupportedModelVersionIssueCheckerKt.READ_MIGRATION_GUIDE_MSG;
import static com.android.tools.idea.gradle.project.sync.errors.UnsupportedModelVersionIssueCheckerKt.UNSUPPORTED_MODEL_VERSION_ERROR_PREFIX;
import static com.android.tools.idea.gradle.project.sync.idea.SdkSyncUtil.syncAndroidSdks;
import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.ANDROID_MODEL;
import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.GRADLE_MODULE_MODEL;
import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.JAVA_MODULE_MODEL;
import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.NATIVE_VARIANTS;
import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.NDK_MODEL;
import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.PROJECT_CLEANUP_MODEL;
import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.SYNC_ISSUE;
import static com.android.tools.idea.gradle.project.sync.idea.issues.GradleWrapperImportCheck.validateGradleWrapper;
import static com.android.tools.idea.gradle.project.sync.idea.svs.IdeAndroidModelsKt.ideAndroidSyncErrorToException;
import static com.android.tools.idea.gradle.project.upgrade.GradlePluginUpgrade.displayForceUpdatesDisabledMessage;
import static com.android.tools.idea.gradle.project.upgrade.GradlePluginUpgrade.expireProjectUpgradeNotifications;
import static com.android.tools.idea.gradle.util.AndroidGradleSettings.ANDROID_HOME_JVM_ARG;
import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.android.tools.idea.io.FilePaths.toSystemDependentPath;
import static com.android.utils.BuildScriptUtil.findGradleSettingsFile;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventCategory.GRADLE_SYNC;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.GRADLE_SYNC_FAILURE_DETAILS;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure.UNSUPPORTED_ANDROID_MODEL_VERSION;
import static com.intellij.openapi.externalSystem.model.ProjectKeys.LIBRARY_DEPENDENCY;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.find;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.findAll;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.isInProcessMode;
import static com.intellij.util.ExceptionUtil.getRootCause;
import static com.intellij.util.PathUtil.getJarPathForClass;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil.getModuleId;

import com.android.ide.common.repository.GradleVersion;
import com.android.ide.gradle.model.GradlePluginModel;
import com.android.ide.gradle.model.artifacts.AdditionalClassifierArtifacts;
import com.android.ide.gradle.model.artifacts.AdditionalClassifierArtifactsModel;
import com.android.repository.Revision;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.LibraryFilePaths;
import com.android.tools.idea.gradle.LibraryFilePaths.ArtifactPaths;
import com.android.tools.idea.gradle.model.IdeAndroidProject;
import com.android.tools.idea.gradle.model.IdeBaseArtifact;
import com.android.tools.idea.gradle.model.IdeVariant;
import com.android.tools.idea.gradle.model.ndk.v1.IdeNativeVariantAbi;
import com.android.tools.idea.gradle.plugin.LatestKnownPluginVersionProvider;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.project.model.IdeaJavaModuleModelFactory;
import com.android.tools.idea.gradle.project.model.JavaModuleModel;
import com.android.tools.idea.gradle.project.model.NdkModel;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.project.model.V1NdkModel;
import com.android.tools.idea.gradle.project.model.V2NdkModel;
import com.android.tools.idea.gradle.project.sync.AdditionalClassifierArtifactsActionOptions;
import com.android.tools.idea.gradle.project.sync.FullSyncActionOptions;
import com.android.tools.idea.gradle.project.sync.GradleSyncStudioFlags;
import com.android.tools.idea.gradle.project.sync.NativeVariantsSyncActionOptions;
import com.android.tools.idea.gradle.project.sync.SdkSync;
import com.android.tools.idea.gradle.project.sync.SelectedVariantCollector;
import com.android.tools.idea.gradle.project.sync.SelectedVariants;
import com.android.tools.idea.gradle.project.sync.SingleVariantSyncActionOptions;
import com.android.tools.idea.gradle.project.sync.SyncActionOptions;
import com.android.tools.idea.gradle.project.sync.common.CommandLineArgs;
import com.android.tools.idea.gradle.project.sync.idea.data.model.ProjectCleanupModel;
import com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys;
import com.android.tools.idea.gradle.project.sync.idea.issues.AgpUpgradeRequiredException;
import com.android.tools.idea.gradle.project.sync.idea.issues.JdkImportCheck;
import com.android.tools.idea.gradle.project.sync.idea.svs.AndroidExtraModelProvider;
import com.android.tools.idea.gradle.project.sync.idea.svs.CachedVariants;
import com.android.tools.idea.gradle.project.sync.idea.svs.IdeAndroidModels;
import com.android.tools.idea.gradle.project.sync.idea.svs.IdeAndroidNativeVariantsModels;
import com.android.tools.idea.gradle.project.sync.idea.svs.IdeAndroidSyncError;
import com.android.tools.idea.gradle.project.sync.issues.SyncIssueData;
import com.android.tools.idea.gradle.project.upgrade.GradlePluginUpgrade;
import com.android.tools.idea.gradle.util.AndroidGradleSettings;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.stats.UsageTrackerUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure;
import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.externalSystem.JavaModuleData;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationsConfiguration;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.LibraryData;
import com.intellij.openapi.externalSystem.model.project.LibraryDependencyData;
import com.intellij.openapi.externalSystem.model.project.LibraryLevel;
import com.intellij.openapi.externalSystem.model.project.LibraryPathType;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ModuleDependencyData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.serviceContainer.NonInjectable;
import com.intellij.util.PathsList;
import com.intellij.util.containers.ContainerUtil;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipException;
import kotlin.Unit;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.kapt.idea.KaptGradleModel;
import org.jetbrains.kotlin.kapt.idea.KaptModelBuilderService;
import org.jetbrains.kotlin.kapt.idea.KaptSourceSetModel;
import org.jetbrains.plugins.gradle.model.BuildScriptClasspathModel;
import org.jetbrains.plugins.gradle.model.ExternalProject;
import org.jetbrains.plugins.gradle.model.ExternalSourceSet;
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider;
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData;
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension;
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil;
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;
import org.jetbrains.plugins.gradle.settings.GradleExecutionWorkspace;

/**
 * Imports Android-Gradle projects into IDEA.
 */
@Order(ExternalSystemConstants.UNORDERED)
public final class AndroidGradleProjectResolver extends AbstractProjectResolverExtension {
  public static final GradleVersion MINIMUM_SUPPORTED_VERSION = GradleVersion.parse(GRADLE_PLUGIN_MINIMUM_VERSION);
  public static final String BUILD_SYNC_ORPHAN_MODULES_NOTIFICATION_GROUP_NAME = "Build sync orphan modules";
  @NotNull public static final Key<String> MODULE_WITH_BUILD_VARIANT_SWITCHED_FROM_UI =
    new Key<>("module.with.build.variant.switched.from.ui");
  @NotNull public static final Key<Boolean> USE_VARIANTS_FROM_PREVIOUS_GRADLE_SYNCS =
    new Key<>("use.variants.from.previous.gradle.syncs");
  public static final Key<Boolean> REFRESH_EXTERNAL_NATIVE_MODELS_KEY = Key.create("refresh.external.native.models");
  private static final Key<Boolean> IS_ANDROID_PROJECT_KEY = Key.create("IS_ANDROID_PROJECT_KEY");
  // For variant switching we need to store the Kapt model with all the source set information as we only setup one
  // variant at a time
  public static final Key<KaptGradleModel> KAPT_GRADLE_MODEL_KEY = Key.create("KAPT_GRADLE_MODEL_KEY");

  private static final Key<Boolean> IS_ANDROID_PLUGIN_REQUESTING_KOTLIN_GRADLE_MODEL_KEY =
    Key.create("IS_ANDROID_PLUGIN_REQUESTING_KOTLIN_GRADLE_MODEL_KEY");
  public static final Key<ProjectResolutionMode> REQUESTED_PROJECT_RESOLUTION_MODE_KEY = Key.create("REQUESTED_PROJECT_RESOLUTION_MODE");
  static final Logger RESOLVER_LOG = Logger.getInstance(AndroidGradleProjectResolver.class);

  @NotNull private final CommandLineArgs myCommandLineArgs;
  @NotNull private final ProjectFinder myProjectFinder;
  @NotNull private final IdeaJavaModuleModelFactory myIdeaJavaModuleModelFactory;
  private boolean myShouldExportDependencies;

  public AndroidGradleProjectResolver() {
    this(new CommandLineArgs(), new ProjectFinder(), new IdeaJavaModuleModelFactory());
  }

  @NonInjectable
  @VisibleForTesting
  AndroidGradleProjectResolver(@NotNull CommandLineArgs commandLineArgs,
                               @NotNull ProjectFinder projectFinder,
                               @NotNull IdeaJavaModuleModelFactory ideaJavaModuleModelFactory) {
    myCommandLineArgs = commandLineArgs;
    myProjectFinder = projectFinder;
    myIdeaJavaModuleModelFactory = ideaJavaModuleModelFactory;
  }

  @Override
  public void setProjectResolverContext(@NotNull ProjectResolverContext projectResolverContext) {
    // Setting this flag on the `projectResolverContext` tells the Kotlin IDE plugin that we are requesting `KotlinGradleModel` for all
    // modules. This is to be able to provide additional arguments to the model builder and avoid unnecessary processing of currently the
    // inactive build variants.
    projectResolverContext.putUserData(IS_ANDROID_PLUGIN_REQUESTING_KOTLIN_GRADLE_MODEL_KEY, true);
    super.setProjectResolverContext(projectResolverContext);
  }

  @Override
  @Nullable
  public DataNode<ModuleData> createModule(@NotNull IdeaModule gradleModule, @NotNull DataNode<ProjectData> projectDataNode) {
    if (!isAndroidGradleProject()) {
      return nextResolver.createModule(gradleModule, projectDataNode);
    }

    IdeAndroidModels androidModels = resolverCtx.getExtraProject(gradleModule, IdeAndroidModels.class);
    if (androidModels != null) {
      String modelVersionString = androidModels.getAndroidProject().getModelVersion();
      validateModelVersion(modelVersionString);
    }

    DataNode<ModuleData> moduleDataNode = nextResolver.createModule(gradleModule, projectDataNode);
    if (moduleDataNode == null) {
      return null;
    }

    createAndAttachModelsToDataNode(projectDataNode, moduleDataNode, gradleModule, androidModels);
    patchLanguageLevels(moduleDataNode, gradleModule, androidModels != null ? androidModels.getAndroidProject() : null);

    return moduleDataNode;
  }

  private void patchLanguageLevels(DataNode<ModuleData> moduleDataNode,
                                   @NotNull IdeaModule gradleModule,
                                   @Nullable IdeAndroidProject androidProject) {
    DataNode<JavaModuleData> javaModuleData = find(moduleDataNode, JavaModuleData.KEY);
    if (javaModuleData == null) {
      return;
    }
    JavaModuleData moduleData = javaModuleData.getData();
    if (androidProject != null) {
      LanguageLevel languageLevel = LanguageLevel.parse(androidProject.getJavaCompileOptions().getSourceCompatibility());
      moduleData.setLanguageLevel(languageLevel);
      moduleData.setTargetBytecodeVersion(androidProject.getJavaCompileOptions().getTargetCompatibility());
    }
    else {
      // Workaround BaseGradleProjectResolverExtension since the IdeaJavaLanguageSettings doesn't contain any information.
      // For this we set the language level based on the "main" source set of the module.
      // TODO: Remove once we have switched to module per source set. The base resolver should handle that correctly.
      ExternalProject externalProject = resolverCtx.getExtraProject(gradleModule, ExternalProject.class);
      if (externalProject != null) {
        // main should always exist, if it doesn't other things will fail before this.
        ExternalSourceSet externalSourceSet = externalProject.getSourceSets().get("main");
        if (externalSourceSet != null) {
          LanguageLevel languageLevel = LanguageLevel.parse(externalSourceSet.getSourceCompatibility());
          moduleData.setLanguageLevel(languageLevel);
          moduleData.setTargetBytecodeVersion(externalSourceSet.getTargetCompatibility());
        }
      }
    }
  }

  private void validateModelVersion(@NotNull String modelVersionString) {
    GradleVersion modelVersion = !isNullOrEmpty(modelVersionString)
                                 ? GradleVersion.tryParseAndroidGradlePluginVersion(modelVersionString)
                                 : null;
    boolean result = modelVersion != null && modelVersion.compareTo(MINIMUM_SUPPORTED_VERSION) >= 0;
    if (!result) {
      AndroidStudioEvent.Builder event = AndroidStudioEvent.newBuilder();
      // @formatter:off
      event.setCategory(GRADLE_SYNC)
           .setKind(GRADLE_SYNC_FAILURE_DETAILS)
           .setGradleSyncFailure(UNSUPPORTED_ANDROID_MODEL_VERSION)
           .setGradleVersion(modelVersionString);
      // @formatter:on
      UsageTrackerUtils.withProjectId(event, myProjectFinder.findProject(resolverCtx));
      UsageTracker.log(event);

      String msg = getUnsupportedModelVersionErrorMsg(modelVersion);
      throw new IllegalStateException(msg);
    }
    Project project = myProjectFinder.findProject(resolverCtx);

    // Before anything, check to see if what we have is compatible with this version of studio.
    GradleVersion currentAgpVersion = GradleVersion.tryParse(modelVersionString);
    GradleVersion latestVersion = GradleVersion.parse(LatestKnownPluginVersionProvider.INSTANCE.get());
    if (currentAgpVersion != null && GradlePluginUpgrade.shouldForcePluginUpgrade(project, currentAgpVersion, latestVersion)) {
      throw new AgpUpgradeRequiredException(project, currentAgpVersion);
    }
  }

  @Override
  public void populateModuleCompileOutputSettings(@NotNull IdeaModule gradleModule,
                                                  @NotNull DataNode<ModuleData> ideModule) {
    super.populateModuleCompileOutputSettings(gradleModule, ideModule);
    CompilerOutputUtilKt.setupCompilerOutputPaths(ideModule);
  }

  @Override
  public @NotNull Set<Class<?>> getToolingExtensionsClasses() {
    return ImmutableSet.of(KaptModelBuilderService.class, Unit.class);
  }

  /**
   * Creates and attaches the following models to the moduleNode depending on the type of module:
   * <ul>
   *   <li>AndroidModuleModel</li>
   *   <li>NdkModuleModel</li>
   *   <li>GradleModuleModel</li>
   *   <li>JavaModuleModel</li>
   * </ul>
   *
   * @param moduleNode    the module node to attach the models to
   * @param gradleModule  the module in question
   * @param androidModels the android project models obtained from this module (null is none found)
   */
  private void createAndAttachModelsToDataNode(@NotNull DataNode<ProjectData> projectDataNode,
                                               @NotNull DataNode<ModuleData> moduleNode,
                                               @NotNull IdeaModule gradleModule,
                                               @Nullable IdeAndroidModels androidModels) {
    String moduleName = moduleNode.getData().getInternalName();
    File rootModulePath = toSystemDependentPath(moduleNode.getData().getLinkedExternalProjectPath());
    @NotNull CachedVariants cachedVariants = findCachedVariants(gradleModule);

    ExternalProject externalProject = resolverCtx.getExtraProject(gradleModule, ExternalProject.class);
    KaptGradleModel kaptGradleModel = resolverCtx.getExtraProject(gradleModule, KaptGradleModel.class);
    GradlePluginModel gradlePluginModel = resolverCtx.getExtraProject(gradleModule, GradlePluginModel.class);
    BuildScriptClasspathModel buildScriptClasspathModel = resolverCtx.getExtraProject(gradleModule, BuildScriptClasspathModel.class);

    AndroidModuleModel androidModel = null;
    JavaModuleModel javaModuleModel = null;
    NdkModuleModel ndkModuleModel = null;
    GradleModuleModel gradleModel = null;
    Collection<SyncIssueData> issueData = null;

    if (androidModels != null) {
      androidModel = createAndroidModuleModel(moduleName, rootModulePath, androidModels, cachedVariants);
      issueData = androidModels.getSyncIssues();
      ndkModuleModel = maybeCreateNdkModuleModel(moduleName, rootModulePath, androidModels, cachedVariants);

      // Set whether or not we have seen an old (pre 3.0) version of the AndroidProject. If we have seen one
      // Then we require all Java modules to export their dependencies.
      myShouldExportDependencies |= androidModel.getFeatures().shouldExportDependencies();
    }

    Collection<String> gradlePluginList = (gradlePluginModel == null) ? ImmutableList.of() : gradlePluginModel.getGradlePluginList();
    File gradleSettingsFile = findGradleSettingsFile(rootModulePath);
    boolean hasArtifactsOrNoRootSettingsFile = !(gradleSettingsFile.isFile() && !hasArtifacts(externalProject));

    if (hasArtifactsOrNoRootSettingsFile || androidModel != null) {
      gradleModel =
        createGradleModuleModel(moduleName,
                                gradleModule,
                                androidModels == null ? null : androidModels.getAndroidProject().getModelVersion(),
                                kaptGradleModel,
                                buildScriptClasspathModel,
                                gradlePluginList);
    }
    if (androidModel == null) {
      javaModuleModel = createJavaModuleModel(gradleModule, externalProject, gradlePluginList, hasArtifactsOrNoRootSettingsFile);
    }

    if (javaModuleModel != null) {
      moduleNode.createChild(JAVA_MODULE_MODEL, javaModuleModel);
    }
    if (gradleModel != null) {
      moduleNode.createChild(GRADLE_MODULE_MODEL, gradleModel);
    }
    if (androidModel != null) {
      moduleNode.createChild(ANDROID_MODEL, androidModel);
    }
    if (ndkModuleModel != null) {
      moduleNode.createChild(NDK_MODEL, ndkModuleModel);
    }
    if (issueData != null) {
      issueData.forEach(it -> moduleNode.createChild(SYNC_ISSUE, it));
    }
    // We also need to patch java modules as we disabled the kapt resolver.
    // Setup Kapt this functionality should be done by KaptProjectResovlerExtension if possible.
    // If we have module per sourceSet turned on we need to fill in the GradleSourceSetData for each of the artifacts.
    if (StudioFlags.USE_MODULE_PER_SOURCE_SET.get() && androidModel != null) {
      IdeVariant variant = androidModel.getSelectedVariant();
      createAndSetupGradleSourceSetDataNode(moduleNode, gradleModule, variant.getMainArtifact());
      IdeBaseArtifact unitTest = variant.getUnitTestArtifact();
      if (unitTest != null) {
        createAndSetupGradleSourceSetDataNode(moduleNode, gradleModule, unitTest);
      }
      IdeBaseArtifact androidTest = variant.getAndroidTestArtifact();
      if (androidTest != null) {
        createAndSetupGradleSourceSetDataNode(moduleNode, gradleModule, androidTest);
      }
    }

    // Ensure the kapt module is stored on the datanode so that dependency setup can use it
    moduleNode.putUserData(KAPT_GRADLE_MODEL_KEY, kaptGradleModel);
    patchMissingKaptInformationOntoModelAndDataNode(androidModel, moduleNode, kaptGradleModel);

    // Populate extra things
    populateAdditionalClassifierArtifactsModel(gradleModule);
  }

  @NotNull
  private JavaModuleModel createJavaModuleModel(@NotNull IdeaModule gradleModule,
                                                ExternalProject externalProject,
                                                Collection<String> gradlePluginList,
                                                boolean hasArtifactsOrNoRootSettingsFile) {
    boolean isBuildable = hasArtifactsOrNoRootSettingsFile && gradlePluginList.contains("org.gradle.api.plugins.JavaPlugin");
    // TODO: This model should eventually be removed.
    return myIdeaJavaModuleModelFactory.create(gradleModule, externalProject, isBuildable);
  }

  @NotNull
  private static GradleModuleModel createGradleModuleModel(String moduleName,
                                                           @NotNull IdeaModule gradleModule,
                                                           @Nullable String modelVersionString,
                                                           KaptGradleModel kaptGradleModel,
                                                           BuildScriptClasspathModel buildScriptClasspathModel,
                                                           Collection<String> gradlePluginList) {
    File buildScriptPath;
    try {
      buildScriptPath = gradleModule.getGradleProject().getBuildScript().getSourceFile();
    }
    catch (UnsupportedOperationException e) {
      buildScriptPath = null;
    }

    return new GradleModuleModel(
      moduleName,
      gradleModule.getGradleProject(),
      gradlePluginList,
      buildScriptPath,
      (buildScriptClasspathModel == null) ? null : buildScriptClasspathModel.getGradleVersion(),
      modelVersionString,
      kaptGradleModel
    );
  }

  @Nullable
  private static NdkModuleModel maybeCreateNdkModuleModel(@NotNull String moduleName,
                                                          @NotNull File rootModulePath,
                                                          @NotNull IdeAndroidModels ideModels,
                                                          @NotNull CachedVariants cachedVariants) {
    // Prefer V2 NativeModule if available
    if (ideModels.getV2NativeModule() != null) {
      return new NdkModuleModel(moduleName,
                                rootModulePath,
                                ideModels.getSelectedAbiName(),
                                new V2NdkModel(ideModels.getAndroidProject().getModelVersion(), ideModels.getV2NativeModule()));
    }
    else {
      // V2 model not available, fallback to V1 model.
      if (ideModels.getV1NativeProject() != null) {
        List<IdeNativeVariantAbi> ideNativeVariantAbis = new ArrayList<>();
        if (ideModels.getV1NativeVariantAbi() != null) {
          ideNativeVariantAbis.add(ideModels.getV1NativeVariantAbi());
        }
        // Inject cached variants from previous Gradle Sync.
        ideNativeVariantAbis.addAll(cachedVariants.getNativeVariantsExcept(ideNativeVariantAbis));

        return new NdkModuleModel(moduleName,
                                  rootModulePath,
                                  ideModels.getSelectedAbiName(),
                                  ideModels.getV1NativeProject(),
                                  ideNativeVariantAbis);
      }
      else {
        return null;
      }
    }
  }

  @NotNull
  private static AndroidModuleModel createAndroidModuleModel(String moduleName,
                                                             File rootModulePath,
                                                             @NotNull IdeAndroidModels ideModels,
                                                             @NotNull CachedVariants cachedVariants) {

    List<IdeVariant> filteredCachedVariants = cachedVariants.getVariantsExcept(ideModels.getFetchedVariants());
    List<IdeVariant> variants = ContainerUtil.concat(ideModels.getFetchedVariants(), filteredCachedVariants);
    return AndroidModuleModel.create(moduleName,
                                     rootModulePath,
                                     ideModels.getAndroidProject(),
                                     variants,
                                     ideModels.getSelectedVariantName());
  }

  private void createAndSetupGradleSourceSetDataNode(@NotNull DataNode<ModuleData> parentDataNode,
                                                     @NotNull IdeaModule gradleModule,
                                                     @NotNull IdeBaseArtifact artifact) {
    String moduleId = computeModuleIdForArtifact(resolverCtx, gradleModule, artifact);
    String readableArtifactName = getModuleName(artifact);
    String moduleExternalName = gradleModule.getName() + ":" + readableArtifactName;
    String moduleInternalName =
      parentDataNode.getData().getInternalName() + "." + readableArtifactName;

    GradleSourceSetData sourceSetData =
      new GradleSourceSetData(moduleId, moduleExternalName, moduleInternalName, parentDataNode.getData().getModuleFileDirectoryPath(),
                              parentDataNode.getData().getLinkedExternalProjectPath());

    parentDataNode.createChild(GradleSourceSetData.KEY, sourceSetData);
  }

  private static String computeModuleIdForArtifact(@NotNull ProjectResolverContext resolverCtx,
                                                   @NotNull IdeaModule gradleModule,
                                                   @NotNull IdeBaseArtifact baseArtifact) {
    return getModuleId(resolverCtx, gradleModule) + ":" + getModuleName(baseArtifact);
  }

  public static String getModuleName(@NotNull IdeBaseArtifact artifact) {
    switch (artifact.getName()) {
      case MAIN:
        return "main";
      case UNIT_TEST:
        return "unitTest";
      case ANDROID_TEST:
        return "androidTest";
      default:
        throw new IllegalStateException("Unknown artifact name: " + artifact.getName());
    }
  }

  /**
   * Get variants from previous sync.
   */
  @NotNull
  private CachedVariants findCachedVariants(@NotNull IdeaModule ideaModule) {
    Project project = myProjectFinder.findProject(resolverCtx);
    if (project == null) {
      return CachedVariants.EMPTY;
    }
    Boolean useCachedVariants = project.getUserData(USE_VARIANTS_FROM_PREVIOUS_GRADLE_SYNCS);
    if (useCachedVariants == null || !useCachedVariants) {
      return CachedVariants.EMPTY;
    }
    String moduleId = createUniqueModuleId(ideaModule.getGradleProject());
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      if (moduleId.equals(getModuleIdForModule(module))) {
        List<IdeVariant> cachedGradleVariants = emptyList();
        List<IdeNativeVariantAbi> cachedNativeVariants = emptyList();
        AndroidModuleModel androidModel = AndroidModuleModel.get(module);
        if (androidModel != null) {
          cachedGradleVariants = androidModel.getVariants();
        }
        NdkModuleModel ndkModuleModel = NdkModuleModel.get(module);
        if (ndkModuleModel != null) {
          NdkModel ndkModel = ndkModuleModel.getNdkModel();
          if (ndkModel instanceof V1NdkModel) {
            // Only V1 has NativeVariant that needs to be cached. V2 instead stores the information directly on disk.
            cachedNativeVariants = ((V1NdkModel)ndkModel).getNativeVariantAbis();
          }
        }
        return new CachedVariants(cachedGradleVariants, cachedNativeVariants);
      }
    }
    return CachedVariants.EMPTY;
  }

  /**
   * Adds the Kapt generated source directories to Android models generated source folders and sets up the kapt generated class library
   * for both Android and non-android modules.
   * <p>
   * This should probably not be done here. If we need this information in the Android model then this should
   * be the responsibility of the Android Gradle plugin. If we don't then this should be handled by the
   * KaptProjectResolverExtension, however as of now this class only works when module per source set is
   * enabled.
   */
  public static void patchMissingKaptInformationOntoModelAndDataNode(@Nullable AndroidModuleModel androidModel,
                                                                     @NotNull DataNode<ModuleData> moduleDataNode,
                                                                     @Nullable KaptGradleModel kaptGradleModel) {
    if (kaptGradleModel == null || !kaptGradleModel.isEnabled()) {
      return;
    }

    Set<File> generatedClassesDirs = new HashSet<>();
    Set<File> generatedTestClassesDirs = new HashSet<>();
    kaptGradleModel.getSourceSets().forEach(sourceSet -> {
      if (androidModel == null) {
        // This is a non-android module
        if (sourceSet.isTest()) {
          generatedTestClassesDirs.add(sourceSet.getGeneratedClassesDirFile());
        } else {
          generatedClassesDirs.add(sourceSet.getGeneratedClassesDirFile());
        }
        return;
      }

      File kotlinGenSourceDir = sourceSet.getGeneratedKotlinSourcesDirFile();
      Pair<IdeVariant, IdeBaseArtifact> result = findVariantAndArtifact(sourceSet, androidModel);
      if (result == null) {
        // No artifact was found for the current source set
        return;
      }

      IdeVariant variant = result.first;
      IdeBaseArtifact artifact = result.second;
      if (artifact != null) {
        if (kotlinGenSourceDir != null && !artifact.getGeneratedSourceFolders().contains(kotlinGenSourceDir)) {
          artifact.addGeneratedSourceFolder(kotlinGenSourceDir);
        }

        if (variant.equals(androidModel.getSelectedVariant())) {
          File classesDirFile = sourceSet.getGeneratedClassesDirFile();
          if (classesDirFile != null) {
            if (artifact.isTestArtifact()) {
              generatedTestClassesDirs.add(classesDirFile);
            } else {
              generatedClassesDirs.add(classesDirFile);
            }
          }
        }
      }
    });

    addToNewOrExistingLibraryData(moduleDataNode, "kaptGeneratedClasses", generatedClassesDirs, false);
    addToNewOrExistingLibraryData(moduleDataNode, "kaptGeneratedTestClasses", generatedTestClassesDirs, true);
  }

  private static void addToNewOrExistingLibraryData(@NotNull DataNode<ModuleData> moduleDataNode,
                                             @NotNull String name,
                                             @NotNull Set<File> files,
                                             boolean isTest) {
    // Code adapted from KaptProjectResolverExtension
    LibraryData newLibrary = new LibraryData(GRADLE_SYSTEM_ID, name);
    LibraryData existingData = moduleDataNode.getChildren().stream().map(DataNode::getData).filter(
      (data) -> data instanceof LibraryDependencyData &&
                newLibrary.getExternalName().equals(((LibraryDependencyData)data).getExternalName()))
      .map(data -> ((LibraryDependencyData)data).getTarget()).findFirst().orElse(null);

    if (existingData != null) {
      files.forEach((file) -> existingData.addPath(LibraryPathType.BINARY, file.getAbsolutePath()));
    } else {
      files.forEach((file) -> newLibrary.addPath(LibraryPathType.BINARY, file.getAbsolutePath()));
      LibraryDependencyData libraryDependencyData = new LibraryDependencyData(moduleDataNode.getData(), newLibrary, LibraryLevel.MODULE);
      libraryDependencyData.setScope(isTest ? DependencyScope.TEST : DependencyScope.COMPILE);
      moduleDataNode.createChild(LIBRARY_DEPENDENCY, libraryDependencyData);
    }
  }

  @Nullable
  private static Pair<IdeVariant, IdeBaseArtifact> findVariantAndArtifact(@NotNull KaptSourceSetModel sourceSetModel,
                                                                          @NotNull AndroidModuleModel androidModel) {
    String sourceSetName = sourceSetModel.getSourceSetName();
    if (!sourceSetModel.isTest()) {
      IdeVariant variant = androidModel.findVariantByName(sourceSetName);
      return variant == null ? null : Pair.create(variant, variant.getMainArtifact());
    }

    // Check if it's android test source set.
    String androidTestSuffix = "AndroidTest";
    if (sourceSetName.endsWith(androidTestSuffix)) {
      String variantName = sourceSetName.substring(0, sourceSetName.length() - androidTestSuffix.length());
      IdeVariant variant = androidModel.findVariantByName(variantName);
      return variant == null ? null : Pair.create(variant, variant.getAndroidTestArtifact());
    }

    // Check if it's unit test source set.
    String unitTestSuffix = "UnitTest";
    if (sourceSetName.endsWith(unitTestSuffix)) {
      String variantName = sourceSetName.substring(0, sourceSetName.length() - unitTestSuffix.length());
      IdeVariant variant = androidModel.findVariantByName(variantName);
      return variant == null ? null : Pair.create(variant, variant.getUnitTestArtifact());
    }

    return null;
  }

  private void populateAdditionalClassifierArtifactsModel(@NotNull IdeaModule gradleModule) {
    Project project = myProjectFinder.findProject(resolverCtx);
    AdditionalClassifierArtifactsModel artifacts = resolverCtx.getExtraProject(gradleModule, AdditionalClassifierArtifactsModel.class);
    if (artifacts != null && project != null) {
      LibraryFilePaths.getInstance(project).populate(artifacts);
    }
  }

  @Override
  public void populateModuleContentRoots(@NotNull IdeaModule gradleModule, @NotNull DataNode<ModuleData> ideModule) {
    DataNode<AndroidModuleModel> androidModuleModelNode = ExternalSystemApiUtil.find(ideModule, AndroidProjectKeys.ANDROID_MODEL);
    // Only process android modules.
    if (androidModuleModelNode == null) {
      super.populateModuleContentRoots(gradleModule, ideModule);
      return;
    }

    nextResolver.populateModuleContentRoots(gradleModule, ideModule);

    if (StudioFlags.USE_MODULE_PER_SOURCE_SET.get()) {
      ContentRootUtilKt.setupAndroidContentEntriesPerSourceSet(
        ideModule,
        androidModuleModelNode.getData()
      );
    } else {
      ContentRootUtilKt.setupAndroidContentEntries(ideModule);
    }
  }

  private static boolean hasArtifacts(@Nullable ExternalProject externalProject) {
    return externalProject != null && !externalProject.getArtifacts().isEmpty();
  }

  @Override
  public void populateModuleDependencies(@NotNull IdeaModule gradleModule,
                                         @NotNull DataNode<ModuleData> ideModule,
                                         @NotNull DataNode<ProjectData> ideProject) {
    DataNode<AndroidModuleModel> androidModelNode = ExternalSystemApiUtil.find(ideModule, AndroidProjectKeys.ANDROID_MODEL);
    // Don't process non-android modules here.
    if (androidModelNode == null) {
      super.populateModuleDependencies(gradleModule, ideModule, ideProject);
      return;
    }

    // Call all the other resolvers to ensure that any dependencies that they need to provide are added.
    nextResolver.populateModuleDependencies(gradleModule, ideModule, ideProject);
    // In AndroidStudio pre-3.0 all dependencies need to be exported, the common resolvers do not set this.
    // to remedy this we need to go through all data nodes added by other resolvers and set this flag.
    if (myShouldExportDependencies) {
      Collection<DataNode<LibraryDependencyData>> libraryDataNodes = findAll(ideModule, LIBRARY_DEPENDENCY);
      for (DataNode<LibraryDependencyData> libraryDataNode : libraryDataNodes) {
        libraryDataNode.getData().setExported(true);
      }
      Collection<DataNode<ModuleDependencyData>> moduleDataNodes = findAll(ideModule, ProjectKeys.MODULE_DEPENDENCY);
      for (DataNode<ModuleDependencyData> moduleDataNode : moduleDataNodes) {
        moduleDataNode.getData().setExported(true);
      }
    }

    AdditionalClassifierArtifactsModel additionalArtifacts =
      resolverCtx.getExtraProject(gradleModule, AdditionalClassifierArtifactsModel.class);
    // TODO: Log error messages from additionalArtifacts.

    GradleExecutionSettings settings = resolverCtx.getSettings();
    GradleExecutionWorkspace workspace = (settings == null) ? null : settings.getExecutionWorkspace();

    Map<String, AdditionalClassifierArtifacts> additionalArtifactsMap;
    if (additionalArtifacts != null) {
      additionalArtifactsMap =
        additionalArtifacts
          .getArtifacts()
          .stream()
          .collect(
            Collectors.toMap((k) -> String.format("%s:%s:%s", k.getId().getGroupId(), k.getId().getArtifactId(), k.getId().getVersion()),
                             (k) -> k
            ));
    }
    else {
      additionalArtifactsMap = ImmutableMap.of();
    }


    Project project = myProjectFinder.findProject(resolverCtx);
    LibraryFilePaths libraryFilePaths;
    if (project == null) {
      libraryFilePaths = null;
    } else {
      libraryFilePaths = LibraryFilePaths.getInstance(project);
    }

    Function<String, ModuleData> moduleDataLookup = (id) -> {
      if (workspace != null) {
        return workspace.findModuleDataByModuleId(id);
      }
      return null;
    };

    BiFunction<String, File, AdditionalArtifactsPaths> artifactLookup = (artifactId, artifactPath) -> {
      // First check to see if we just obtained any paths from Gradle. Since we don't request all the paths this can be null
      // or contain an imcomplete set of entries. In order to complete this set we need to obtains the reminder from LibraryFilePaths cache.
      AdditionalClassifierArtifacts artifacts = additionalArtifactsMap.get(artifactId);
      if (artifacts != null) {
        new AdditionalArtifactsPaths(artifacts.getSources(), artifacts.getJavadoc(), artifacts.getSampleSources());
      }

      // Then check to see whether we already have the library cached.
      if (libraryFilePaths != null) {
        ArtifactPaths cachedPaths = libraryFilePaths.getCachedPathsForArtifact(artifactId);
        if (cachedPaths != null) {
          return new AdditionalArtifactsPaths(cachedPaths.sources, cachedPaths.javaDoc, cachedPaths.sampleSource);
        }
      }
      return null;
    };

    if (StudioFlags.USE_MODULE_PER_SOURCE_SET.get()) {
      DependencyUtilKt.setupAndroidDependenciesForMpss(
        ideModule,
        moduleDataLookup::apply,
        artifactLookup::apply,
        androidModelNode.getData(),
        androidModelNode.getData().getSelectedVariant()
      );
    } else {
      DependencyUtilKt.setupAndroidDependenciesForModule(ideModule, moduleDataLookup::apply, artifactLookup::apply);
    }
  }

  @Override
  public void resolveFinished(@NotNull DataNode<ProjectData> projectDataNode) {
    disableOrphanModuleNotifications();
  }

  /**
   * A method that resets the configuration of "Build sync orphan modules" notification group to "not display" and "not log"
   * in order to prevent a notification which allows users to restore the removed module as a non-Gradle module. Non-Gradle modules
   * are not supported by AS in Gradle projects.
   */
  private static void disableOrphanModuleNotifications() {
    if (IdeInfo.getInstance().isAndroidStudio()) {
      NotificationsConfiguration
        .getNotificationsConfiguration()
        .changeSettings(BUILD_SYNC_ORPHAN_MODULES_NOTIFICATION_GROUP_NAME, NotificationDisplayType.NONE, false, false);
    }
  }

  // Indicates it is an "Android" project if at least one module has an AndroidProject.
  private boolean isAndroidGradleProject() {
    Boolean isAndroidGradleProject = resolverCtx.getUserData(IS_ANDROID_PROJECT_KEY);
    if (isAndroidGradleProject != null) {
      return isAndroidGradleProject;
    }
    isAndroidGradleProject = resolverCtx.hasModulesWithModel(IdeAndroidModels.class);
    return resolverCtx.putUserDataIfAbsent(IS_ANDROID_PROJECT_KEY, isAndroidGradleProject);
  }

  @Override
  public void populateProjectExtraModels(@NotNull IdeaProject gradleProject, @NotNull DataNode<ProjectData> projectDataNode) {
    IdeAndroidSyncError syncError = resolverCtx.getModels().getModel(IdeAndroidSyncError.class);
    if (syncError != null) {
      throw ideAndroidSyncErrorToException(syncError);
    }
    // Special mode sync to fetch additional native variants.
    for (IdeaModule gradleModule : gradleProject.getModules()) {
      IdeAndroidNativeVariantsModels nativeVariants = resolverCtx.getExtraProject(gradleModule, IdeAndroidNativeVariantsModels.class);
      if (nativeVariants != null) {
        projectDataNode.createChild(NATIVE_VARIANTS,
                                    new IdeAndroidNativeVariantsModelsWrapper(
                                      GradleProjectResolverUtil.getModuleId(resolverCtx, gradleModule),
                                      nativeVariants
                                    ));
      }
    }
    if (isAndroidGradleProject()) {
      projectDataNode.createChild(PROJECT_CLEANUP_MODEL, ProjectCleanupModel.getInstance());
    }
    super.populateProjectExtraModels(gradleProject, projectDataNode);
  }

  /**
   * This method is not used. Its functionality is only present when not using a
   * {@link ProjectImportModelProvider}. See: {@link #getModelProvider}
   */
  @Override
  @NotNull
  public Set<Class<?>> getExtraProjectModelClasses() {
    throw new UnsupportedOperationException("getExtraProjectModelClasses() is not used when getModelProvider() is overridden.");
  }

  @NotNull
  @Override
  public ProjectImportModelProvider getModelProvider() {
    return configureAndGetExtraModelProvider();
  }

  @Override
  public void preImportCheck() {
    // Don't run pre-import checks for the buildSrc project.
    if (resolverCtx.getBuildSrcGroup() != null) {
      return;
    }

    simulateRegisteredSyncError();

    syncAndroidSdks(SdkSync.getInstance(), resolverCtx.getProjectPath());

    JdkImportCheck.validateJdk();
    validateGradleWrapper(resolverCtx.getProjectPath());

    displayInternalWarningIfForcedUpgradesAreDisabled();
    expireProjectUpgradeNotifications(myProjectFinder.findProject(resolverCtx));

    if (IdeInfo.getInstance().isAndroidStudio()) {
      // Don't execute in IDEA in order to avoid conflicting behavior with IDEA's proxy support in gradle project.
      // (https://youtrack.jetbrains.com/issue/IDEA-245273, see BaseResolverExtension#getExtraJvmArgs)
      // To be discussed with the AOSP team to find a way to unify configuration across IDEA and AndroidStudio.
      cleanUpHttpProxySettings();
    }
  }

  @Override
  @NotNull
  public List<Pair<String, String>> getExtraJvmArgs() {
    if (isInProcessMode(GRADLE_SYSTEM_ID)) {
      List<Pair<String, String>> args = new ArrayList<>();

      if (IdeInfo.getInstance().isAndroidStudio()) {
        // Inject javaagent args.
        TraceSyncUtil.addTraceJvmArgs(args);
      }
      else {
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
        if (msg.startsWith(COULD_NOT_INSTALL_GRADLE_DISTRIBUTION_PREFIX)) {
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
    GradleExecutionSettings gradleExecutionSettings = resolverCtx.getSettings();
    ProjectResolutionMode projectResolutionMode = getRequestedSyncMode(gradleExecutionSettings);
    SyncActionOptions syncOptions;

    boolean parallelSync = StudioFlags.GRADLE_SYNC_PARALLEL_SYNC_ENABLED.get();
    boolean parallelSyncPrefetchVariants = StudioFlags.GRADLE_SYNC_PARALLEL_SYNC_PREFETCH_VARIANTS.get();
    GradleSyncStudioFlags studioFlags = new GradleSyncStudioFlags(parallelSync, parallelSyncPrefetchVariants);

    if (projectResolutionMode == ProjectResolutionMode.SyncProjectMode.INSTANCE) {
      // Here we set up the options for the sync and pass them to the AndroidExtraModelProvider which will decide which will use them
      // to decide which models to request from Gradle.
      @Nullable Project project = myProjectFinder.findProject(resolverCtx);

      AdditionalClassifierArtifactsActionOptions additionalClassifierArtifactsAction =
        new AdditionalClassifierArtifactsActionOptions(
          (project != null) ? LibraryFilePaths.getInstance(project).retrieveCachedLibs() : emptySet(),
          StudioFlags.SAMPLES_SUPPORT_ENABLED.get()
        );
      boolean isSingleVariantSync = project != null && !shouldSyncAllVariants(project);
      if (isSingleVariantSync) {
        SelectedVariantCollector variantCollector = new SelectedVariantCollector(project);
        SelectedVariants selectedVariants = variantCollector.collectSelectedVariants();
        String moduleWithVariantSwitched = project.getUserData(MODULE_WITH_BUILD_VARIANT_SWITCHED_FROM_UI);
        project.putUserData(MODULE_WITH_BUILD_VARIANT_SWITCHED_FROM_UI, null);
        syncOptions = new SingleVariantSyncActionOptions(
          studioFlags,
          selectedVariants,
          moduleWithVariantSwitched,
          additionalClassifierArtifactsAction
        );
      }
      else {
        syncOptions = new FullSyncActionOptions(studioFlags, additionalClassifierArtifactsAction);
      }
    }
    else if (projectResolutionMode instanceof ProjectResolutionMode.FetchNativeVariantsMode) {
      ProjectResolutionMode.FetchNativeVariantsMode fetchNativeVariantsMode =
        (ProjectResolutionMode.FetchNativeVariantsMode)projectResolutionMode;
      syncOptions = new NativeVariantsSyncActionOptions(studioFlags,
                                                        fetchNativeVariantsMode.getModuleVariants(),
                                                        fetchNativeVariantsMode.getRequestedAbis());
    }
    else {
      throw new IllegalStateException("Unknown FetchModelsMode class: " + projectResolutionMode.getClass().getName());
    }
    return new AndroidExtraModelProvider(syncOptions);
  }

  @NotNull
  private static ProjectResolutionMode getRequestedSyncMode(GradleExecutionSettings gradleExecutionSettings) {
    ProjectResolutionMode projectResolutionMode =
      gradleExecutionSettings != null ? gradleExecutionSettings.getUserData(REQUESTED_PROJECT_RESOLUTION_MODE_KEY) : null;
    return projectResolutionMode != null ? projectResolutionMode : ProjectResolutionMode.SyncProjectMode.INSTANCE;
  }

  private static boolean shouldSyncAllVariants(@NotNull Project project) {
    Boolean shouldSyncAllVariants = project.getUserData(GradleSyncExecutor.FULL_SYNC_KEY);
    return shouldSyncAllVariants != null && shouldSyncAllVariants;
  }

  private void displayInternalWarningIfForcedUpgradesAreDisabled() {
    if (DISABLE_FORCED_UPGRADES.get()) {
      Project project = myProjectFinder.findProject(resolverCtx);
      if (project != null) {
        displayForceUpdatesDisabledMessage(project);
      }
    }
  }

  private void cleanUpHttpProxySettings() {
    Project project = myProjectFinder.findProject(resolverCtx);
    if (project != null) {
      ApplicationManager.getApplication().invokeAndWait(() -> HttpProxySettingsCleanUp.cleanUp(project));
    }
  }

  @Override
  public void enhanceRemoteProcessing(@NotNull SimpleJavaParameters parameters) {
    PathsList classPath = parameters.getClassPath();
    classPath.add(getJarPathForClass(getClass()));
    classPath.add(getJarPathForClass(Revision.class));
    classPath.add(getJarPathForClass(AndroidGradleSettings.class));
  }

  @Nullable
  public static String getModuleIdForModule(@NotNull Module module) {
    ExternalSystemModulePropertyManager propertyManager = ExternalSystemModulePropertyManager.getInstance(module);
    String rootProjectPath = propertyManager.getRootProjectPath();
    if (rootProjectPath != null) {
      String gradlePath = propertyManager.getLinkedProjectId();
      if (gradlePath != null) {
        return createUniqueModuleId(rootProjectPath, gradlePath);
      }
    }
    return null;
  }
}
