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

import com.android.build.OutputFile;
import com.android.builder.model.*;
import com.android.ddmlib.IDevice;
import com.android.tools.apk.analyzer.AndroidApplicationInfo;
import com.android.tools.apk.analyzer.Archive;
import com.android.tools.apk.analyzer.Archives;
import com.android.tools.idea.apk.viewer.ApkParser;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.ide.android.IdeAndroidArtifact;
import com.android.tools.idea.gradle.project.model.ide.android.IdeVariant;
import com.android.tools.idea.gradle.run.PostBuildModel;
import com.android.tools.idea.gradle.run.PostBuildModelProvider;
import com.android.tools.idea.gradle.structure.editors.AndroidProjectSettingsService;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.util.Computable;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.android.builder.model.AndroidProject.*;
import static com.android.tools.idea.gradle.util.GradleUtil.findModuleByGradlePath;

/**
 * Provides the information on APKs to install for run configurations in Gradle projects.
 */
public class GradleApkProvider implements ApkProvider {
  @NotNull private final AndroidFacet myFacet;
  @NotNull private final ApplicationIdProvider myApplicationIdProvider;
  @NotNull private final PostBuildModelProvider myOutputModelProvider;
  @NotNull private final BestOutputFinder myBestOutputFinder;
  private final boolean myTest;

  public GradleApkProvider(@NotNull AndroidFacet facet,
                           @NotNull ApplicationIdProvider applicationIdProvider,
                           boolean test) {
    this(facet, applicationIdProvider, () -> null, test);
  }

  public GradleApkProvider(@NotNull AndroidFacet facet,
                           @NotNull ApplicationIdProvider applicationIdProvider,
                           @NotNull PostBuildModelProvider outputModelProvider,
                           boolean test) {
    this(facet, applicationIdProvider, outputModelProvider, new BestOutputFinder(), test);
  }

  @VisibleForTesting
  GradleApkProvider(@NotNull AndroidFacet facet,
                    @NotNull ApplicationIdProvider applicationIdProvider,
                    @NotNull PostBuildModelProvider outputModelProvider,
                    @NotNull BestOutputFinder bestOutputFinder,
                    boolean test) {
    myFacet = facet;
    myApplicationIdProvider = applicationIdProvider;
    myOutputModelProvider = outputModelProvider;
    myBestOutputFinder = bestOutputFinder;
    myTest = test;
  }

