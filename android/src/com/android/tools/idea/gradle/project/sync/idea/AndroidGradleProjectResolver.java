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

import static com.android.tools.idea.flags.StudioFlags.DISABLE_FORCED_UPGRADES;
import static com.android.tools.idea.gradle.project.sync.Modules.createUniqueModuleId;
import static com.android.tools.idea.gradle.project.sync.SimulatedSyncErrors.simulateRegisteredSyncError;
import static com.android.tools.idea.gradle.project.sync.errors.GradleDistributionInstallIssueCheckerKt.COULD_NOT_INSTALL_GRADLE_DISTRIBUTION_PREFIX;
import static com.android.tools.idea.gradle.project.sync.errors.UnsupportedModelVersionIssueCheckerKt.READ_MIGRATION_GUIDE_MSG;
import static com.android.tools.idea.gradle.project.sync.errors.UnsupportedModelVersionIssueCheckerKt.UNSUPPORTED_MODEL_VERSION_ERROR_PREFIX;
import static com.android.tools.idea.gradle.project.sync.idea.GradleModelVersionCheck.getModelVersion;
import static com.android.tools.idea.gradle.project.sync.idea.GradleModelVersionCheck.isSupportedVersion;
import static com.android.tools.idea.gradle.project.sync.idea.SdkSyncUtil.syncAndroidSdks;
import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.ANDROID_MODEL;
import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.GRADLE_MODULE_MODEL;
import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.JAVA_MODULE_MODEL;
import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.NDK_MODEL;
import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.PROJECT_CLEANUP_MODEL;
import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.SYNC_ISSUE;
import static com.android.tools.idea.gradle.project.sync.idea.issues.GradleWrapperImportCheck.validateGradleWrapper;
import static com.android.tools.idea.gradle.project.sync.setup.post.upgrade.GradlePluginUpgrade.displayForceUpdatesDisabledMessage;
import static com.android.tools.idea.gradle.project.sync.setup.post.upgrade.GradlePluginUpgrade.expireProjectUpgradeNotifications;
import static com.android.tools.idea.gradle.util.AndroidGradleSettings.ANDROID_HOME_JVM_ARG;
import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.android.tools.idea.gradle.variant.view.BuildVariantUpdater.MODULE_WITH_BUILD_VARIANT_SWITCHED_FROM_UI;
import static com.android.tools.idea.gradle.variant.view.BuildVariantUpdater.USE_VARIANTS_FROM_PREVIOUS_GRADLE_SYNCS;
import static com.android.tools.idea.gradle.variant.view.BuildVariantUpdater.getModuleIdForModule;
import static com.android.utils.BuildScriptUtil.findGradleSettingsFile;
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
import com.android.ide.common.gradle.model.IdeVariant;
import com.android.ide.common.gradle.model.level2.IdeDependenciesFactory;
import com.android.ide.common.repository.GradleVersion;
import com.android.ide.gradle.model.GradlePluginModel;
import com.android.ide.gradle.model.artifacts.AdditionalClassifierArtifacts;
import com.android.ide.gradle.model.artifacts.AdditionalClassifierArtifactsModel;
import com.android.repository.Revision;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.LibraryFilePaths;
import com.android.tools.idea.gradle.plugin.LatestKnownPluginVersionProvider;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.project.model.IdeaJavaModuleModelFactory;
import com.android.tools.idea.gradle.project.model.JavaModuleModel;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.project.sync.SdkSync;
import com.android.tools.idea.gradle.project.sync.SelectedVariantCollector;
import com.android.tools.idea.gradle.project.sync.SelectedVariants;
import com.android.tools.idea.gradle.project.sync.SyncActionOptions;
import com.android.tools.idea.gradle.project.sync.common.CommandLineArgs;
import com.android.tools.idea.gradle.project.sync.idea.data.model.ProjectCleanupModel;
import com.android.tools.idea.gradle.project.sync.idea.issues.AgpUpgradeRequiredException;
import com.android.tools.idea.gradle.project.sync.idea.issues.AndroidSyncException;
import com.android.tools.idea.gradle.project.sync.idea.issues.JdkImportCheck;
import com.android.tools.idea.gradle.project.sync.idea.svs.AndroidExtraModelProvider;
import com.android.tools.idea.gradle.project.sync.idea.svs.CachedVariants;
import com.android.tools.idea.gradle.project.sync.idea.svs.VariantGroup;
import com.android.tools.idea.gradle.project.sync.issues.SyncIssueData;
import com.android.tools.idea.gradle.project.sync.setup.post.upgrade.GradlePluginUpgrade;
import com.android.tools.idea.gradle.util.AndroidGradleSettings;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.io.FilePaths;
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
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
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
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipException;
import kotlin.Unit;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.ProjectModel;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.kapt.idea.KaptGradleModel;
import org.jetbrains.kotlin.kapt.idea.KaptModelBuilderService;
import org.jetbrains.kotlin.kapt.idea.KaptSourceSetModel;
import org.jetbrains.plugins.gradle.model.Build;
import org.jetbrains.plugins.gradle.model.BuildScriptClasspathModel;
import org.jetbrains.plugins.gradle.model.ExternalProject;
import org.jetbrains.plugins.gradle.model.ExternalSourceSet;
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider;
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData;
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension;
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolver;
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;
import org.jetbrains.plugins.gradle.settings.GradleExecutionWorkspace;

