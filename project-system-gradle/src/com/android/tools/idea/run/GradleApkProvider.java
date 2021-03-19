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
import static com.android.tools.idea.gradle.util.GradleBuildOutputUtil.getOutputFileOrFolderFromListingFile;
import static com.android.tools.idea.gradle.util.GradleBuildOutputUtil.getOutputListingFile;
import static com.android.tools.idea.gradle.util.GradleUtil.findModuleByGradlePath;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradlePath;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;

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
import com.android.ide.common.gradle.model.IdeAndroidArtifact;
import com.android.ide.common.gradle.model.IdeAndroidArtifactOutput;
import com.android.ide.common.gradle.model.IdeAndroidProject;
import com.android.ide.common.gradle.model.IdeAndroidProjectType;
import com.android.ide.common.gradle.model.IdeTestedTargetVariant;
import com.android.ide.common.gradle.model.IdeVariant;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.apk.analyzer.AaptInvoker;
import com.android.tools.apk.analyzer.AndroidApplicationInfo;
import com.android.tools.apk.analyzer.ArchiveContext;
import com.android.tools.apk.analyzer.Archives;
import com.android.tools.idea.apk.viewer.ApkParser;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.ModelCache;
import com.android.tools.idea.gradle.run.PostBuildModel;
import com.android.tools.idea.gradle.run.PostBuildModelProvider;
import com.android.tools.idea.gradle.util.DynamicAppUtils;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.gradle.util.OutputType;
import com.android.tools.idea.log.LogWrapper;
import com.android.tools.idea.projectsystem.AndroidProjectSettingsService;
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
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
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * Provides the information on APKs to install for run configurations in Gradle projects.
 */
public class GradleApkProvider implements ApkProvider {
  @NotNull private final AndroidFacet myFacet;
  @NotNull private final GradleApplicationIdProvider myApplicationIdProvider;
  @NotNull private final PostBuildModelProvider myOutputModelProvider;
  @NotNull private final BestOutputFinder myBestOutputFinder;
  private final boolean myTest;
  private final Function<AndroidVersion, OutputKind> myOutputKindProvider;

  public static final Key<PostBuildModel> POST_BUILD_MODEL = Key.create("com.android.tools.idea.post_build_model");

