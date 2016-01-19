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
import com.android.builder.model.Variant;
import com.android.ddmlib.IDevice;
import com.android.ide.common.build.SplitOutputMatcher;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.structure.editors.AndroidProjectSettingsService;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
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

  /** Default suffix for test packages (as added by Android Gradle plugin) */
  private static final String DEFAULT_TEST_PACKAGE_SUFFIX = ".test";

  @NotNull
  private final AndroidFacet myFacet;
  private final boolean myTest;

  public GradleApkProvider(@NotNull AndroidFacet facet, boolean test) {
    myFacet = facet;
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
      apkList.add(new ApkInfo(apk, getPackageName()));
    }

    if (myTest) {
      AndroidArtifact testArtifactInfo = androidModel.getAndroidTestArtifactInSelectedVariant();
      if (testArtifactInfo != null) {
        AndroidArtifactOutput output = GradleUtil.getOutput(testArtifactInfo);
        File testApk = output.getMainOutputFile().getOutputFile();
        String testPackageName = getTestPackageName();
        assert testPackageName != null; // Cannot be null if initialized.
        apkList.add(new ApkInfo(testApk, testPackageName));
      }
    }
    return apkList;
  }

  @Override
  @NotNull
  public String getPackageName() throws ApkProvisionException {
    return ApkProviderUtil.computePackageName(myFacet);
  }

  @Override
  public String getTestPackageName() throws ApkProvisionException {
    AndroidGradleModel androidModel = AndroidGradleModel.get(myFacet);
    assert androidModel != null; // This is a Gradle project, there must be an AndroidGradleModel.
    // In the case of Gradle projects, either the merged flavor provides a test package name,
    // or we just append ".test" to the source package name
    Variant selectedVariant = androidModel.getSelectedVariant();
    String testPackageName = selectedVariant.getMergedFlavor().getTestApplicationId();
    return (testPackageName != null) ? testPackageName : getPackageName() + DEFAULT_TEST_PACKAGE_SUFFIX;
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