/**
 * Imports Android-Gradle projects into IDEA.
 */
@Order(ExternalSystemConstants.UNORDERED)
public class AndroidGradleProjectResolver extends AbstractProjectResolverExtension {
  public static final String BUILD_SYNC_ORPHAN_MODULES_NOTIFICATION_GROUP_NAME = "Build sync orphan modules";
  private static final Key<Boolean> IS_ANDROID_PROJECT_KEY = Key.create("IS_ANDROID_PROJECT_KEY");
  static final Logger RESOLVER_LOG = Logger.getInstance(AndroidGradleProjectResolver.class);

  @NotNull private final CommandLineArgs myCommandLineArgs;
  @NotNull private final ProjectFinder myProjectFinder;
  @NotNull private final IdeNativeAndroidProject.Factory myNativeAndroidProjectFactory;
  @NotNull private final IdeaJavaModuleModelFactory myIdeaJavaModuleModelFactory;
  @NotNull private final IdeDependenciesFactory myDependenciesFactory;
  private boolean myIsImportPre3Dot0;

  @SuppressWarnings("unused")
  // This constructor is used by the IDE. This class is an extension point implementation, registered in plugin.xml.
  public AndroidGradleProjectResolver() {
    this(new CommandLineArgs(), new ProjectFinder(), new IdeNativeAndroidProjectImpl.FactoryImpl(),
         new IdeaJavaModuleModelFactory(), new IdeDependenciesFactory());
  }

  @NonInjectable
  @VisibleForTesting
  AndroidGradleProjectResolver(@NotNull CommandLineArgs commandLineArgs,
                               @NotNull ProjectFinder projectFinder,
                               @NotNull IdeNativeAndroidProject.Factory nativeAndroidProjectFactory,
                               @NotNull IdeaJavaModuleModelFactory ideaJavaModuleModelFactory,
                               @NotNull IdeDependenciesFactory dependenciesFactory) {
    myCommandLineArgs = commandLineArgs;
    myProjectFinder = projectFinder;
    myNativeAndroidProjectFactory = nativeAndroidProjectFactory;
    myIdeaJavaModuleModelFactory = ideaJavaModuleModelFactory;
    myDependenciesFactory = dependenciesFactory;
  }