  private static final String VARIANT_DISPLAY_NAME_STUB = "<VARIANT_DISPLAY_NAME>";

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
                           @NotNull Function<AndroidVersion, OutputKind> outputKindProvider) {
    this(facet, applicationIdProvider, outputModelProvider, new BestOutputFinder(), test, outputKindProvider);
  }

  @VisibleForTesting
  public GradleApkProvider(@NotNull AndroidFacet facet,
                           @NotNull GradleApplicationIdProvider applicationIdProvider,
                           boolean test) {
    this(facet, applicationIdProvider, () -> null, test, version -> OutputKind.Default);
  }

  @VisibleForTesting
  public GradleApkProvider(@NotNull AndroidFacet facet,
                           @NotNull GradleApplicationIdProvider applicationIdProvider,
                           @NotNull PostBuildModelProvider outputModelProvider,
                           boolean test) {
    this(facet, applicationIdProvider, outputModelProvider, new BestOutputFinder(), test, version -> OutputKind.Default);
  }

  @VisibleForTesting
  GradleApkProvider(@NotNull AndroidFacet facet,
                    @NotNull GradleApplicationIdProvider applicationIdProvider,
                    @NotNull PostBuildModelProvider outputModelProvider,
                    @NotNull BestOutputFinder bestOutputFinder,
                    boolean test,
                    Function<AndroidVersion, OutputKind> outputKindProvider) {
    myFacet = facet;
    myApplicationIdProvider = applicationIdProvider;
    myOutputModelProvider = outputModelProvider;
    myBestOutputFinder = bestOutputFinder;
    myTest = test;
    myOutputKindProvider = outputKindProvider;
  }

  @TestOnly
  OutputKind getOutputKind(@Nullable AndroidVersion targetDevicesMinVersion) { return myOutputKindProvider.apply(targetDevicesMinVersion); }

  @TestOnly
  boolean isTest() { return myTest; }

  @Override
  @NotNull
  public Collection<ApkInfo> getApks(@NotNull IDevice device) throws ApkProvisionException {
    return getApks(device.getAbis(), device.getVersion());
  }

  @VisibleForTesting
  @NotNull
  public List<ApkInfo> getApks(List<String> deviceAbis, AndroidVersion deviceVersion) throws ApkProvisionException {
    AndroidModuleModel androidModel = AndroidModuleModel.get(myFacet);
    if (androidModel == null) {
      getLogger().warn("Android model is null. Sync might have failed");
      return Collections.emptyList();
    }
    IdeVariant selectedVariant = androidModel.getSelectedVariant();
    try {
      List<ApkInfo> apkList = new ArrayList<>();

      IdeAndroidProjectType projectType = androidModel.getAndroidProject().getProjectType();
      if (projectType == IdeAndroidProjectType.PROJECT_TYPE_APP ||
          projectType == IdeAndroidProjectType.PROJECT_TYPE_INSTANTAPP ||
          projectType == IdeAndroidProjectType.PROJECT_TYPE_TEST ||
          projectType == IdeAndroidProjectType.PROJECT_TYPE_DYNAMIC_FEATURE) {
        String pkgName = projectType == IdeAndroidProjectType.PROJECT_TYPE_TEST
                         ? myApplicationIdProvider.getTestPackageName()
                         : myApplicationIdProvider.getPackageName();
        if (pkgName == null) {
          getLogger().warn("Package name is null. Sync might have failed");
          return Collections.emptyList();
        }

        switch (myOutputKindProvider.apply(deviceVersion)) {
          case Default:
            // Collect the base (or single) APK file, then collect the dependent dynamic features for dynamic
            // apps (assuming the androidModel is the base split).
            //
            // Note: For instant apps, "getApk" currently returns a ZIP to be provisioned on the device instead of
            //       a .apk file, the "collectDependentFeaturesApks" is a no-op for instant apps.
            List<ApkFileUnit> apkFileList = new ArrayList<>();
            apkFileList.add(new ApkFileUnit(androidModel.getModuleName(),
                                            getApk(selectedVariant.getName(), selectArtifact(selectedVariant, false), deviceAbis, deviceVersion, myFacet, false
                                            )));
            apkFileList.addAll(collectDependentFeaturesApks(androidModel, deviceAbis, deviceVersion));
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

      apkList.addAll(getAdditionalApks(selectedVariant.getMainArtifact()));

      if (myTest) {
        if (projectType == IdeAndroidProjectType.PROJECT_TYPE_TEST) {
          if (androidModel.getFeatures().isTestedTargetVariantsSupported()) {
            apkList.addAll(0, getTargetedApks(selectedVariant, deviceAbis, deviceVersion));
          }
        }
        else {
          IdeAndroidArtifact testArtifactInfo = androidModel.getSelectedVariant().getAndroidTestArtifact();
          if (testArtifactInfo != null) {
            File testApk = getApk(androidModel.getSelectedVariant().getName(), selectArtifact(androidModel.getSelectedVariant(), true), deviceAbis, deviceVersion, myFacet, true
            );
            String testPackageName = myApplicationIdProvider.getTestPackageName();
            assert testPackageName != null; // Cannot be null if initialized.
            apkList.add(new ApkInfo(testApk, testPackageName));
            apkList.addAll(getAdditionalApks(testArtifactInfo));
          }
        }
      }
      return apkList;
    }
    catch (ApkProvisionException apkProvisioningException) {
      if (apkProvisioningException.getMessage() == null || !apkProvisioningException.getMessage().contains(VARIANT_DISPLAY_NAME_STUB)) {
        throw apkProvisioningException;
      }
      throw new ApkProvisionException(
        apkProvisioningException.getMessage().replace(VARIANT_DISPLAY_NAME_STUB, selectedVariant.getDisplayName()),
        apkProvisioningException);
    }
  }

  @NotNull
  private List<ApkFileUnit> collectDependentFeaturesApks(@NotNull AndroidModuleModel androidModel,
                                                         @NotNull List<String> deviceAbis,
                                                         @NotNull AndroidVersion deviceVersion) {
    IdeAndroidProject project = androidModel.getAndroidProject();
    return DynamicAppUtils.getDependentFeatureModulesForBase(myFacet.getModule().getProject(), project)
      .stream()
      .map(module -> {
        // Find the output APK of the module
        AndroidFacet featureFacet = AndroidFacet.getInstance(module);
        if (featureFacet == null) {
          return null;
        }
        AndroidModuleModel androidFeatureModel = AndroidModuleModel.get(featureFacet);
        if (androidFeatureModel == null) {
          return null;
        }
        IdeVariant selectedVariant = androidFeatureModel.getSelectedVariant();
        try {
          File apk = getApk(selectedVariant.getName(), selectArtifact(selectedVariant, false), deviceAbis, deviceVersion, featureFacet, false
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
              @NotNull AndroidFacet facet,
              boolean fromTestArtifact) throws ApkProvisionException {
    AndroidModuleModel androidModel = AndroidModuleModel.get(facet);
    assert androidModel != null;
    if (androidModel.getFeatures().isBuildOutputFileSupported()) {
      return getApkFromBuildOutputFile(artifact, deviceAbis);
    }
    if (androidModel.getFeatures().isPostBuildSyncSupported()) {
      return getApkFromPostBuildSync(variantName, artifact, deviceAbis, deviceVersion, facet, fromTestArtifact
      );
    }
    return getApkFromPreBuildSync(artifact, deviceAbis);
  }

  @NotNull
  private File getApkFromBuildOutputFile(@NotNull IdeAndroidArtifact artifact, @NotNull List<String> deviceAbis) throws ApkProvisionException {
    String outputFile = getOutputListingFile(artifact.getBuildInformation(), OutputType.Apk);
    if (outputFile == null) {
      throw new ApkProvisionException("Cannot get output listing file name from the build model");
    }
    GenericBuiltArtifacts builtArtifacts = GenericBuiltArtifactsLoader.loadFromFile(new File(outputFile), new LogWrapper(getLogger()));
    if (builtArtifacts == null) {
      throw new ApkProvisionException(String.format("Error loading build artifacts from: %s", outputFile));
    }
    return myBestOutputFinder
      .findBestOutput(artifact.getAbiFilters(), deviceAbis, builtArtifacts);
  }

  @NotNull
  @VisibleForTesting
  File getApkFromPreBuildSync(@NotNull IdeAndroidArtifact artifact, @NotNull List<String> deviceAbis) throws ApkProvisionException {
    @SuppressWarnings("deprecation") List<IdeAndroidArtifactOutput> outputs = new ArrayList<>(artifact.getOutputs());
    return myBestOutputFinder.findBestOutput(artifact.getAbiFilters(), deviceAbis, outputs);
  }

  @NotNull
  public static IdeAndroidArtifact selectArtifact(@NotNull IdeVariant variant, boolean fromTestArtifact) throws ApkProvisionException {
    if (fromTestArtifact) {
      if (variant.getAndroidTestArtifact() == null) {
        throw new ApkProvisionException(String.format("AndroidTest artifact is not configured in %s variant.", variant.getDisplayName()));
      }
      return variant.getAndroidTestArtifact();
    }
    return variant.getMainArtifact();
  }

  @NotNull
  @VisibleForTesting
  File getApkFromPostBuildSync(@NotNull String variantName,
                               @NotNull IdeAndroidArtifact artifact,
                               @NotNull List<String> deviceAbis,
                               @NotNull AndroidVersion deviceVersion,
                               @NotNull AndroidFacet facet,
                               boolean fromTestArtifact) throws ApkProvisionException {
    List<IdeAndroidArtifactOutput> outputs = new ArrayList<>();

    PostBuildModel outputModels = myOutputModelProvider.getPostBuildModel();
    if (outputModels == null) {
      return getApkFromPreBuildSync(artifact, deviceAbis);
    }

    ModelCache modelCache = ModelCache.create();
    if (facet.getConfiguration().getProjectType() == PROJECT_TYPE_INSTANTAPP) {
      InstantAppProjectBuildOutput outputModel = outputModels.findInstantAppProjectBuildOutput(getGradlePath(facet.getModule()));
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
      ProjectBuildOutput outputModel = outputModels.findProjectBuildOutput(getGradlePath(facet.getModule()));
      if (outputModel == null) {
        return getApkFromPreBuildSync(artifact, deviceAbis);
      }

      // Loop through the variants in the model and get the one that matches
      //noinspection deprecation
      for (VariantBuildOutput variantBuildOutput : outputModel.getVariantsBuildOutput()) {
        if (variantBuildOutput.getName().equals(variantName)) {

          if (fromTestArtifact) {
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

    // If empty, it means that either ProjectBuildOut has not been filled correctly or the variant was not found.
    // In this case we try to get an APK known at sync time, if any.
    if (outputs.isEmpty()) {
      return getApkFromPreBuildSync(artifact, deviceAbis);
    }
    Set<String> abiFilters = artifact.getAbiFilters();
    return myBestOutputFinder.findBestOutput(abiFilters, deviceAbis, outputs);
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
          return findModuleByGradlePath(project, targetGradlePath);
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

      AndroidModuleModel targetAndroidModel = AndroidModuleModel.get(targetModule);
      if (targetAndroidModel == null) {
        getLogger().warn("Android model for tested module is null. Sync might have failed.");
        continue;
      }

      IdeVariant targetVariant = targetAndroidModel.findVariantByName(testedVariant.getTargetVariant());
      if (targetVariant == null) {
        getLogger().warn("Tested variant not found. Sync might have failed.");
        continue;
      }

      File targetApk = getApk(targetVariant.getName(), selectArtifact(targetVariant, false), deviceAbis, deviceVersion, targetFacet, false);

      // TODO: use the applicationIdProvider to get the applicationId (we might not know it by sync time for Instant Apps)
      String applicationId = targetVariant.getDeprecatedPreMergedApplicationId();
      if (applicationId == null) {
        // If can't find applicationId in model, get it directly from manifest
        applicationId = targetAndroidModel.getApplicationId();
      }
      targetedApks.add(new ApkInfo(targetApk, applicationId));
    }

    return targetedApks;
  }

  @NotNull
  @Override
  public List<ValidationError> validate() {
    AndroidModuleModel androidModuleModel = AndroidModuleModel.get(myFacet);
    if (androidModuleModel == null) {
      Runnable requestProjectSync =
        () -> ProjectSystemUtil.getSyncManager(myFacet.getModule().getProject())
          .syncProject(ProjectSystemSyncManager.SyncReason.USER_REQUEST);
      return ImmutableList.of(ValidationError.fatal("The project has not yet been synced with Gradle configuration", requestProjectSync));
    }
    // Note: Instant apps and app bundles outputs are assumed to be signed
    AndroidVersion targetDevicesMinVersion = null; // NOTE: ApkProvider.validate() runs in a device-less context.
    if (androidModuleModel.getAndroidProject().getProjectType() == IdeAndroidProjectType.PROJECT_TYPE_INSTANTAPP ||
        myOutputKindProvider.apply(targetDevicesMinVersion) == OutputKind.AppBundleOutputModel ||
        isArtifactSigned(androidModuleModel)) {
      return ImmutableList.of();
    }

    File outputFile = getOutputFile(androidModuleModel);
    String outputFileName = outputFile == null ? "Unknown output" : outputFile.getName();
    final String message =
      AndroidBundle.message("run.error.apk.not.signed", outputFileName, androidModuleModel.getSelectedVariant().getDisplayName());

    Runnable quickFix = () -> {
      Module module = myFacet.getModule();
      ProjectSettingsService service = ProjectSettingsService.getInstance(module.getProject());
      if (service instanceof AndroidProjectSettingsService) {
        ((AndroidProjectSettingsService)service).openSigningConfiguration(module);
      }
      else {
        service.openModuleSettings(module);
      }
    };
    return ImmutableList.of(ValidationError.fatal(message, quickFix));
  }

  /**
   * Returns an {@link ApkInfo} instance containing all the APKS generated by the "select apks from bundle" Gradle task, or {@code null}
   * in case of unexpected error.
   */
  @Nullable
  private static ApkInfo collectAppBundleOutput(@NotNull Module module,
                                               @NotNull PostBuildModelProvider outputModelProvider,
                                               @NotNull String pkgName) {
    AndroidModuleModel androidModel = AndroidModuleModel.get(module);
    if (androidModel == null) {
      getLogger().warn("Android model is null. Sync might have failed");
      return null;
    }

    File apkFolder = androidModel.getFeatures().isBuildOutputFileSupported()
                     ? getOutputFileOrFolderFromListingFile(androidModel.getSelectedVariant().getMainArtifact().getBuildInformation(),
                                                            OutputType.ApkFromBundle)
                     : collectApkFolderFromPostBuildModel(module, outputModelProvider, androidModel);

    if (apkFolder == null) {
      getLogger().warn("Could not find apk folder.");
      return null;
    }

    // List all files in the folder
    try (Stream<Path> stream = Files.list(apkFolder.toPath())) {
      List<ApkFileUnit> apks = stream
        .map(path -> new ApkFileUnit(getFeatureNameFromPathHack(path), path.toFile()))
        .collect(Collectors.toList());
      return new ApkInfo(apks, pkgName);
    }
    catch (IOException e) {
      getLogger().warn(String.format("Error reading list of APK files from bundle build output directory \"%s\".", apkFolder), e);
      return null;
    }
  }

  /**
   * Returns the folder that contains all of the APKS generated by the "select apks from bundle" Gradle task, this method retrieves
   * the folder from post build model. This method should be used for AGP prior to 4.0.
   */
  @Nullable
  private static File collectApkFolderFromPostBuildModel(@NotNull Module module,
                                                         @NotNull PostBuildModelProvider outputModelProvider,
                                                         @NotNull AndroidModuleModel androidModel) {
    PostBuildModel model = outputModelProvider.getPostBuildModel();
    if (model == null) {
      getLogger().warn("Post build model is null. Build might have failed.");
      return null;
    }
    AppBundleProjectBuildOutput output = model.findAppBundleProjectBuildOutput(GradleUtil.getGradlePath(module));
    if (output == null) {
      getLogger().warn("Project output is null. Build may have failed.");
      return null;
    }

    for (AppBundleVariantBuildOutput variantBuildOutput : output.getAppBundleVariantsBuildOutput()) {
      if (variantBuildOutput.getName().equals(androidModel.getSelectedVariant().getName())) {
        return variantBuildOutput.getApkFolder();
      }
    }

    getLogger().warn("Bundle variant build output model has no entries. Build may have failed.");
    return null;
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

  /**
   * Get the main output APK file for the selected variant.
   * The method uses output listing file if it is supported, in which case, the output file will be empty if the project is never built.
   * If build output listing file is not supported, then AndroidArtifact::getOutputs will be used.
   *
   * @return the main output file for selected variant.
   */
  @Nullable
  public static File getOutputFile(@NotNull AndroidModuleModel androidModel) {
    if (androidModel.getFeatures().isBuildOutputFileSupported()) {
      return getOutputFileOrFolderFromListingFile(androidModel.getSelectedVariant().getMainArtifact().getBuildInformation(), OutputType.Apk);
    }
    else {
      //noinspection deprecation
      List<IdeAndroidArtifactOutput> outputs = androidModel.getMainArtifact().getOutputs();
      if (outputs.isEmpty()) return null;
      IdeAndroidArtifactOutput output = getFirstItem(outputs);
      assert output != null;
      return output.getOutputFile();
    }
  }

  private boolean isArtifactSigned(AndroidModuleModel androidModuleModel) {
    IdeAndroidArtifact artifact = myTest ? androidModuleModel.getArtifactForAndroidTest() : androidModuleModel.getMainArtifact();
    return artifact != null && artifact.isSigned();
  }

  @NotNull
  private static Logger getLogger() {
    return Logger.getInstance(GradleApkProvider.class);
  }

  /**
   * Find the best output for the selected device and variant when multiple splits are available.
   */
  static class BestOutputFinder {

    @NotNull
    File findBestOutput(@NotNull Set<String> artifactAbiFilters,
                        @NotNull List<String> abis,
                        @NotNull List<IdeAndroidArtifactOutput> outputs) throws ApkProvisionException {
      if (outputs.isEmpty()) {
        throw new ApkProvisionException("No outputs for the main artifact of variant: " + VARIANT_DISPLAY_NAME_STUB);
      }
      List<File> apkFiles =
        ContainerUtil.map(SplitOutputMatcher.computeBestOutput(outputs, artifactAbiFilters, abis), IdeAndroidArtifactOutput::getOutputFile);
      verifyApkCollectionIsNotEmpty(apkFiles, abis, outputs.size());
      // Install apk (note that variant.getOutputFile() will point to a .aar in the case of a library).
      return apkFiles.get(0);
    }

    @NotNull
    File findBestOutput(@NotNull Set<String> artifactAbiFilters,
                        @NotNull List<String> abis,
                        @NotNull GenericBuiltArtifacts builtArtifact) throws ApkProvisionException {
      List<File> apkFiles = GenericBuiltArtifactsSplitOutputMatcher.INSTANCE.computeBestOutput(builtArtifact, artifactAbiFilters, abis);
      verifyApkCollectionIsNotEmpty(apkFiles, abis, builtArtifact.getElements().size());
      // Install apk (note that variant.getOutputFile() will point to a .aar in the case of a library).
      return apkFiles.get(0);
    }

    private static void verifyApkCollectionIsNotEmpty(@NotNull List<File> apkFiles,
                                                      @NotNull List<String> abis,
                                                      int outputCount)
      throws ApkProvisionException {
      if (apkFiles.isEmpty()) {
        String message = AndroidBundle.message("deployment.failed.splitapk.nomatch",
                                               VARIANT_DISPLAY_NAME_STUB,
                                               outputCount,
                                               Joiner.on(", ").join(abis));
        throw new ApkProvisionException(message);
      }
    }
  }
}
