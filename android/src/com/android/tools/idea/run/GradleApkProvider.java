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
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.run.ProjectBuildOutputProvider;
import com.android.tools.idea.gradle.structure.editors.AndroidProjectSettingsService;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.util.Computable;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.android.builder.model.AndroidProject.PROJECT_TYPE_APP;
import static com.android.builder.model.AndroidProject.PROJECT_TYPE_INSTANTAPP;
import static com.android.tools.idea.gradle.util.GradleUtil.findModuleByGradlePath;
import static com.android.tools.idea.gradle.util.GradleUtil.getOutput;

/**
 * Provides the information on APKs to install for run configurations in Gradle projects.
 */
public class GradleApkProvider implements ApkProvider {
  @NotNull private final AndroidFacet myFacet;
  @NotNull private final ApplicationIdProvider myApplicationIdProvider;
  @NotNull private final ProjectBuildOutputProvider myOutputModelProvider;
  @NotNull private final BestOutputFinder myBestOutputFinder;
  private final boolean myTest;

  public GradleApkProvider(@NotNull AndroidFacet facet,
                           @NotNull ApplicationIdProvider applicationIdProvider,
                           boolean test) {
    this(facet, applicationIdProvider, () -> null, test);
  }

  public GradleApkProvider(@NotNull AndroidFacet facet,
                           @NotNull ApplicationIdProvider applicationIdProvider,
                           @NotNull ProjectBuildOutputProvider outputModelProvider,
                           boolean test) {
    this(facet, applicationIdProvider, outputModelProvider, new BestOutputFinder(), test);
  }

  @VisibleForTesting
  GradleApkProvider(@NotNull AndroidFacet facet,
                    @NotNull ApplicationIdProvider applicationIdProvider,
                    @NotNull ProjectBuildOutputProvider outputModelProvider,
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
    Variant selectedVariant = androidModel.getSelectedVariant();

    List<ApkInfo> apkList = new ArrayList<>();

    // install apk (note that variant.getOutputFile() will point to a .aar in the case of a library)
    int projectType = androidModel.getAndroidProject().getProjectType();
    if (projectType == PROJECT_TYPE_APP || projectType == PROJECT_TYPE_INSTANTAPP) {
      // The APK file for instant apps is actually a zip file
      File apk = getApk(selectedVariant, device, androidModel);
      apkList.add(new ApkInfo(apk, myApplicationIdProvider.getPackageName()));
    }

    if (myTest) {
      AndroidArtifact testArtifactInfo = androidModel.getSelectedVariant().getAndroidTestArtifact();
      if (testArtifactInfo != null) {
        AndroidArtifactOutput output = getOutput(testArtifactInfo);
        File testApk = output.getMainOutputFile().getOutputFile();
        String testPackageName = myApplicationIdProvider.getTestPackageName();
        assert testPackageName != null; // Cannot be null if initialized.
        apkList.add(new ApkInfo(testApk, testPackageName));
      }

      if (androidModel.getFeatures().isTestedTargetVariantsSupported()) {
        apkList.addAll(0, getTargetedApks(selectedVariant, device, androidModel));
      }
    }
    return apkList;
  }

  @VisibleForTesting
  @NotNull
  File getApk(@NotNull Variant variant, @NotNull IDevice device, @NotNull AndroidModuleModel androidModel)
    throws ApkProvisionException {
    if (androidModel.getFeatures().isPostBuildSyncSupported()) {
      return getApkFromPostBuildSync(variant, device);
    }
    return getApk(variant, device);
  }

  @NotNull
  private File getApk(@NotNull Variant variant, @NotNull IDevice device) throws ApkProvisionException {
    AndroidArtifact mainArtifact = variant.getMainArtifact();
    List<AndroidArtifactOutput> outputs = new ArrayList<>(mainArtifact.getOutputs());
    if (outputs.isEmpty()) {
      throw new ApkProvisionException("No outputs for the main artifact of variant: " + variant.getDisplayName());
    }
    List<OutputFile> apkFiles = myBestOutputFinder.findBestOutput(variant, device, outputs);
    return apkFiles.get(0).getOutputFile();
  }

