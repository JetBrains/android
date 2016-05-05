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
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidArtifactOutput;
import com.android.builder.model.TestedTargetVariant;
import com.android.builder.model.Variant;
import com.android.ddmlib.IDevice;
import com.android.ide.common.build.SplitOutputMatcher;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.structure.editors.AndroidProjectSettingsService;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.util.Computable;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Provides the information on APKs to install for run configurations in Gradle projects.
 */
public class GradleApkProvider implements ApkProvider {
  private static final Logger LOG = Logger.getInstance(GradleApkProvider.class);

  @NotNull
  private final AndroidFacet myFacet;
  @NotNull
  private final ApplicationIdProvider myApplicationIdProvider;
  private final boolean myTest;

  public GradleApkProvider(@NotNull AndroidFacet facet,
                           @NotNull ApplicationIdProvider applicationIdProvider,
                           boolean test) {
    myFacet = facet;
    myApplicationIdProvider = applicationIdProvider;
    myTest = test;
  }

  @Override
  @NotNull
  public Collection<ApkInfo> getApks(@NotNull IDevice device) throws ApkProvisionException {
    AndroidGradleModel androidModel = AndroidGradleModel.get(myFacet);
    if (androidModel == null) {
      LOG.warn("Android model is null. Sync might have failed");
      return Collections.emptyList();
    }
    Variant selectedVariant = androidModel.getSelectedVariant();

    List<ApkInfo> apkList = Lists.newArrayList();

    // install apk (note that variant.getOutputFile() will point to a .aar in the case of a library)
    if (!androidModel.getAndroidProject().isLibrary()) {
      File apk = getApk(selectedVariant, device);
      apkList.add(new ApkInfo(apk, myApplicationIdProvider.getPackageName()));
    }

    if (myTest) {
      AndroidArtifact testArtifactInfo = androidModel.getAndroidTestArtifactInSelectedVariant();
      if (testArtifactInfo != null) {
        AndroidArtifactOutput output = GradleUtil.getOutput(testArtifactInfo);
        File testApk = output.getMainOutputFile().getOutputFile();
        String testPackageName = myApplicationIdProvider.getTestPackageName();
        assert testPackageName != null; // Cannot be null if initialized.
        apkList.add(new ApkInfo(testApk, testPackageName));
      }

      if (androidModel.supportsTestedTargetVariants()) {
        apkList.addAll(0, getTargetedApks(selectedVariant, device));
      }
    }
    return apkList;
  }


  @NotNull
  private static File getApk(@NotNull Variant variant, @NotNull IDevice device) throws ApkProvisionException {
    AndroidArtifact mainArtifact = variant.getMainArtifact();
    List<AndroidArtifactOutput> outputs = Lists.newArrayList(mainArtifact.getOutputs());
    if (outputs.isEmpty()) {
      throw new ApkProvisionException("No outputs for the main artifact of variant: " + variant.getDisplayName());
    }

    List<String> abis = device.getAbis();
    int density = device.getDensity();
    Set<String> variantAbiFilters = mainArtifact.getAbiFilters();
    List<OutputFile> apkFiles = SplitOutputMatcher.computeBestOutput(outputs, variantAbiFilters, density, abis);
    if (apkFiles.isEmpty()) {
      String message = AndroidBundle.message("deployment.failed.splitapk.nomatch",
                                             variant.getDisplayName(),
                                             outputs.size(),
                                             density,
                                             Joiner.on(", ").join(abis));
      throw new ApkProvisionException(message);
    }
    return apkFiles.get(0).getOutputFile();
  }

  /**
   * Gets the list of targeted apks for the specified variant.
   *
   * <p>This is used for test-only modules when specifying the tested apk
   * using the targetProjectPath and targetVariant properties in the build file.
   */
  @NotNull
  private List<ApkInfo> getTargetedApks(@NotNull Variant selectedVariant, @NotNull IDevice device) throws ApkProvisionException {
    List<ApkInfo> targetedApks = Lists.newArrayList();
    for (TestedTargetVariant testedVariant: getTestedTargetVariants(selectedVariant)) {
      Module targetModule = ApplicationManager.getApplication().runReadAction(
        (Computable<Module>)() ->
          GradleUtil.findModuleByGradlePath(myFacet.getModule().getProject(), testedVariant.getTargetProjectPath()));

      assert targetModule != null; // target module has to exist, otherwise we would not be able to build test apk
      AndroidFacet targetFacet = AndroidFacet.getInstance(targetModule);
      if (targetFacet == null){
        LOG.warn("Please install tested apk manually.");
        continue;
      }

      AndroidGradleModel targetAndroidModel = AndroidGradleModel.get(targetFacet);
      if (targetAndroidModel == null){
        LOG.warn("Android model for tested module is null. Sync might have failed.");
        continue;
      }

      Variant targetVariant = targetAndroidModel.findVariantByName(testedVariant.getTargetVariant());
      if (targetVariant == null){
        LOG.warn("Tested variant not found. Sync might have failed.");
        continue;
      }

      File targetApk = getApk(targetVariant, device);
      targetedApks.add(new ApkInfo(targetApk, targetVariant.getMergedFlavor().getApplicationId()));
    }

    return targetedApks;
  }

  // TODO: Remove once Android plugin v. 2.3 is the "recommended" version.
  @NotNull
  private static Collection<TestedTargetVariant> getTestedTargetVariants(@NotNull Variant variant) {
    try {
      return variant.getTestedTargetVariants();
    }
    catch (UnsupportedMethodException e) {
      Logger.getInstance(GradleApkProvider.class).warn("Method 'getTestedTargetVariants' not found", e);
      return Lists.newArrayList();
    }
  }

  @NotNull
  @Override
  public List<ValidationError> validate() {
    AndroidGradleModel androidGradleModel = AndroidGradleModel.get(myFacet);
    assert androidGradleModel != null; // This is a Gradle project, there must be an AndroidGradleModel.
    if (androidGradleModel.getMainArtifact().isSigned()) {
      return ImmutableList.of();
    }

    AndroidArtifactOutput output = GradleUtil.getOutput(androidGradleModel.getMainArtifact());
    final String message = AndroidBundle.message("run.error.apk.not.signed", output.getMainOutputFile().getOutputFile().getName(),
                                                 androidGradleModel.getSelectedVariant().getDisplayName());

    Runnable quickFix = new Runnable() {
      @Override
      public void run() {
        Module module = myFacet.getModule();
        ProjectSettingsService service = ProjectSettingsService.getInstance(module.getProject());
        if (service instanceof AndroidProjectSettingsService) {
          ((AndroidProjectSettingsService)service).openSigningConfiguration(module);
        }
        else {
          service.openModuleSettings(module);
        }
      }
    };
    return ImmutableList.of(ValidationError.fatal(message, quickFix));
  }
}