  @Override
  @Nullable
  public DataNode<ModuleData> createModule(@NotNull IdeaModule gradleModule, @NotNull DataNode<ProjectData> projectDataNode) {
    if (!isAndroidGradleProject()) {
      return nextResolver.createModule(gradleModule, projectDataNode);
    }

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

    if (androidProject != null) {
      Project project = myProjectFinder.findProject(resolverCtx);

      // Before anything, check to see if what we have is compatible with this version of studio.
      GradleVersion currentAgpVersion = GradleVersion.tryParse(androidProject.getModelVersion());
      GradleVersion latestVersion = GradleVersion.parse(LatestKnownPluginVersionProvider.INSTANCE.get());
      if (currentAgpVersion != null && GradlePluginUpgrade.shouldForcePluginUpgrade(project, currentAgpVersion, latestVersion)) {
        throw new AgpUpgradeRequiredException(project, currentAgpVersion);
      }
    }

    DataNode<ModuleData> moduleDataNode = nextResolver.createModule(gradleModule, projectDataNode);
    if (moduleDataNode == null) {
      return null;
    }

    createAndAttachModelsToDataNode(moduleDataNode, gradleModule, androidProject);

    if (androidProject != null) {
      DataNode<JavaModuleData> javaModuleData = find(moduleDataNode, JavaModuleData.KEY);
      if (javaModuleData != null) {
        LanguageLevel languageLevel = LanguageLevel.parse(androidProject.getJavaCompileOptions().getSourceCompatibility());
        javaModuleData.getData().setLanguageLevel(languageLevel);
        javaModuleData.getData().setTargetBytecodeVersion(androidProject.getJavaCompileOptions().getTargetCompatibility());
      }
      CompilerOutputUtilKt.setupCompilerOutputPaths(moduleDataNode);
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
          DataNode<JavaModuleData> javaModuleData = find(moduleDataNode, JavaModuleData.KEY);
          if (javaModuleData != null) {
            LanguageLevel languageLevel = LanguageLevel.parse(externalSourceSet.getSourceCompatibility());
            javaModuleData.getData().setLanguageLevel(languageLevel);
            javaModuleData.getData().setTargetBytecodeVersion(externalSourceSet.getTargetCompatibility());
          }
        }
      }
    }

    return moduleDataNode;
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
   * @param moduleNode     the module node to attach the models to
   * @param gradleModule   the module in question
   * @param androidProject the android project obtained from this module (null is none found)
   */
  private void createAndAttachModelsToDataNode(@NotNull DataNode<ModuleData> moduleNode,
                                               @NotNull IdeaModule gradleModule,
                                               @Nullable AndroidProject androidProject) {
    String moduleName = moduleNode.getData().getInternalName();
    File rootModulePath = FilePaths.stringToFile(moduleNode.getData().getLinkedExternalProjectPath());

    VariantGroup variantGroup = resolverCtx.getExtraProject(gradleModule, VariantGroup.class);
    // The ProjectSyncIssues model was introduced in the Android Gradle plugin 3.6, it contains all the
    // sync issues that have been produced by the plugin. Before this the sync issues were attached to the
    // AndroidProject.
    ProjectSyncIssues projectSyncIssues = resolverCtx.getExtraProject(gradleModule, ProjectSyncIssues.class);
    KaptGradleModel kaptGradleModel = resolverCtx.getExtraProject(gradleModule, KaptGradleModel.class);
    CachedVariants cachedVariants = findCachedVariants(gradleModule);

    // 1 - If we have an AndroidProject then we need to construct an AndroidModuleModel.
    if (androidProject != null) {
      Variant selectedVariant = findVariantToSelect(androidProject, variantGroup);
      Collection<SyncIssue> syncIssues = findSyncIssues(androidProject, projectSyncIssues);

      // Add the SyncIssues as DataNodes to the project data tree. While we could just re-use the
      // SyncIssues in AndroidModuleModel this allows us to remove sync issues from the IDE side model in the future.
      syncIssues.forEach((syncIssue) -> {
        SyncIssueData issueData = new SyncIssueData(
          syncIssue.getMessage(),
          syncIssue.getData(),
          syncIssue.getMultiLineMessage(),
          syncIssue.getSeverity(),
          syncIssue.getType()
        );
        moduleNode.createChild(SYNC_ISSUE, issueData);
      });

      AndroidModuleModel androidModel = AndroidModuleModel.create(
        moduleName,
        rootModulePath,
        androidProject,
        selectedVariant.getName(),
        myDependenciesFactory,
        (variantGroup == null) ? null : variantGroup.getVariants(),
        (cachedVariants == null) ? emptyList() : cachedVariants.getVariants(),
        syncIssues
      );

      // Set whether or not we have seen an old (pre 3.0) version of the AndroidProject. If we have seen one
      // Then we require all Java modules to export their dependencies.
      myIsImportPre3Dot0 |= androidModel.getFeatures().shouldExportDependencies();

      moduleNode.createChild(ANDROID_MODEL, androidModel);

      // Setup Kapt this functionality should be done by KaptProjectResovlerExtension if possible.
      patchMissingKaptInformationOntoModelAndDataNode(androidModel, moduleNode, kaptGradleModel);
    } else {
      // We also need to patch java modules as we disabled the kapt resolver.
      patchMissingKaptInformationOntoModelAndDataNode(null, moduleNode, kaptGradleModel);
    }



    // 2 -  If we have an NativeAndroidProject then we need to construct an NdkModuleModel
    NativeAndroidProject nativeAndroidProject = resolverCtx.getExtraProject(gradleModule, NativeAndroidProject.class);
    if (nativeAndroidProject != null) {
      IdeNativeAndroidProject nativeProjectCopy = myNativeAndroidProjectFactory.create(nativeAndroidProject);
      List<IdeNativeVariantAbi> ideNativeVariantAbis;
      if (variantGroup != null) {
        ideNativeVariantAbis = ContainerUtil.map(variantGroup.getNativeVariants(), IdeNativeVariantAbi::new);
      }
      else {
        ideNativeVariantAbis = new ArrayList<>();
      }
      // Inject cached variants from previous Gradle Sync.
      if (cachedVariants != null) {
        Set<String> variantNames = ideNativeVariantAbis.stream().map(variant -> variant.getAbi()).collect(Collectors.toSet());
        for (IdeNativeVariantAbi variant : cachedVariants.getNativeVariants()) {
          // Add cached IdeNativeVariantAbi only if it is not contained in the current model.
          if (!variantNames.contains(variant.getAbi())) {
            ideNativeVariantAbis.add(variant);
          }
        }
      }

      NdkModuleModel ndkModel = new NdkModuleModel(moduleName, rootModulePath, nativeProjectCopy, ideNativeVariantAbis);
      moduleNode.createChild(NDK_MODEL, ndkModel);
    }

    File gradleSettingsFile = findGradleSettingsFile(rootModulePath);
    if (gradleSettingsFile.isFile() && androidProject == null && nativeAndroidProject == null &&
        // if the module has artifacts, it is a Java library module.
        // https://code.google.com/p/android/issues/detail?id=226802
        !hasArtifacts(gradleModule)) {
      // This is just a root folder for a group of Gradle projects. We don't set an IdeaGradleProject so the JPS builder won't try to
      // compile it using Gradle. We still need to create the module to display files inside it.
      createJavaProject(gradleModule, moduleNode, emptyList(), false);
      return;
    }

    // 3 - Now we need to create and add the GradleModuleModel
    GradlePluginModel gradlePluginModel = resolverCtx.getExtraProject(gradleModule, GradlePluginModel.class);
    File buildScriptPath;
    try {
      buildScriptPath = gradleModule.getGradleProject().getBuildScript().getSourceFile();
    }
    catch (UnsupportedOperationException e) {
      buildScriptPath = null;
    }

    BuildScriptClasspathModel buildScriptClasspathModel = resolverCtx.getExtraProject(gradleModule, BuildScriptClasspathModel.class);
    Collection<String> gradlePluginList = (gradlePluginModel == null) ? ImmutableList.of() : gradlePluginModel.getGradlePluginList();

    GradleModuleModel gradleModel = new GradleModuleModel(
      moduleName,
      gradleModule.getGradleProject(),
      gradlePluginList,
      buildScriptPath,
      (buildScriptClasspathModel == null) ? null : buildScriptClasspathModel.getGradleVersion(),
      (androidProject == null) ? null : androidProject.getModelVersion(),
      kaptGradleModel
    );
    moduleNode.createChild(GRADLE_MODULE_MODEL, gradleModel);

    // 4 - If this is not an Android or Native project it must be a Java module.
    // TODO: This model should eventually be removed.
    if (androidProject == null && nativeAndroidProject == null) {
      createJavaProject(
        gradleModule,
        moduleNode,
        ImmutableList.of(),
        gradlePluginList.contains("org.gradle.api.plugins.JavaPlugin")
      );
    }

    // 5 - Populate extra things
    populateAdditionalClassifierArtifactsModel(gradleModule);
  }

  /**
   * Get variants from previous sync.
   */
  @Nullable
  CachedVariants findCachedVariants(@NotNull IdeaModule ideaModule) {
    Project project = myProjectFinder.findProject(resolverCtx);
    if (project == null) {
      return null;
    }
    Boolean useCachedVariants = project.getUserData(USE_VARIANTS_FROM_PREVIOUS_GRADLE_SYNCS);
    if (useCachedVariants == null || !useCachedVariants) {
      return null;
    }
    String moduleId = createUniqueModuleId(ideaModule.getGradleProject());
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      if (moduleId.equals(getModuleIdForModule(module))) {
        CachedVariants cachedVariants = new CachedVariants();
        AndroidModuleModel androidModel = AndroidModuleModel.get(module);
        if (androidModel != null) {
          cachedVariants.getVariants().addAll(androidModel.getVariants());
        }
        NdkModuleModel nativeModel = NdkModuleModel.get(module);
        if (nativeModel != null) {
          cachedVariants.getNativeVariants().addAll(nativeModel.getVariantAbi());
        }
        return cachedVariants;
      }
    }
    return null;
  }

  /**
   * Obtains a list of [SyncIssue]s from either the [AndroidProject] (legacy pre Android Gradle plugin 3.6)
   * or from the [ProjectSyncIssues] model (post Android Gradle plugin 3.6).
   */
  @NotNull
  Collection<SyncIssue> findSyncIssues(@NotNull AndroidProject androidProject, @Nullable ProjectSyncIssues projectSyncIssues) {
    if (projectSyncIssues != null) {
      return projectSyncIssues.getSyncIssues();
    }
    else {
      //noinspection deprecation
      return androidProject.getSyncIssues();
    }
  }

  /**
   * Obtain the selected variant using either the legacy method or from the [VariantGroup]. If no variants are
   * found then this method throws an [AndroidSyncException].
   */
  @VisibleForTesting
  @NotNull
  public static Variant findVariantToSelect(@NotNull AndroidProject androidProject, @Nullable VariantGroup variantGroup) {
    if (variantGroup != null) {
      List<Variant> variants = variantGroup.getVariants();
      if (!variants.isEmpty()) {
        return variants.get(0);
      }
    }

    Variant legacyVariant = findLegacyVariantToSelect(androidProject);
    if (legacyVariant != null) {
      return legacyVariant;
    }

    throw new AndroidSyncException(
      "No variants found for '" + androidProject.getName() + "'. Check build files to ensure at least one variant exists.");
  }

  /**
   * Attempts to find a variant from the [AndroidProject], this is here to support legacy versions of the
   * Android Gradle plugin that don't have the [VariantGroup] model populated. First it tries to find a
   * [Variant] by the name "debug", otherwise returns the first variant found.
   */
  @Nullable
  private static Variant findLegacyVariantToSelect(@NotNull AndroidProject androidProject) {
    Collection<Variant> variants = androidProject.getVariants();
    if (variants.isEmpty()) {
      return null;
    }

    // First attempt to select the "debug" variant if it exists.
    Variant debugVariant = variants.stream().filter(variant -> variant.getName().equals("debug")).findFirst().orElse(null);
    if (debugVariant != null) {
      return debugVariant;
    }

    // Otherwise return the first variant.
    return variants.stream().min(Comparator.comparing(Variant::getName)).orElse(null);
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
  private static void patchMissingKaptInformationOntoModelAndDataNode(@Nullable AndroidModuleModel androidModel,
                                                                      @NotNull DataNode<ModuleData> moduleDataNode,
                                                                      @Nullable KaptGradleModel kaptGradleModel) {
    if (kaptGradleModel == null || !kaptGradleModel.isEnabled()) {
      return;
    }

    Set<File> generatedClassesDirs = new HashSet<>();
    kaptGradleModel.getSourceSets().forEach(sourceSet -> {
      File kotlinGenSourceDir = sourceSet.getGeneratedKotlinSourcesDirFile();
      if (androidModel != null) {
        IdeBaseArtifact artifact = findArtifact(sourceSet, androidModel);
        if (artifact != null && kotlinGenSourceDir != null) {
          artifact.addGeneratedSourceFolder(kotlinGenSourceDir);
        }
      }

      // We should really only add the current variant here, but we need to work out how to store this information and switch
      // the library. For now just add all of them.
      File classesDirFile = sourceSet.getGeneratedClassesDirFile();
      if (classesDirFile != null) {
        generatedClassesDirs.add(classesDirFile);
      }
    });

    // Code adapted from KaptProjectResolverExtension
    LibraryData newLibrary = new LibraryData(GRADLE_SYSTEM_ID, "kaptGeneratedClasses");
    LibraryData existingData = moduleDataNode.getChildren().stream().map(node -> node.getData()).filter(
      (data) -> data instanceof LibraryDependencyData &&
                newLibrary.getExternalName().equals(((LibraryDependencyData)data).getExternalName()))
      .map(data -> ((LibraryDependencyData)data).getTarget()).findFirst().orElse(null);

    if (existingData != null) {
      generatedClassesDirs.forEach((file) -> existingData.addPath(LibraryPathType.BINARY, file.getAbsolutePath()));
    } else {
      generatedClassesDirs.forEach((file) -> newLibrary.addPath(LibraryPathType.BINARY, file.getAbsolutePath()));
      LibraryDependencyData libraryDependencyData = new LibraryDependencyData(moduleDataNode.getData(), newLibrary, LibraryLevel.MODULE);
      moduleDataNode.createChild(LIBRARY_DEPENDENCY, libraryDependencyData);
    }
  }

  @Nullable
  private static IdeBaseArtifact findArtifact(@NotNull KaptSourceSetModel sourceSetModel, @NotNull AndroidModuleModel androidModel) {
    String sourceSetName = sourceSetModel.getSourceSetName();
    if (!sourceSetModel.isTest()) {
      IdeVariant variant = androidModel.findVariantByName(sourceSetName);
      return variant == null ? null : variant.getMainArtifact();
    }

    // Check if it's android test source set.
    String androidTestSuffix = "AndroidTest";
    if (sourceSetName.endsWith(androidTestSuffix)) {
      String variantName = sourceSetName.substring(0, sourceSetName.length() - androidTestSuffix.length());
      IdeVariant variant = androidModel.findVariantByName(variantName);
      return variant == null ? null : variant.getAndroidTestArtifact();
    }

    // Check if it's unit test source set.
    String unitTestSuffix = "UnitTest";
    if (sourceSetName.endsWith(unitTestSuffix)) {
      String variantName = sourceSetName.substring(0, sourceSetName.length() - unitTestSuffix.length());
      IdeVariant variant = androidModel.findVariantByName(variantName);
      return variant == null ? null : variant.getUnitTestArtifact();
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
    nextResolver.populateModuleContentRoots(gradleModule, ideModule);

    ContentRootUtilKt.setupAndroidContentEntries(ideModule);
  }

  private boolean hasArtifacts(@NotNull IdeaModule gradleModule) {
    ExternalProject externalProject = resolverCtx.getExtraProject(gradleModule, ExternalProject.class);
    return externalProject != null && !externalProject.getArtifacts().isEmpty();
  }

  private void createJavaProject(@NotNull IdeaModule gradleModule,
                                 @NotNull DataNode<ModuleData> ideModule,
                                 @NotNull Collection<SyncIssue> syncIssues,
                                 boolean isBuildable) {
    ExternalProject externalProject = resolverCtx.getExtraProject(gradleModule, ExternalProject.class);
    JavaModuleModel javaModuleModel = myIdeaJavaModuleModelFactory.create(gradleModule, syncIssues, externalProject, isBuildable);
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
    // Call all the other resolvers to ensure that any dependencies that they need to provide are added.
    nextResolver.populateModuleDependencies(gradleModule, ideModule, ideProject);
    // In AndroidStudio pre-3.0 all dependencies need to be exported, the common resolvers do not set this.
    // to remedy this we need to go through all datanodes added by other resolvers and set this flag.
    if (myIsImportPre3Dot0) {
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

    DependencyUtilKt.setupAndroidDependenciesForModule(ideModule, (id) -> {
      if (workspace != null) {
        if (resolverCtx.isResolveModulePerSourceSet()){
          // This block is adapted from AOSP/DependencyUtil.kt: Change-Id: Ib67b8cd552866322e5566719d58a9d70421f3865.
          // There is no need to merge this back into AOSP. When merging AOSP (?4.3?) > IJ, it should also go away.

          Map<String, Pair<DataNode<GradleSourceSetData>, ExternalSourceSet>> sourceSetMap =
            ideProject.getUserData(GradleProjectResolver.RESOLVED_SOURCE_SETS);
          if (sourceSetMap == null) return workspace.findModuleDataByModuleId(id);

          Pair<DataNode<GradleSourceSetData>, ExternalSourceSet> mainSourceSet = sourceSetMap.get(id + ":main");
          if (mainSourceSet != null) {
            return mainSourceSet.first.getData();
          } else {
            // Assume that modules per source set are disabled (e.g. android modules). In rare cases this may also mean that
            //  the module should depend on some source set other than "main". We don't know exactly on which one anyway.
            return workspace.findModuleDataByModuleId(id);
          }
        } else {
          return workspace.findModuleDataByModuleId(id);
        }
      }
      return null;
    }, (artifactId, artifactPath) -> {
      AdditionalClassifierArtifacts artifacts = additionalArtifactsMap.get(artifactId);
      if (artifacts == null) {
        return null;
      }
      return new AdditionalArtifactsPaths(artifacts.getSources(), artifacts.getJavadoc(), artifacts.getSampleSources());
    });
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
    isAndroidGradleProject = resolverCtx.hasModulesWithModel(AndroidProject.class) ||
                             resolverCtx.hasModulesWithModel(NativeAndroidProject.class);
    return resolverCtx.putUserDataIfAbsent(IS_ANDROID_PROJECT_KEY, isAndroidGradleProject);
  }

  @Override
  public void populateProjectExtraModels(@NotNull IdeaProject gradleProject, @NotNull DataNode<ProjectData> projectDataNode) {
    populateModuleBuildDirs(gradleProject);
    populateGlobalLibraryMap();
    if (isAndroidGradleProject()) {
      projectDataNode.createChild(PROJECT_CLEANUP_MODEL, ProjectCleanupModel.getInstance());
    }
    super.populateProjectExtraModels(gradleProject, projectDataNode);
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
    List<Build> includedBuilds = resolverCtx.getModels().getIncludedBuilds();
    for (Build includedBuild : includedBuilds) {
      IdeaProject ideaProject = resolverCtx.getModels().getModel(includedBuild, IdeaProject.class);
      assert ideaProject != null;
      ideaProjects.add(ideaProject);
    }

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
  private void populateGlobalLibraryMap() {
    List<GlobalLibraryMap> globalLibraryMaps = new ArrayList<>();

    // Request GlobalLibraryMap for root and included projects.
    Build mainBuild = resolverCtx.getModels().getMainBuild();
    List<Build> includedBuilds = resolverCtx.getModels().getIncludedBuilds();
    List<Build> builds = new ArrayList<>(includedBuilds.size() + 1);
    builds.add(mainBuild);
    builds.addAll(includedBuilds);

    for (Build build : builds) {
      GlobalLibraryMap mapOfCurrentBuild = null;
      // Since GlobalLibraryMap is requested on each module, we need to find the map that was
      // requested at the last, which is the one that contains the most of items.
      for (ProjectModel projectModel : build.getProjects()) {
        GlobalLibraryMap moduleMap = resolverCtx.getModels().getModel(projectModel, GlobalLibraryMap.class);
        if (mapOfCurrentBuild == null || (moduleMap != null && moduleMap.getLibraries().size() > mapOfCurrentBuild.getLibraries().size())) {
          mapOfCurrentBuild = moduleMap;
        }
      }
      if (mapOfCurrentBuild != null) {
        globalLibraryMaps.add(mapOfCurrentBuild);
      }
    }
    myDependenciesFactory.setUpGlobalLibraryMap(globalLibraryMaps);
  }

  /**
   * Note that this method not always used, its functionality is only present when not using a
   * {@link ProjectImportModelProvider}. Any classes added here also need to be requested in the
   * provider.
   */
  @Override
  @NotNull
  public Set<Class<?>> getExtraProjectModelClasses() {
    // Use LinkedHashSet to maintain insertion order.
    // GlobalLibraryMap should be requested after AndroidProject.
    Set<Class<?>> modelClasses = new LinkedHashSet<>();
    modelClasses.add(AndroidProject.class);
    modelClasses.add(NativeAndroidProject.class);
    modelClasses.add(GlobalLibraryMap.class);
    modelClasses.add(GradlePluginModel.class);
    modelClasses.add(ProjectSyncIssues.class);

    // We disable the Kapt resolver extension in studio to solve some issues with duplicate/extra paths.
    // As a result we need to request the model class here so we can still use it.
    modelClasses.add(KaptGradleModel.class);
    return modelClasses;
  }

  @NotNull
  @Override
  public ProjectImportModelProvider getModelProvider() {
    return configureAndGetExtraModelProvider();
  }

  @Override
  public void preImportCheck() {
    // FIXME-ank4: it should be OK to import gradle project in IDEA without Android SDK
    if (!IdeInfo.getInstance().isAndroidStudio()){
      return;
    }

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

    cleanUpHttpProxySettings();
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
    File projectDir = FilePaths.stringToFile(resolverCtx.getProjectPath());
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
  private ProjectImportModelProvider configureAndGetExtraModelProvider() {
    // Here we set up the options for the sync and pass them to the AndroidExtraModelProvider which will decide which will use them
    // to decide which models to request from Gradle.
    Project project = myProjectFinder.findProject(resolverCtx);
    SelectedVariants selectedVariants = null;
    boolean isSingleVariantSync = false;
    Collection<String> cachedLibraries = emptySet();
    String moduleWithVariantSwitched = null;

    if (project != null) {
      isSingleVariantSync = shouldOnlySyncSingleVariant(project);
      if (isSingleVariantSync) {
        SelectedVariantCollector variantCollector = new SelectedVariantCollector(project);
        selectedVariants = variantCollector.collectSelectedVariants();
        moduleWithVariantSwitched = project.getUserData(MODULE_WITH_BUILD_VARIANT_SWITCHED_FROM_UI);
        project.putUserData(MODULE_WITH_BUILD_VARIANT_SWITCHED_FROM_UI, null);
      }
      cachedLibraries = LibraryFilePaths.getInstance(project).retrieveCachedLibs();
    }

    SyncActionOptions options = new SyncActionOptions(
      selectedVariants,
      moduleWithVariantSwitched,
      isSingleVariantSync,
      cachedLibraries,
      StudioFlags.SAMPLES_SUPPORT_ENABLED.get()
    );
    return new AndroidExtraModelProvider(options);
  }

  private static boolean shouldOnlySyncSingleVariant(@NotNull Project project) {
    Boolean shouldOnlySyncSingleVariant = project.getUserData(GradleSyncExecutor.SINGLE_VARIANT_KEY);
    return shouldOnlySyncSingleVariant != null && shouldOnlySyncSingleVariant;
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
    classPath.add(getJarPathForClass(AndroidProject.class));
  }
}