  @NotNull
  private File getApkFromPostBuildSync(@NotNull Variant variant, @NotNull IDevice device) throws ApkProvisionException {
    AndroidArtifact mainArtifact = variant.getMainArtifact();

    List<OutputFile> outputs = new ArrayList<>();

    ProjectBuildOutput outputModel = myOutputModelProvider.getOutputModel();
    if (outputModel != null) {
      for (VariantBuildOutput variantBuildOutput : outputModel.getVariantsBuildOutput()) {
        if (variantBuildOutput.getName().equals(variant.getName())) {
          outputs.addAll(variantBuildOutput.getOutputs());
        }
      }
    }
    if (outputs.isEmpty()) {
      // This should be reached only in case the ProjectBuildOutput is not correctly filled or it's an old version of the plugin.
      outputs.addAll(mainArtifact.getOutputs());
    }

    if (outputs.isEmpty()) {
      throw new ApkProvisionException("No outputs for the main artifact of variant: " + variant.getDisplayName());
    }

    List<OutputFile> apkFiles = myBestOutputFinder.findBestOutput(variant, device, outputs);
    return apkFiles.get(0).getOutputFile();
  }

  /**
   * Gets the list of targeted apks for the specified variant.
   *
   * <p>This is used for test-only modules when specifying the tested apk
   * using the targetProjectPath and targetVariant properties in the build file.
   */
  @NotNull
  private List<ApkInfo> getTargetedApks(@NotNull Variant selectedVariant, @NotNull IDevice device, @NotNull AndroidModuleModel androidModel)
    throws ApkProvisionException {
    List<ApkInfo> targetedApks = new ArrayList<>();
    for (TestedTargetVariant testedVariant : getTestedTargetVariants(selectedVariant)) {
      Module targetModule = ApplicationManager.getApplication().runReadAction(
        (Computable<Module>)() -> {
          Project project = myFacet.getModule().getProject();
          return findModuleByGradlePath(project, testedVariant.getTargetProjectPath());
        });

      assert targetModule != null; // target module has to exist, otherwise we would not be able to build test apk
      AndroidFacet targetFacet = AndroidFacet.getInstance(targetModule);
      if (targetFacet == null) {
        getLogger().warn("Please install tested apk manually.");
        continue;
      }

      AndroidModuleModel targetAndroidModel = AndroidModuleModel.get(targetFacet);
      if (targetAndroidModel == null) {
        getLogger().warn("Android model for tested module is null. Sync might have failed.");
        continue;
      }

      Variant targetVariant = targetAndroidModel.findVariantByName(testedVariant.getTargetVariant());
      if (targetVariant == null) {
        getLogger().warn("Tested variant not found. Sync might have failed.");
        continue;
      }

      File targetApk = getApk(targetVariant, device, androidModel);
      String applicationId = targetVariant.getMergedFlavor().getApplicationId();
      assert applicationId != null;
      targetedApks.add(new ApkInfo(targetApk, applicationId));
    }

    return targetedApks;
  }

  // TODO: Remove once Android plugin v. 2.3 is the "recommended" version.
  @NotNull
  @Deprecated
  // TODO: use IdeVariant#getTestedTargetVariants instead.
  private static Collection<TestedTargetVariant> getTestedTargetVariants(@NotNull Variant variant) {
    try {
      return variant.getTestedTargetVariants();
    }
    catch (UnsupportedMethodException e) {
      getLogger().warn("Method 'getTestedTargetVariants' not found", e);
      return Collections.emptyList();
    }
  }

  @NotNull
  @Override
  public List<ValidationError> validate() {
    AndroidModuleModel androidModuleModel = AndroidModuleModel.get(myFacet);
    assert androidModuleModel != null; // This is a Gradle project, there must be an AndroidGradleModel.
    if (androidModuleModel.getAndroidProject().getProjectType() == PROJECT_TYPE_INSTANTAPP || androidModuleModel.getMainArtifact().isSigned()) {
      return ImmutableList.of();
    }

    AndroidArtifactOutput output = getOutput(androidModuleModel.getMainArtifact());
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
