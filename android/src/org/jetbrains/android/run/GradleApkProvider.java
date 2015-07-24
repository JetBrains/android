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
package org.jetbrains.android.run;

import com.android.build.OutputFile;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidArtifactOutput;
import com.android.builder.model.BaseArtifact;
import com.android.builder.model.Variant;
import com.android.ddmlib.IDevice;
import com.android.ide.common.build.SplitOutputMatcher;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Provides the information on APKs to install for run configurations in Gradle projects.
 */
public class GradleApkProvider implements ApkProvider {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.run.GradleApkProvider");
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
    IdeaAndroidProject androidModel = myFacet.getAndroidModel();
    assert androidModel != null; // This is a Gradle project, there must be an IdeaAndroidProject.
    Variant selectedVariant = androidModel.getSelectedVariant();

    List<ApkInfo> apkList = new ArrayList<ApkInfo>();

    // install apk (note that variant.getOutputFile() will point to a .aar in the case of a library)
    if (!androidModel.getAndroidProject().isLibrary()) {
      File apk = getApk(selectedVariant, device);
      if (apk == null) {
        String message = AndroidBundle.message("deployment.failed.cannot.determine.apk", selectedVariant.getDisplayName(), device.getName());
        throw new ApkProvisionException(message);
      }
      apkList.add(new ApkInfo(apk, getPackageName()));
    }

    if (myTest) {
      BaseArtifact testArtifactInfo = androidModel.findSelectedTestArtifactInSelectedVariant();
      if (testArtifactInfo instanceof AndroidArtifact) {
        AndroidArtifactOutput output = GradleUtil.getOutput((AndroidArtifact)testArtifactInfo);
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
    IdeaAndroidProject androidModel = myFacet.getAndroidModel();
    assert androidModel != null; // This is a Gradle project, there must be an IdeaAndroidProject.
    // In the case of Gradle projects, either the merged flavor provides a test package name,
    // or we just append ".test" to the source package name
    Variant selectedVariant = androidModel.getSelectedVariant();
    String testPackageName = selectedVariant.getMergedFlavor().getTestApplicationId();
    return (testPackageName != null) ? testPackageName : getPackageName() + DEFAULT_TEST_PACKAGE_SUFFIX;
  }

  static File getApk(@NotNull Variant variant, @NotNull IDevice device) {
    AndroidArtifact mainArtifact = variant.getMainArtifact();
    List<AndroidArtifactOutput> outputs = Lists.newArrayList(mainArtifact.getOutputs());
    if (outputs.isEmpty()) {
      LOG.info("No outputs for the main artifact of variant: " + variant.getDisplayName());
      return null;
    }

    List<String> abis = device.getAbis();
    int density = device.getDensity();
    Set<String> variantAbiFilters = mainArtifact.getAbiFilters();
    List<OutputFile> apkFiles = SplitOutputMatcher.computeBestOutput(outputs, variantAbiFilters, density, abis);
    if (apkFiles.isEmpty()) {
      String message = AndroidBundle.message("deployment.failed.splitapk.nomatch", outputs.size(), density, Joiner.on(", ").join(abis));
      LOG.error(message);
      return null;
    }
    return apkFiles.get(0).getOutputFile();
  }
}
