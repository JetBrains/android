/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.run;

import static com.android.AndroidProjectTypes.PROJECT_TYPE_DYNAMIC_FEATURE;
import static com.android.AndroidProjectTypes.PROJECT_TYPE_INSTANTAPP;
import static com.android.tools.idea.gradle.util.BuildOutputUtil.getOutputFilesFromListingFile;
import static com.android.tools.idea.gradle.util.BuildOutputUtil.getOutputListingFile;
import static com.android.tools.idea.projectsystem.ProjectSystemUtil.getModuleSystem;
import static com.android.tools.idea.projectsystem.gradle.GradleProjectPathKt.getGradleProjectPath;
import static com.android.tools.idea.projectsystem.gradle.GradleProjectPathKt.resolveIn;
import static java.util.Collections.emptyList;

import com.android.builder.model.AppBundleProjectBuildOutput;
import com.android.builder.model.AppBundleVariantBuildOutput;
import com.android.builder.model.InstantAppProjectBuildOutput;
import com.android.builder.model.InstantAppVariantBuildOutput;
import com.android.builder.model.ProjectBuildOutput;
import com.android.builder.model.TestVariantBuildOutput;
import com.android.builder.model.VariantBuildOutput;
import com.android.ddmlib.IDevice;
import com.android.ide.common.build.GenericBuiltArtifacts;
import com.android.ide.common.build.GenericBuiltArtifactsLoader;
import com.android.ide.common.build.GenericBuiltArtifactsSplitOutputMatcher;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.apk.analyzer.AaptInvoker;
import com.android.tools.apk.analyzer.AndroidApplicationInfo;
import com.android.tools.apk.analyzer.ArchiveContext;
import com.android.tools.apk.analyzer.Archives;
import com.android.tools.idea.apk.viewer.ApkParser;
import com.android.tools.idea.gradle.model.IdeAndroidArtifact;
import com.android.tools.idea.gradle.model.IdeAndroidArtifactOutput;
import com.android.tools.idea.gradle.model.IdeAndroidProjectType;
import com.android.tools.idea.gradle.model.IdeBasicVariant;
import com.android.tools.idea.gradle.model.IdePrivacySandboxSdkInfo;
import com.android.tools.idea.gradle.model.IdeTestedTargetVariant;
import com.android.tools.idea.gradle.model.IdeVariant;
import com.android.tools.idea.gradle.project.model.GradleAndroidModel;
import com.android.tools.idea.gradle.project.sync.ModelCache;
import com.android.tools.idea.gradle.run.PostBuildModel;
import com.android.tools.idea.gradle.run.PostBuildModelProvider;
import com.android.tools.idea.gradle.util.BuildOutputUtil;
import com.android.tools.idea.gradle.util.DynamicAppUtils;
import com.android.tools.idea.gradle.util.OutputType;
import com.android.tools.idea.log.LogWrapper;
import com.android.tools.idea.projectsystem.AndroidProjectSettingsService;
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.projectsystem.gradle.GradleHolderProjectPath;
import com.android.tools.idea.projectsystem.gradle.GradleProjectPath;
import com.android.tools.idea.sdk.AndroidSdks;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides the information on APKs to install for run configurations in Gradle projects.
 */
public final class GradleApkProvider implements ApkProvider {
  @NotNull private final AndroidFacet myFacet;
  @NotNull private final GradleApplicationIdProvider myApplicationIdProvider;
  @NotNull private final PostBuildModelProvider myOutputModelProvider;
  private final boolean myTest;

  private final boolean myAlwaysDeployApkFromBundle;
  public static final Key<PostBuildModel> POST_BUILD_MODEL = Key.create("com.android.tools.idea.post_build_model");

  /**
   * Specify where to look for build output APKs
   */
  public enum OutputKind {
    /**
     * Default behavior: Look for build output in the regular Gradle Model output
     */
    Default,
    /**
     * Bundle behavior: Look for build output in the Bundle task output model
     */
    AppBundleOutputModel,
  }

