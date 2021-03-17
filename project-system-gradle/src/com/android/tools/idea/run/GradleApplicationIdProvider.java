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

import static com.android.AndroidProjectTypes.PROJECT_TYPE_DYNAMIC_FEATURE;
import static com.android.AndroidProjectTypes.PROJECT_TYPE_INSTANTAPP;
import static com.android.AndroidProjectTypes.PROJECT_TYPE_LIBRARY;
import static com.android.AndroidProjectTypes.PROJECT_TYPE_TEST;
import static com.android.tools.idea.gradle.util.GradleUtil.findModuleByGradlePath;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradlePath;

import com.android.builder.model.InstantAppProjectBuildOutput;
import com.android.builder.model.InstantAppVariantBuildOutput;
import com.android.ide.common.gradle.model.IdeAndroidProjectType;
import com.android.ide.common.gradle.model.IdeTestedTargetVariant;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.run.PostBuildModel;
import com.android.tools.idea.gradle.run.PostBuildModelProvider;
import com.android.tools.idea.gradle.util.DynamicAppUtils;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import java.util.Collection;
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
  @NotNull private final PostBuildModelProvider myOutputModelProvider;

  public GradleApplicationIdProvider(@NotNull AndroidFacet facet) {
    this(facet, () -> null);
  }

  public GradleApplicationIdProvider(@NotNull AndroidFacet facet, @NotNull PostBuildModelProvider outputModelProvider) {
    myFacet = facet;
    myOutputModelProvider = outputModelProvider;
  }

  @Override
  @NotNull
  public String getPackageName() throws ApkProvisionException {
    // Android library project doesn't produce APK except for instrumentation tests. And for instrumentation test,
    // AGP creates instrumentation APK only. Both test code and library code will be packaged into an instrumentation APK.
    // This is called self-instrumenting test: https://source.android.com/compatibility/tests/development/instr-self-e2e
    // For this reason, this method should return test package name for Android library project.
    if (myFacet.getConfiguration().getProjectType() == PROJECT_TYPE_LIBRARY) {
      String testPackageName = getTestPackageName();
      if (testPackageName != null) {
        return testPackageName;
      }
      else {
        getLogger().warn("Could not get applicationId for library module.");
      }
    }

    if (myFacet.getConfiguration().getProjectType() == PROJECT_TYPE_TEST) {
      AndroidFacet targetFacet = getTargetFacet();
      if (targetFacet != null) {
        GradleApplicationIdProvider targetApplicationProvider = new GradleApplicationIdProvider(targetFacet, myOutputModelProvider);
        return targetApplicationProvider.getPackageName();
      }
      else {
        getLogger().warn("Could not get applicationId for tested module.");
      }
    }

    if (myFacet.getConfiguration().getProjectType() == PROJECT_TYPE_INSTANTAPP) {
      String applicationId = tryToGetInstantAppApplicationId();
      if (applicationId != null) {
        return applicationId;
      }
      else {
        getLogger().warn("Could not get instant app applicationId from post build model.");
      }
    }

    if (myFacet.getConfiguration().getProjectType() == PROJECT_TYPE_DYNAMIC_FEATURE) {
      String applicationId = tryToGetDynamicFeatureApplicationId();
      if (applicationId != null) {
        return applicationId;
      }
    }

    return ApkProviderUtil.computePackageName(myFacet);
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

    return ApkProviderUtil.computePackageName(baseAppFacet);
  }

  @Nullable
  private String tryToGetInstantAppApplicationId() {
    AndroidModuleModel androidModel = AndroidModuleModel.get(myFacet);
    if (androidModel == null ||
        !androidModel.getFeatures().isPostBuildSyncSupported() ||
        androidModel.getAndroidProject().getProjectType() != IdeAndroidProjectType.PROJECT_TYPE_INSTANTAPP) {
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
      if (variantBuildOutput.getName().equals(androidModel.getSelectedVariant().getName())) {
        return variantBuildOutput.getApplicationId();
      }
    }

    return null;
  }

  @Override
  public String getTestPackageName() throws ApkProvisionException {
    if (myFacet.getConfiguration().getProjectType() == PROJECT_TYPE_TEST) {
      return ApkProviderUtil.computePackageName(myFacet);
    }

    AndroidModuleModel androidModel = AndroidModuleModel.get(myFacet);
    // This is a Gradle project, there must be an AndroidGradleModel, but to avoid NPE we gracefully handle a null androidModel.
    // In the case of Gradle projects, either the merged flavor provides a test package name,
    // or we just append ".test" to the source package name.
    String testPackageName = androidModel == null ? null : androidModel.getSelectedVariant().getTestApplicationId();
    if (testPackageName != null) {
      return testPackageName;
    }

    if (myFacet.getConfiguration().getProjectType() == PROJECT_TYPE_DYNAMIC_FEATURE
        || myFacet.getConfiguration().getProjectType() == PROJECT_TYPE_LIBRARY) {
      return ApkProviderUtil.computePackageName(myFacet) + DEFAULT_TEST_PACKAGE_SUFFIX;
    }
    else {
      return getPackageName() + DEFAULT_TEST_PACKAGE_SUFFIX;
    }
  }

  @Nullable
  private AndroidFacet getTargetFacet() {
    AndroidModuleModel androidModel = AndroidModuleModel.get(myFacet);
    if (androidModel == null) {
      return null;
    }

    Collection<IdeTestedTargetVariant> targetVariants = androidModel.getSelectedVariant().getTestedTargetVariants();
    if (targetVariants.size() != 1) {
      // There is no tested variant or more than one (what should never happen currently) and then we can't get package name.
      return null;
    }

    IdeTestedTargetVariant targetVariant = targetVariants.iterator().next();
    Module targetModule = findModuleByGradlePath(myFacet.getModule().getProject(), targetVariant.getTargetProjectPath());
    if (targetModule == null) {
      return null;
    }

    return AndroidFacet.getInstance(targetModule);
  }

  private static Logger getLogger() {
    return Logger.getInstance(GradleApplicationIdProvider.class);
  }
}
