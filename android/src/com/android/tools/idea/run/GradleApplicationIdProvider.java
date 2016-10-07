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
package com.android.tools.idea.run;

import com.android.builder.model.Variant;
import com.android.tools.idea.gradle.AndroidGradleModel;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

/**
 * Application id provider for Gradle projects.
 */
public class GradleApplicationIdProvider implements ApplicationIdProvider {

  /** Default suffix for test packages (as added by Android Gradle plugin) */
  private static final String DEFAULT_TEST_PACKAGE_SUFFIX = ".test";

  @NotNull
  private final AndroidFacet myFacet;

  public GradleApplicationIdProvider(@NotNull AndroidFacet facet) {
    myFacet = facet;
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
}