  public GradleApkProvider(@NotNull AndroidFacet facet,
                           @NotNull GradleApplicationIdProvider applicationIdProvider,
                           @NotNull PostBuildModelProvider outputModelProvider,
                           boolean test,
                           boolean alwaysDeployApkFromBundle) {
    myFacet = facet;
    myApplicationIdProvider = applicationIdProvider;
    myOutputModelProvider = outputModelProvider;
    myTest = test;
    myAlwaysDeployApkFromBundle = alwaysDeployApkFromBundle;
  }

  private static OutputKind getOutputKind(@NotNull Module module,
                                          boolean alwaysDeployApkFromBundle,
                                          boolean isTest,
                                          @Nullable AndroidVersion targetDevicesMinVersion) {
    if (DynamicAppUtils.useSelectApksFromBundleBuilder(
      module,
      alwaysDeployApkFromBundle,
      isTest,
      targetDevicesMinVersion
    )) {
      return GradleApkProvider.OutputKind.AppBundleOutputModel;
    }
    else {
      return GradleApkProvider.OutputKind.Default;
    }
  }

  @Override
  @NotNull
  public Collection<ApkInfo> getApks(@NotNull IDevice device) throws ApkProvisionException {
    GradleAndroidModel androidModel = GradleAndroidModel.get(myFacet);
    if (androidModel == null) {
      getLogger().warn("Android model is null. Sync might have failed");
      return Collections.emptyList();
    }
    return getApks(device.getAbis(),
                   device.getVersion(),
                   androidModel,
                   androidModel.getSelectedVariant(),
                   getOutputKind(myFacet.getModule(), myAlwaysDeployApkFromBundle, myTest, device.getVersion()));
  }

  @NotNull
  public List<ApkInfo> getApks(@NotNull List<String> deviceAbis,
                               @NotNull AndroidVersion deviceVersion,
                               @NotNull GradleAndroidModel androidModel,
                               @NotNull IdeVariant variant,
                               @NotNull OutputKind outputKind) throws ApkProvisionException {
    List<ApkInfo> apkList = new ArrayList<>();

    IdeAndroidProjectType projectType = androidModel.getAndroidProject().getProjectType();
    Logger logger = getLogger();
    if (projectType == IdeAndroidProjectType.PROJECT_TYPE_APP ||
        projectType == IdeAndroidProjectType.PROJECT_TYPE_INSTANTAPP ||
        projectType == IdeAndroidProjectType.PROJECT_TYPE_TEST ||
        projectType == IdeAndroidProjectType.PROJECT_TYPE_DYNAMIC_FEATURE) {
      String pkgName = projectType == IdeAndroidProjectType.PROJECT_TYPE_TEST
                       ? myApplicationIdProvider.getTestPackageName()
                       : myApplicationIdProvider.getPackageName();
      if (pkgName == null) {
        logger.warn("Package name is null. Sync might have failed");
        return Collections.emptyList();
      }

      switch (outputKind) {
        case Default:
          // Collect the base (or single) APK file, then collect the dependent dynamic features for dynamic
          // apps (assuming the androidModel is the base split).
          //
          // Note: For instant apps, "getApk" currently returns a ZIP to be provisioned on the device instead of
          //       a .apk file, the "collectDependentFeaturesApks" is a no-op for instant apps.
          List<ApkFileUnit> apkFileList = new ArrayList<>();
          apkFileList.add(new ApkFileUnit(androidModel.getModuleName(),
                                          getApk(variant.getName(), variant.getMainArtifact(), deviceAbis, deviceVersion,
                                                 myFacet
                                          )));
          apkFileList.addAll(collectDependentFeaturesApks(androidModel, deviceAbis, deviceVersion));
          if (variant.getMainArtifact().getPrivacySandboxSdkInfo() != null) {
            apkList.addAll(getApksForPrivacySandboxSdks(
              variant.getName(),
              variant.getMainArtifact().getPrivacySandboxSdkInfo(),
              variant.getMainArtifact().getAbiFilters(),
              deviceAbis
            ));
          }
          apkList.add(new ApkInfo(apkFileList, pkgName));
          break;

        case AppBundleOutputModel:
          Module baseAppModule = myFacet.getModule();
          if (projectType == IdeAndroidProjectType.PROJECT_TYPE_DYNAMIC_FEATURE) {
            // If it's instrumented test for dynamic feature module, the base-app module is retrieved,
            // and then Apks from bundle are able to be extracted.
            baseAppModule = DynamicAppUtils.getBaseFeature(myFacet.getModule());
          }
          if (baseAppModule != null) {
            ApkInfo apkInfo = collectAppBundleOutput(baseAppModule, myOutputModelProvider, pkgName);
            if (apkInfo != null) {
              apkList.add(apkInfo);
            }
          }
          break;
      }
    }

    apkList.addAll(getAdditionalApks(variant.getMainArtifact()));

    if (myTest) {
      if (projectType == IdeAndroidProjectType.PROJECT_TYPE_TEST) {
        apkList.addAll(0, getTargetedApks(variant, deviceAbis, deviceVersion));
      }
      else {
        IdeAndroidArtifact testArtifactInfo = androidModel.getSelectedVariant().getAndroidTestArtifact();
        if (testArtifactInfo != null) {
          File testApk =
            getApk(androidModel.getSelectedVariant().getName(), getAndroidTestArtifact(androidModel.getSelectedVariant()), deviceAbis,
                   deviceVersion, myFacet
            );
          String testPackageName = myApplicationIdProvider.getTestPackageName();
          assert testPackageName != null; // Cannot be null if initialized.
          apkList.add(new ApkInfo(testApk, testPackageName));
          apkList.addAll(getAdditionalApks(testArtifactInfo));
        }
      }
    }
    logger.info(String.format("APKs for %s:", this.myFacet.getModule()));
    for (ApkInfo info : apkList) {
      logger.info(String.format("  %s =>", info.getApplicationId()));
      for (ApkFileUnit file : info.getFiles()) {
        logger.info(String.format("    %s : %s", file.getModuleName(), file.getApkFile()));
      }
    }

    return apkList;
  }