  @Override
  @NotNull
  public Collection<ApkInfo> getApks(@NotNull IDevice device) throws ApkProvisionException {
    AndroidModuleModel androidModel = AndroidModuleModel.get(myFacet);
    if (androidModel == null) {
      getLogger().warn("Android model is null. Sync might have failed");
      return Collections.emptyList();
    }
    IdeVariant selectedVariant = androidModel.getSelectedVariant();

    List<ApkInfo> apkList = new ArrayList<>();

    int projectType = androidModel.getAndroidProject().getProjectType();
    if (projectType == PROJECT_TYPE_APP || projectType == PROJECT_TYPE_INSTANTAPP || projectType == PROJECT_TYPE_TEST) {
      // The APK file for instant apps is actually a zip file
      File apk = getApk(selectedVariant, device, myFacet, false);
      String pkgName = projectType == PROJECT_TYPE_TEST
                       ? myApplicationIdProvider.getTestPackageName()
                       : myApplicationIdProvider.getPackageName();
      apkList.add(new ApkInfo(apk, pkgName));
    }

    apkList.addAll(getAdditionalApks(selectedVariant.getMainArtifact()));

    if (myTest) {
      if (projectType == PROJECT_TYPE_TEST) {
        if (androidModel.getFeatures().isTestedTargetVariantsSupported()) {
          apkList.addAll(0, getTargetedApks(selectedVariant, device));
        }
      }
      else {
        IdeAndroidArtifact testArtifactInfo = androidModel.getSelectedVariant().getAndroidTestArtifact();
        if (testArtifactInfo != null) {
          File testApk = getApk(androidModel.getSelectedVariant(), device, myFacet, true);
          String testPackageName = myApplicationIdProvider.getTestPackageName();
          assert testPackageName != null; // Cannot be null if initialized.
          apkList.add(new ApkInfo(testApk, testPackageName));
          apkList.addAll(getAdditionalApks(testArtifactInfo));
        }
      }
    }
    return apkList;
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
  private static List<ApkInfo> getAdditionalApks(@NotNull AndroidArtifact testArtifactInfo) {
    List<ApkInfo> result = new ArrayList<>();
    for (File fileApk : testArtifactInfo.getAdditionalRuntimeApks()) {
      try {
        String packageId = getPackageId(fileApk);
        result.add(new ApkInfo(fileApk, packageId));
      }
      catch (ApkProvisionException e) {
        getLogger().error("Failed to get the package name from the given file. Therefore, we are not be able to install it. Please install it manually: " + fileApk.getName() + " error: " + e.getMessage(), e);
      }
    }
    return result;
  }

  private static String getPackageId(@NotNull File fileApk) throws ApkProvisionException {
    try (Archive archive = Archives.open(fileApk.toPath())) {
      AndroidApplicationInfo applicationInfo = ApkParser.getAppInfo(archive);
      if(applicationInfo == AndroidApplicationInfo.UNKNOWN) {
        throw new ApkProvisionException("Could not determine manifest package for apk: " + fileApk.getName());
      } else {
        return applicationInfo.packageId;
      }
    }
    catch (IOException e) {
      throw new ApkProvisionException("Could not determine manifest package for apk: " + fileApk.getName(), e.getCause());
    }
  }

  @VisibleForTesting
  @NotNull
  File getApk(@NotNull IdeVariant variant,
              @NotNull IDevice device,
              @NotNull AndroidFacet facet,
              boolean fromTestArtifact) throws ApkProvisionException {
    AndroidModuleModel androidModel = AndroidModuleModel.get(facet);
    assert androidModel != null;
    if (androidModel.getFeatures().isPostBuildSyncSupported()) {
      return getApkFromPostBuildSync(variant, device, facet, fromTestArtifact);
    }
    return getApkFromPreBuildSync(variant, device, fromTestArtifact);
  }

  @NotNull
  @VisibleForTesting
  File getApkFromPreBuildSync(@NotNull IdeVariant variant,
                              @NotNull IDevice device,
                              boolean fromTestArtifact) throws ApkProvisionException {
    IdeAndroidArtifact artifact = fromTestArtifact ? variant.getAndroidTestArtifact() : variant.getMainArtifact();
    assert artifact != null;
    List<AndroidArtifactOutput> outputs = new ArrayList<>(artifact.getOutputs());
    return getBestOutput(variant, device, outputs);
  }

  @NotNull
  @VisibleForTesting
  File getApkFromPostBuildSync(@NotNull IdeVariant variant,
                               @NotNull IDevice device,
                               @NotNull AndroidFacet facet,
                               boolean fromTestArtifact) throws ApkProvisionException {
    List<OutputFile> outputs = new ArrayList<>();

    PostBuildModel outputModels = myOutputModelProvider.getPostBuildModel();
    if (outputModels == null) {
      return getApkFromPreBuildSync(variant, device, fromTestArtifact);
    }

    if (facet.getProjectType() == PROJECT_TYPE_INSTANTAPP) {
      InstantAppProjectBuildOutput outputModel = outputModels.findInstantAppProjectBuildOutput(facet);
      if (outputModel == null) {
        throw new ApkProvisionException("Couldn't get post build model for Instant Apps. Please, make sure to use plugin 3.0.0-alpha10 or later.");
      }

      for (InstantAppVariantBuildOutput instantAppVariantBuildOutput : outputModel.getInstantAppVariantsBuildOutput()) {
        if (instantAppVariantBuildOutput.getName().equals(variant.getName())) {
          outputs.add(instantAppVariantBuildOutput.getOutput());
        }
      }
    }
    else {
      ProjectBuildOutput outputModel = outputModels.findProjectBuildOutput(facet);
      if (outputModel == null) {
        return getApkFromPreBuildSync(variant, device, fromTestArtifact);
      }

      // Loop through the variants in the model and get the one that matches
      for (VariantBuildOutput variantBuildOutput : outputModel.getVariantsBuildOutput()) {
        if (variantBuildOutput.getName().equals(variant.getName())) {

          if (fromTestArtifact) {
            // Get the output from the test artifact
            for (TestVariantBuildOutput testVariantBuildOutput : variantBuildOutput.getTestingVariants()) {
              if (testVariantBuildOutput.getType().equals(TestVariantBuildOutput.ANDROID_TEST)) {
                outputs.addAll(testVariantBuildOutput.getOutputs());
              }
            }
          }
          else {
            // Get the output from the main artifact
            outputs.addAll(variantBuildOutput.getOutputs());
          }
        }
      }
    }

    // If empty, it means that either ProjectBuildOut has not been filled correctly or the variant was not found.
    // In this case we try to get an APK known at sync time, if any.
    return outputs.isEmpty() ? getApkFromPreBuildSync(variant, device, fromTestArtifact) : getBestOutput(variant, device, outputs);
  }

  /**
   * Find the best output for the selected device and variant.
   * If multiple splits are available, it selects the best one.
   */
  @NotNull
  private File getBestOutput(@NotNull IdeVariant variant, @NotNull IDevice device, @NotNull List<? extends OutputFile> outputs)
    throws ApkProvisionException {
    if (outputs.isEmpty()) {
      throw new ApkProvisionException("No outputs for the main artifact of variant: " + variant.getDisplayName());
    }
    List<OutputFile> apkFiles = myBestOutputFinder.findBestOutput(variant, device, outputs);
    // Install apk (note that variant.getOutputFile() will point to a .aar in the case of a library).
    return apkFiles.get(0).getOutputFile();
  }

  /**
   * Gets the list of targeted apks for the specified variant.
   *
   * <p>This is used for test-only modules when specifying the tested apk
   * using the targetProjectPath and targetVariant properties in the build file.
   */
  @NotNull
  private List<ApkInfo> getTargetedApks(@NotNull IdeVariant selectedVariant,
                                        @NotNull IDevice device) throws ApkProvisionException {
    List<ApkInfo> targetedApks = new ArrayList<>();

    for (TestedTargetVariant testedVariant : selectedVariant.getTestedTargetVariants()) {
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

      IdeVariant targetVariant = (IdeVariant) targetAndroidModel.findVariantByName(testedVariant.getTargetVariant());
      if (targetVariant == null) {
        getLogger().warn("Tested variant not found. Sync might have failed.");
        continue;
      }

      File targetApk = getApk(targetVariant, device, targetFacet, false);

      // TODO: use the applicationIdProvider to get the applicationId (we might not know it by sync time for Instant Apps)
      String applicationId = targetVariant.getMergedFlavor().getApplicationId();
      if (applicationId == null) {
        // If can't find applicationId in model, get it directly from manifest
        applicationId = ApkProviderUtil.computePackageName(targetFacet);
      }
      targetedApks.add(new ApkInfo(targetApk, applicationId));
    }

    return targetedApks;
  }

  @NotNull
  @Override
  public List<ValidationError> validate() {
    AndroidModuleModel androidModuleModel = AndroidModuleModel.get(myFacet);
    assert androidModuleModel != null; // This is a Gradle project, there must be an AndroidGradleModel.
    if (androidModuleModel.getAndroidProject().getProjectType() == PROJECT_TYPE_INSTANTAPP || androidModuleModel.getMainArtifact().isSigned()) {
      return ImmutableList.of();
    }

    AndroidArtifactOutput output = GradleUtil.getOutput(androidModuleModel.getMainArtifact());
    final String message = AndroidBundle.message("run.error.apk.not.signed", output.getMainOutputFile().getOutputFile().getName(),
                                                 androidModuleModel.getSelectedVariant().getDisplayName());

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

  @NotNull
  private static Logger getLogger() {
    return Logger.getInstance(GradleApkProvider.class);
  }
}
