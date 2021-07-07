/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.android.tools.idea.gradle.util.GradleUtil.findModuleByGradlePath;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradlePath;

import com.android.builder.model.InstantAppProjectBuildOutput;
import com.android.builder.model.InstantAppVariantBuildOutput;
import com.android.ide.common.build.GenericBuiltArtifacts;
import com.android.tools.idea.gradle.model.IdeAndroidArtifact;
import com.android.tools.idea.gradle.model.IdeAndroidProjectType;
import com.android.tools.idea.gradle.model.IdeTestedTargetVariant;
import com.android.tools.idea.gradle.model.IdeVariant;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.run.PostBuildModel;
import com.android.tools.idea.gradle.run.PostBuildModelProvider;
import com.android.tools.idea.gradle.util.DynamicAppUtils;
import com.android.tools.idea.gradle.util.GradleBuildOutputUtil;
import com.android.tools.idea.gradle.util.OutputType;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.google.common.base.Strings;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import java.util.Collection;
import org.jetbrains.android.dom.manifest.AndroidManifestUtils;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Application id provider for Gradle projects.
 */
public class GradleApplicationIdProvider implements ApplicationIdProvider {
  /**
   * Default suffix for test packages (as added by Android Gradle plugin).
   */
  private static final String DEFAULT_TEST_PACKAGE_SUFFIX = ".test";

  @NotNull private final AndroidFacet myFacet;

  private final boolean myForTests;
  @NotNull private final AndroidModuleModel myAndroidModel;
  @NotNull private final IdeVariant myVariant;
  @NotNull private final PostBuildModelProvider myOutputModelProvider;

  public GradleApplicationIdProvider(@NotNull AndroidFacet facet,
                                     boolean forTests,
                                     @NotNull AndroidModuleModel androidModel,
                                     @NotNull IdeVariant variant,
                                     @NotNull PostBuildModelProvider outputModelProvider) {
    myFacet = facet;
    myForTests = forTests;
    myAndroidModel = androidModel;
    myVariant = variant;
    myOutputModelProvider = outputModelProvider;
  }

  @Override
  @NotNull
  public String getPackageName() throws ApkProvisionException {
    // Android library project doesn't produce APK except for instrumentation tests. And for instrumentation test,
    // AGP creates instrumentation APK only. Both test code and library code will be packaged into an instrumentation APK.
    // This is called self-instrumenting test: https://source.android.com/compatibility/tests/development/instr-self-e2e
    // For this reason, this method should return test package name for Android library project.
    IdeAndroidProjectType projectType = myAndroidModel.getAndroidProject().getProjectType();
    if (projectType == IdeAndroidProjectType.PROJECT_TYPE_LIBRARY) {
      String testPackageName = getTestPackageName();
      if (testPackageName != null) {
        return testPackageName;
      }
      else {
        getLogger().warn("Could not get applicationId for library module.");
      }
    }

    if (projectType == IdeAndroidProjectType.PROJECT_TYPE_TEST) {
      ApplicationIdProvider targetApplicationIdProvider = getTargetApplicationIdProvider();
      if (targetApplicationIdProvider != null) {
        return targetApplicationIdProvider.getPackageName();
      }
      else {
        getLogger().warn("Could not get applicationId for tested module.");
      }
    }

    if (projectType == IdeAndroidProjectType.PROJECT_TYPE_INSTANTAPP) {
      String applicationId = tryToGetInstantAppApplicationId();
      if (applicationId != null) {
        return applicationId;
      }
      else {
        getLogger().warn("Could not get instant app applicationId from post build model.");
      }
    }

    if (projectType == IdeAndroidProjectType.PROJECT_TYPE_DYNAMIC_FEATURE) {
      String applicationId = tryToGetDynamicFeatureApplicationId();
      if (applicationId != null) {
        return applicationId;
      }
    }

    return getApplicationIdFromModelOrManifest(myFacet);
  }

  @Nullable
  private String tryToGetDynamicFeatureApplicationId() throws ApkProvisionException {
    Module baseAppModule = DynamicAppUtils.getBaseFeature(myFacet.getModule());
    if (baseAppModule == null) {
      getLogger().warn("[Instrumented test for Dynamic Features] Can't get base-app module");
      return null;
    }

    AndroidFacet baseAppFacet = AndroidFacet.getInstance(baseAppModule);
    if (baseAppFacet == null) {
      getLogger().warn("[Instrumented test for Dynamic Features] Can't get base-app Android Facet");
      return null;
    }

    return getApplicationIdFromModelOrManifest(baseAppFacet);
  }