  @NotNull
  private List<ApkFileUnit> collectDependentFeaturesApks(@NotNull GradleAndroidModel androidModel,
                                                         @NotNull List<String> deviceAbis,
                                                         @NotNull AndroidVersion deviceVersion) {
    return getModuleSystem(myFacet).getDynamicFeatureModules()
      .stream()
      .map(module -> {
        // Find the output APK of the module
        AndroidFacet featureFacet = AndroidFacet.getInstance(module);
        if (featureFacet == null) {
          return null;
        }
        GradleAndroidModel androidFeatureModel = GradleAndroidModel.get(featureFacet);
        if (androidFeatureModel == null) {
          return null;
        }
        IdeVariant selectedVariant = androidFeatureModel.getSelectedVariant();
        try {
          File apk = getApk(selectedVariant.getName(), selectedVariant.getMainArtifact(), deviceAbis, deviceVersion, featureFacet
          );
          return new ApkFileUnit(androidFeatureModel.getModuleName(), apk);
        }
        catch (ApkProvisionException e) {
          //TODO: Is this the right thing to do?
          return null;
        }
      })
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
  }

  /**
   * Returns ApkInfo objects for all runtime apks.
   * <p>
   * For each of the additional runtime apks it finds its package id and creates ApkInfo object it.
   * Thus it returns information for all runtime apks.
   *
   * @param testArtifactInfo test android artifact
   * @return a list of ApkInfo objects for each additional runtime Apk
   */
  @NotNull
  private static List<ApkInfo> getAdditionalApks(@NotNull IdeAndroidArtifact testArtifactInfo) {
    List<ApkInfo> result = new ArrayList<>();
    for (File fileApk : testArtifactInfo.getAdditionalRuntimeApks()) {
      try {
        String packageId = getPackageId(fileApk);
        result.add(new ApkInfo(fileApk, packageId,
                               // TODO: Read the install options from AGP json.
                               ImmutableSet.of(ApkInfo.AppInstallOption.FORCE_QUERYABLE,
                                               ApkInfo.AppInstallOption.GRANT_ALL_PERMISSIONS)));
      }
      catch (ApkProvisionException e) {
        getLogger().error(
          "Failed to get the package name from the given file. Therefore, we are not be able to install it. Please install it manually: " +
          fileApk.getName() +
          " error: " +
          e.getMessage(), e);
      }
    }
    return result;
  }

  private static Path getPathToAapt() {
    AndroidSdkHandler handler = AndroidSdks.getInstance().tryToChooseSdkHandler();
    return AaptInvoker.getPathToAapt(handler, new LogWrapper(GradleApkProvider.class));
  }

  private static String getPackageId(@NotNull File fileApk) throws ApkProvisionException {
    try (ArchiveContext archiveContext = Archives.open(fileApk.toPath())) {
      AndroidApplicationInfo applicationInfo = ApkParser.getAppInfo(getPathToAapt(), archiveContext.getArchive());
      if (applicationInfo == AndroidApplicationInfo.UNKNOWN) {
        throw new ApkProvisionException("Could not determine manifest package for apk: " + fileApk.getName());
      }
      else {
        return applicationInfo.packageId;
      }
    }
    catch (IOException e) {
      throw new ApkProvisionException("Could not determine manifest package for apk: " + fileApk.getName(), e.getCause());
    }
  }

  @VisibleForTesting
  @NotNull
  File getApk(@NotNull String variantName,
              @NotNull IdeAndroidArtifact artifact,
              @NotNull List<String> deviceAbis,
              @NotNull AndroidVersion deviceVersion,
              @NotNull AndroidFacet facet) throws ApkProvisionException {
    GradleAndroidModel androidModel = GradleAndroidModel.get(facet);
    assert androidModel != null;
    if (androidModel.getFeatures().isBuildOutputFileSupported()) {
      return getApkFromBuildOutputFile(variantName, artifact, deviceAbis);
    }
    if (androidModel.getFeatures().isPostBuildSyncSupported()) {
      return getApkFromPostBuildSync(variantName, artifact, deviceAbis, deviceVersion, facet
      );
    }
    throw new IllegalStateException(
      "AGP 3.1.0 and later support either post build models or build output listing files. " +
      "However, neither is available.");
  }

  @NotNull
  private File getApkFromBuildOutputFile(@NotNull String variantName,
                                         @NotNull IdeAndroidArtifact artifact,
                                         @NotNull List<String> deviceAbis)
    throws ApkProvisionException {
    String outputFile = getOutputListingFile(artifact.getBuildInformation(), OutputType.Apk);
    if (outputFile == null) {
      throw new ApkProvisionException("Cannot get output listing file name from the build model");
    }
    GenericBuiltArtifacts builtArtifacts = GenericBuiltArtifactsLoader.loadFromFile(new File(outputFile), new LogWrapper(getLogger()));
    if (builtArtifacts == null) {
      throw new ApkProvisionException(String.format("Error loading build artifacts from: %s", outputFile));
    }
    return findBestOutput(variantName, artifact.getAbiFilters(), deviceAbis, builtArtifacts);
  }

  @NotNull
  private List<ApkInfo> getApksForPrivacySandboxSdks(
    @NotNull String variantName,
    @NotNull IdePrivacySandboxSdkInfo privacySandboxSdkInfo,
    @NotNull Set<String> abiFilters,
    @NotNull List<String> deviceAbis) throws ApkProvisionException {

    File outputFile = privacySandboxSdkInfo.getOutputListingFile();
    List<GenericBuiltArtifacts> builtArtifacts = GenericBuiltArtifactsLoader.loadListFromFile(outputFile, new LogWrapper(getLogger()));
    if (builtArtifacts.isEmpty()) {
      throw new ApkProvisionException(String.format("Error loading build artifacts from: %s", outputFile));
    }

    List<ApkInfo> list = new ArrayList<>();
    for (GenericBuiltArtifacts builtArtifact : builtArtifacts) {
      ApkInfo unit = new ApkInfo(findBestOutput(variantName, abiFilters, deviceAbis, builtArtifact), builtArtifact.getApplicationId());
      list.add(unit);
    }
    return list;
  }