  @Nullable
  private String tryToGetInstantAppApplicationId() {
    if (!myAndroidModel.getFeatures().isPostBuildSyncSupported() ||
        myAndroidModel.getAndroidProject().getProjectType() != IdeAndroidProjectType.PROJECT_TYPE_INSTANTAPP) {
      return null;
    }

    PostBuildModel postBuildModel = myOutputModelProvider.getPostBuildModel();
    if (postBuildModel == null) {
      return null;
    }

    InstantAppProjectBuildOutput projectBuildOutput = postBuildModel.findInstantAppProjectBuildOutput(getGradlePath(myFacet.getModule()));
    if (projectBuildOutput == null) {
      return null;
    }

    for (InstantAppVariantBuildOutput variantBuildOutput : projectBuildOutput.getInstantAppVariantsBuildOutput()) {
      if (variantBuildOutput.getName().equals(myVariant.getName())) {
        return variantBuildOutput.getApplicationId();
      }
    }

    return null;
  }

  @Override
  @Nullable
  public String getTestPackageName() throws ApkProvisionException {
    if (!myForTests) return null;

    IdeAndroidArtifact artifactForAndroidTest = getArtifactForAndroidTest();
    if (artifactForAndroidTest == null) return null;

    String outputListingFile = GradleBuildOutputUtil.getOutputListingFile(artifactForAndroidTest.getBuildInformation(), OutputType.Apk);
    if (!Strings.isNullOrEmpty(outputListingFile)) {
      GenericBuiltArtifacts builtArtifacts =
        myAndroidModel.getGenericBuiltArtifactsUsingCache(outputListingFile).getGenericBuiltArtifacts();
      if (builtArtifacts != null) {
        return builtArtifacts.getApplicationId();
      }
    }

    IdeAndroidProjectType projectType = myAndroidModel.getAndroidProject().getProjectType();
    if (projectType == IdeAndroidProjectType.PROJECT_TYPE_TEST) {
      String testPackageName = myVariant.getTestApplicationId();
      if (Strings.isNullOrEmpty(testPackageName)) {
        testPackageName = AndroidManifestUtils.getPackageName(myFacet);
      }
      if (Strings.isNullOrEmpty(testPackageName)) {
        throw new ApkProvisionException("[" + myFacet.getModule().getName() + "] Unable to obtain test package.");
      }
      return testPackageName;
    }

    // This is a Gradle project, there must be an AndroidGradleModel, but to avoid NPE we gracefully handle a null androidModel.
    // In the case of Gradle projects, either the merged flavor provides a test package name,
    // or we just append ".test" to the source package name.
    String testPackageName = myVariant.getTestApplicationId();
    if (testPackageName != null) {
      return testPackageName;
    }

    if (projectType == IdeAndroidProjectType.PROJECT_TYPE_DYNAMIC_FEATURE
        || projectType == IdeAndroidProjectType.PROJECT_TYPE_LIBRARY) {
      return getApplicationIdFromModelOrManifest(myFacet) + DEFAULT_TEST_PACKAGE_SUFFIX;
    }
    else {
      return getPackageName() + DEFAULT_TEST_PACKAGE_SUFFIX;
    }
  }

  @Nullable
  private ApplicationIdProvider getTargetApplicationIdProvider() {
    Collection<IdeTestedTargetVariant> targetVariants = myVariant.getTestedTargetVariants();
    if (targetVariants.size() != 1) {
      // There is no tested variant or more than one (what should never happen currently) and then we can't get package name.
      return null;
    }

    IdeTestedTargetVariant testedTargetVariant = targetVariants.iterator().next();
    Module targetModule = findModuleByGradlePath(myFacet.getModule().getProject(), testedTargetVariant.getTargetProjectPath());
    if (targetModule == null) {
      return null;
    }

    AndroidFacet targetFacet = AndroidFacet.getInstance(targetModule);
    if (targetFacet == null) {
      return null;
    }

    AndroidModuleModel targetModel = AndroidModuleModel.get(targetFacet);
    if (targetModel == null) {
      return null;
    }

    IdeVariant targetVariant = targetModel
      .getVariants()
      .stream()
      .filter(it -> it.getName().equals(testedTargetVariant.getTargetVariant()))
      .findAny()
      .orElse(null);
    if (targetVariant == null) {
      return null;
    }

    // Note: Even though our project is a test module project we request an application id provider for the target module which is
    //       not a test module and the return provider is supposed to be used to obtain the non-test application id only and hence
    //       we create a `GradleApplicationIdProvider` instance in `forTests = false` mode.
    return new GradleApplicationIdProvider(targetFacet, false, targetModel, targetVariant, myOutputModelProvider);
  }

  @NotNull
  private String getApplicationIdFromModelOrManifest(@NotNull AndroidFacet facet) throws ApkProvisionException {
    String pkg = AndroidModuleInfo.getInstance(facet).getPackage();
    if (pkg == null || pkg.isEmpty()) {
      throw new ApkProvisionException("[" + myFacet.getModule().getName() + "] Unable to obtain main package from manifest.");
    }
    return pkg;
  }

  @Nullable
  public IdeAndroidArtifact getArtifactForAndroidTest() {
    return myAndroidModel.getAndroidProject().getProjectType() == IdeAndroidProjectType.PROJECT_TYPE_TEST ?
           myVariant.getMainArtifact() :
           myVariant.getAndroidTestArtifact();
  }

  private static Logger getLogger() {
    return Logger.getInstance(GradleApplicationIdProvider.class);
  }
}