  @NotNull
  public static IdeAndroidArtifact getAndroidTestArtifact(@NotNull IdeVariant variant) throws ApkProvisionException {
    if (variant.getAndroidTestArtifact() == null) {
      throw new ApkProvisionException(String.format("AndroidTest artifact is not configured in %s variant.", variant.getDisplayName()));
    }
    return variant.getAndroidTestArtifact();
  }

  @NotNull
  @VisibleForTesting
  File getApkFromPostBuildSync(@NotNull String variantName,
                               @NotNull IdeAndroidArtifact artifact,
                               @NotNull List<String> deviceAbis,
                               @NotNull AndroidVersion deviceVersion,
                               @NotNull AndroidFacet facet) throws ApkProvisionException {
    List<IdeAndroidArtifactOutput> outputs = new ArrayList<>();

    PostBuildModel outputModels = myOutputModelProvider.getPostBuildModel();
    if (outputModels == null) {
      throw new ApkProvisionException(
        String.format("Couldn't get post build model. Module: %s Variant: %s", facet.getModule().getName(), variantName));
    }

    ModelCache.V1 modelCache = ModelCache.createForPostBuildModels();
    if (facet.getConfiguration().getProjectType() == PROJECT_TYPE_INSTANTAPP) {
      InstantAppProjectBuildOutput outputModel =
        outputModels.findInstantAppProjectBuildOutput(getGradlePathAsStringForPostBuildModels(facet.getModule()));
      if (outputModel == null) {
        throw new ApkProvisionException(
          "Couldn't get post build model for Instant Apps. Please, make sure to use plugin 3.0.0-alpha10 or later.");
      }

      for (InstantAppVariantBuildOutput instantAppVariantBuildOutput : outputModel.getInstantAppVariantsBuildOutput()) {
        if (instantAppVariantBuildOutput.getName().equals(variantName)) {
          outputs.add(modelCache.androidArtifactOutputFrom(instantAppVariantBuildOutput.getOutput()));
        }
      }
    }
    else {
      @SuppressWarnings("deprecation")
      ProjectBuildOutput outputModel =
        outputModels.findProjectBuildOutput(getGradlePathAsStringForPostBuildModels(facet.getModule()));
      if (outputModel == null) {
        throw new ApkProvisionException(
          String.format("Couldn't get post build model. Module: %s Variant: %s", facet.getModule().getName(), variantName));
      }

      // Loop through the variants in the model and get the one that matches
      //noinspection deprecation
      for (VariantBuildOutput variantBuildOutput : outputModel.getVariantsBuildOutput()) {
        if (variantBuildOutput.getName().equals(variantName)) {

          if (artifact.isTestArtifact()) {
            // Get the output from the test artifact
            for (TestVariantBuildOutput testVariantBuildOutput : variantBuildOutput.getTestingVariants()) {
              if (testVariantBuildOutput.getType().equals(TestVariantBuildOutput.ANDROID_TEST)) {
                int apiWithSplitApk = AndroidVersion.ALLOW_SPLIT_APK_INSTALLATION.getApiLevel();
                if (facet.getConfiguration().getProjectType() == PROJECT_TYPE_DYNAMIC_FEATURE &&
                    !deviceVersion.isGreaterOrEqualThan(apiWithSplitApk)) {
                  // b/119663247
                  throw new ApkProvisionException(
                    "Running Instrumented Tests for Dynamic Features is currently not supported on API < 21.");
                }
                outputs.addAll(ContainerUtil.map(testVariantBuildOutput.getOutputs(), modelCache::androidArtifactOutputFrom));
              }
            }
          }
          else {
            // Get the output from the main artifact
            outputs.addAll(ContainerUtil.map(variantBuildOutput.getOutputs(), modelCache::androidArtifactOutputFrom));
          }
        }
      }
    }

    Set<String> abiFilters = artifact.getAbiFilters();
    return findBestOutput(variantName, abiFilters, deviceAbis, outputs);
  }

  /**
   * Gets the list of targeted apks for the specified variant.
   *
   * <p>This is used for test-only modules when specifying the tested apk
   * using the targetProjectPath and targetVariant properties in the build file.
   */
  @NotNull
  private List<ApkInfo> getTargetedApks(@NotNull IdeVariant selectedVariant,
                                        @NotNull List<String> deviceAbis,
                                        @NotNull AndroidVersion deviceVersion) throws ApkProvisionException {
    List<ApkInfo> targetedApks = new ArrayList<>();

    for (IdeTestedTargetVariant testedVariant : selectedVariant.getTestedTargetVariants()) {
      String targetGradlePath = testedVariant.getTargetProjectPath();
      Module targetModule = ApplicationManager.getApplication().runReadAction(
        (Computable<Module>)() -> {
          Project project = myFacet.getModule().getProject();
          GradleProjectPath projectPath = getGradleProjectPath(myFacet.getHolderModule());
          if (projectPath == null) return null;
          GradleProjectPath targetProjectPath = new GradleHolderProjectPath(projectPath.getBuildRoot(), targetGradlePath);
          return resolveIn(targetProjectPath, project);
        });

      if (targetModule == null) {
        getLogger().warn(String.format("Module not found for gradle path %s. Please install tested apk manually.", targetGradlePath));
        continue;
      }

      AndroidFacet targetFacet = AndroidFacet.getInstance(targetModule);
      if (targetFacet == null) {
        getLogger().warn("Android facet for tested module is null. Please install tested apk manually.");
        continue;
      }

      GradleAndroidModel targetAndroidModel = GradleAndroidModel.get(targetModule);
      if (targetAndroidModel == null) {
        getLogger().warn("Android model for tested module is null. Sync might have failed.");
        continue;
      }

      IdeVariant targetVariant = targetAndroidModel.findVariantByName(testedVariant.getTargetVariant());
      IdeBasicVariant targetBasicVariant = targetAndroidModel.findBasicVariantByName(testedVariant.getTargetVariant());
      if (targetBasicVariant == null) {
        getLogger().warn("Tested variant not found. Sync might have failed.");
        continue;
      }

      File targetApk = getApk(targetVariant.getName(), targetVariant.getMainArtifact(), deviceAbis, deviceVersion, targetFacet);

      String applicationId =
        GradleApplicationIdProvider.createForBaseModule(targetFacet, targetAndroidModel, targetBasicVariant).getPackageName();
      targetedApks.add(new ApkInfo(targetApk, applicationId));
    }

    return targetedApks;
  }

  @NotNull
  public static ImmutableList<ValidationError> doValidate(@NotNull AndroidFacet androidFacet,
                                                          boolean isTest,
                                                          boolean alwaysDeployApkFromBundle) {
    ImmutableList.Builder<ValidationError> result = ImmutableList.builder();

    GradleAndroidModel androidModuleModel = GradleAndroidModel.get(androidFacet);
    if (androidModuleModel == null) {
      Runnable requestProjectSync =
        () -> ProjectSystemUtil.getSyncManager(androidFacet.getModule().getProject())
          .syncProject(ProjectSystemSyncManager.SyncReason.USER_REQUEST);
      result.add(ValidationError.fatal("The project has not yet been synced with Gradle configuration", requestProjectSync));
      return result.build();
    }

    if (alwaysDeployApkFromBundle) {
      if (StringUtil.isEmpty(androidModuleModel.getSelectedVariant().getMainArtifact().getBuildInformation().getBundleTaskName())) {
        ValidationError error = ValidationError.fatal("Bundle task not supported for module '" + androidFacet.getModule().getName() + "'");
        result.add(error);
      }
    }

    if (isTest) {
      IdeAndroidArtifact testArtifact = androidModuleModel.getArtifactForAndroidTest();
      if (testArtifact == null) {
        IdeVariant selectedVariant = androidModuleModel.getSelectedVariant();
        result.add(ValidationError.warning("Active build variant \"" + selectedVariant.getName() + "\" does not have a test artifact."));
      }
    }

    // Note: Instant apps and app bundles outputs are assumed to be signed
    AndroidVersion targetDevicesMinVersion = null; // NOTE: ApkProvider.validate() runs in a device-less context.
    Module module = androidFacet.getModule();
    //noinspection ConstantConditions
    if (androidModuleModel.getAndroidProject().getProjectType() == IdeAndroidProjectType.PROJECT_TYPE_INSTANTAPP ||
        GradleApkProvider.getOutputKind(module,
                                        alwaysDeployApkFromBundle,
                                        isTest,
                                        targetDevicesMinVersion
        ) == OutputKind.AppBundleOutputModel ||
        GradleApkProvider.isArtifactSigned(androidModuleModel, isTest)) {
      return result.build();
    }

    final String message =
      AndroidBundle.message("run.error.apk.not.signed", androidModuleModel.getSelectedVariant().getDisplayName());

    Runnable quickFix = () -> {
      ProjectSettingsService service = ProjectSettingsService.getInstance(module.getProject());
      if (service instanceof AndroidProjectSettingsService) {
        ((AndroidProjectSettingsService)service).openSigningConfiguration(module);
      }
      else {
        service.openModuleSettings(module);
      }
    };

    result.add(ValidationError.fatal(message, quickFix));
    return result.build();
  }

  /**
   * Returns an {@link ApkInfo} instance containing all the APKS generated by the "select apks from bundle" Gradle task, or {@code null}
   * in case of unexpected error.
   */
  @Nullable
  private static ApkInfo collectAppBundleOutput(@NotNull Module module,
                                                @NotNull PostBuildModelProvider outputModelProvider,
                                                @NotNull String pkgName) {
    GradleAndroidModel androidModel = GradleAndroidModel.get(module);
    if (androidModel == null) {
      getLogger().warn("Android model is null. Sync might have failed");
      return null;
    }

    List<File> apkFiles;
    if (androidModel.getFeatures().isBuildOutputFileSupported()) {
      String outputListingFile = BuildOutputUtil
        .getOutputListingFile(androidModel.getSelectedVariant().getMainArtifact().getBuildInformation(),
                              OutputType.ApkFromBundle);
      apkFiles = outputListingFile != null ? getOutputFilesFromListingFile(outputListingFile) : emptyList();
    }
    else {
      apkFiles = collectApkFilesFromPostBuildModel(outputModelProvider,
                                                   getGradlePathAsStringForPostBuildModels(module),
                                                   androidModel.getSelectedVariant().getName());
    }

    if (apkFiles.isEmpty()) {
      getLogger().warn("Could not find apk files.");
      return null;
    }

    // List all files in the folder
    List<ApkFileUnit> apks = apkFiles.stream()
      .map(path -> new ApkFileUnit(getFeatureNameFromPathHack(path.toPath()), path))
      .collect(Collectors.toList());
    return new ApkInfo(apks, pkgName);
  }

  /**
   * Returns the APKS generated by the "select apks from bundle" Gradle task, this method retrieves
   * the folder from post build model. This method should be used for AGP prior to 4.0.
   */
  @NotNull
  private static List<File> collectApkFilesFromPostBuildModel(@NotNull PostBuildModelProvider outputModelProvider,
                                                              @Nullable String gradlePath,
                                                              @Nullable String variantName) {
    PostBuildModel model = outputModelProvider.getPostBuildModel();
    if (model == null) {
      getLogger().warn("Post build model is null. Build might have failed.");
      return emptyList();
    }
    AppBundleProjectBuildOutput output = model.findAppBundleProjectBuildOutput(gradlePath);
    if (output == null) {
      getLogger().warn("Project output is null. Build may have failed.");
      return emptyList();
    }

    for (AppBundleVariantBuildOutput variantBuildOutput : output.getAppBundleVariantsBuildOutput()) {
      if (variantBuildOutput.getName().equals(variantName)) {
        try (Stream<Path> stream = Files.list(variantBuildOutput.getApkFolder().toPath())) {
          return stream.map(Path::toFile).collect(Collectors.toList());
        }
        catch (IOException e) {
          getLogger()
            .warn(String.format("Error reading list of APK files from bundle build output directory \"%s\".",
                                variantBuildOutput.getApkFolder()),
                  e);
          return emptyList();
        }
      }
    }

    getLogger().warn("Bundle variant build output model has no entries. Build may have failed.");
    return emptyList();
  }

  /**
   * TODO: Until the "selectApks" tasks lets us specify the list of features, we rely on the fact
   * the file names created by the bundle tool are of the form "featureName-xxx.apk" where
   * "xxx" is a single word name used by the bundle tool (e.g. "mdpi", "master")
   */
  @NotNull
  private static String getFeatureNameFromPathHack(@NotNull Path path) {
    String fileName = path.getFileName().toString();
    int separatorIndex = fileName.lastIndexOf('-');
    if (separatorIndex < 0) {
      return "";
    }

    return fileName.substring(0, separatorIndex);
  }

  private static boolean isArtifactSigned(GradleAndroidModel androidModuleModel, boolean isTest) {
    IdeAndroidArtifact artifact = isTest ? androidModuleModel.getArtifactForAndroidTest() : androidModuleModel.getMainArtifact();
    return artifact != null && artifact.isSigned();
  }

  @NotNull
  private static Logger getLogger() {
    return Logger.getInstance(GradleApkProvider.class);
  }

  @NotNull
  static File findBestOutput(@NotNull String variantName,
                             @NotNull Set<String> artifactAbiFilters,
                             @NotNull List<String> abis,
                             @NotNull List<IdeAndroidArtifactOutput> outputs) throws ApkProvisionException {
    if (outputs.isEmpty()) {
      throw new ApkProvisionException("No outputs for the main artifact of variant: " + variantName);
    }
    List<File> apkFiles =
      ContainerUtil.map(SplitOutputMatcher.computeBestOutput(outputs, artifactAbiFilters, abis), IdeAndroidArtifactOutput::getOutputFile);
    verifyApkCollectionIsNotEmpty(variantName, apkFiles, abis, outputs.size());
    // Install apk (note that variant.getOutputFile() will point to a .aar in the case of a library).
    return apkFiles.get(0);
  }

  @NotNull
  static File findBestOutput(@NotNull String variantName,
                             @NotNull Set<String> artifactAbiFilters,
                             @NotNull List<String> abis,
                             @NotNull GenericBuiltArtifacts builtArtifact) throws ApkProvisionException {
    List<File> apkFiles = GenericBuiltArtifactsSplitOutputMatcher.INSTANCE.computeBestOutput(builtArtifact, artifactAbiFilters, abis);
    verifyApkCollectionIsNotEmpty(variantName, apkFiles, abis, builtArtifact.getElements().size());
    // Install apk (note that variant.getOutputFile() will point to a .aar in the case of a library).
    return apkFiles.get(0);
  }

  private static void verifyApkCollectionIsNotEmpty(@NotNull String variantName,
                                                    @NotNull List<File> apkFiles,
                                                    @NotNull List<String> abis,
                                                    int outputCount)
    throws ApkProvisionException {
    if (apkFiles.isEmpty()) {
      String message = AndroidBundle.message("deployment.failed.splitapk.nomatch",
                                             variantName,
                                             outputCount,
                                             Joiner.on(", ").join(abis));
      throw new ApkProvisionException(message);
    }
  }

  @Nullable
  private static String getGradlePathAsStringForPostBuildModels(@NotNull Module module) {
    GradleProjectPath gradleProjectPath = getGradleProjectPath(module);
    return gradleProjectPath != null ? gradleProjectPath.getPath() : null;
  }
}
