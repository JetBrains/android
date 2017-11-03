/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.model.ide.android.stubs;

import com.android.builder.model.ApiVersion;
import com.android.builder.model.ProductFlavor;
import com.android.builder.model.SigningConfig;
import com.android.builder.model.VectorDrawablesOptions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

public class ProductFlavorStub extends BaseConfigStub implements ProductFlavor {
  @NotNull private final Map<String, String> myTestInstrumentationRunnerArguments;
  @NotNull private final Collection<String> myResourceConfigurations;
  @NotNull private final VectorDrawablesOptions myVectorDrawables;
  @Nullable private final String myDimension;
  @Nullable private final String myApplicationId;
  @Nullable private final Integer myVersionCode;
  @Nullable private final String myVersionName;
  @Nullable private final ApiVersion myMinSdkVersion;
  @Nullable private final ApiVersion myTargetSdkVersion;
  @Nullable private final Integer myMaxSdkVersion;
  @Nullable private final Integer myRenderscriptTargetApi;
  @Nullable private final Boolean myRenderscriptSupportModeEnabled;
  @Nullable private final Boolean myRenderscriptSupportModeBlasEnabled;
  @Nullable private final Boolean myRenderscriptNdkModeEnabled;
  @Nullable private final String myTestApplicationId;
  @Nullable private final String myTestInstrumentationRunner;
  @Nullable private final Boolean myTestHandleProfiling;
  @Nullable private final Boolean myTestFunctionalTest;
  @Nullable private final SigningConfig mySigningConfig;
  @Nullable private final Boolean myWearAppUnbundled;

  public ProductFlavorStub() {
    this(ImmutableMap.of("testInstrumentationRunnerArgumentsKey", "testInstrumentationRunnerArgumentsValue"),
         Lists.newArrayList("resourceConfiguration"), new VectorDrawablesOptionsStub(), "dimension", "applicationId", 1, "versionName",
         new ApiVersionStub(), new ApiVersionStub(), 2, 3, true, true, true, "testApplicationId", "testInstrumentationRunner", true, true,
         new SigningConfigStub(), true);
  }

  public ProductFlavorStub(@NotNull Map<String, String> testInstrumentationRunnerArguments,
                           @NotNull Collection<String> resourceConfigurations,
                           @NotNull VectorDrawablesOptions vectorDrawables,
                           @Nullable String dimension,
                           @Nullable String applicationId,
                           @Nullable Integer versionCode,
                           @Nullable String versionName,
                           @Nullable ApiVersion minSdkVersion,
                           @Nullable ApiVersion targetSdkVersion,
                           @Nullable Integer maxSdkVersion,
                           @Nullable Integer renderscriptTargetApi,
                           @Nullable Boolean renderscriptSupportModeEnabled,
                           @Nullable Boolean renderscriptSupportModeBlasEnabled,
                           @Nullable Boolean renderscriptNdkModeEnabled,
                           @Nullable String testApplicationId,
                           @Nullable String testInstrumentationRunner,
                           @Nullable Boolean testHandleProfiling,
                           @Nullable Boolean testFunctionalTest,
                           @Nullable SigningConfig signingConfig,
                           @Nullable Boolean wearAppUnbundled) {
    myTestInstrumentationRunnerArguments = testInstrumentationRunnerArguments;
    myResourceConfigurations = resourceConfigurations;
    myVectorDrawables = vectorDrawables;
    myDimension = dimension;
    myApplicationId = applicationId;
    myVersionCode = versionCode;
    myVersionName = versionName;
    myMinSdkVersion = minSdkVersion;
    myTargetSdkVersion = targetSdkVersion;
    myMaxSdkVersion = maxSdkVersion;
    myRenderscriptTargetApi = renderscriptTargetApi;
    myRenderscriptSupportModeEnabled = renderscriptSupportModeEnabled;
    myRenderscriptSupportModeBlasEnabled = renderscriptSupportModeBlasEnabled;
    myRenderscriptNdkModeEnabled = renderscriptNdkModeEnabled;
    myTestApplicationId = testApplicationId;
    myTestInstrumentationRunner = testInstrumentationRunner;
    myTestHandleProfiling = testHandleProfiling;
    myTestFunctionalTest = testFunctionalTest;
    mySigningConfig = signingConfig;
    myWearAppUnbundled = wearAppUnbundled;
  }

  @Override
  @NotNull
  public Map<String, String> getTestInstrumentationRunnerArguments() {
    return myTestInstrumentationRunnerArguments;
  }

  @Override
  @NotNull
  public Collection<String> getResourceConfigurations() {
    return myResourceConfigurations;
  }

  @Override
  @NotNull
  public VectorDrawablesOptions getVectorDrawables() {
    return myVectorDrawables;
  }

  @Override
  @Nullable
  public String getDimension() {
    return myDimension;
  }

  @Override
  @Nullable
  public String getApplicationId() {
    return myApplicationId;
  }

  @Override
  @Nullable
  public Integer getVersionCode() {
    return myVersionCode;
  }

  @Override
  @Nullable
  public String getVersionName() {
    return myVersionName;
  }

  @Override
  @Nullable
  public ApiVersion getMinSdkVersion() {
    return myMinSdkVersion;
  }

  @Override
  @Nullable
  public ApiVersion getTargetSdkVersion() {
    return myTargetSdkVersion;
  }

  @Override
  @Nullable
  public Integer getMaxSdkVersion() {
    return myMaxSdkVersion;
  }

  @Override
  @Nullable
  public Integer getRenderscriptTargetApi() {
    return myRenderscriptTargetApi;
  }

  @Override
  @Nullable
  public Boolean getRenderscriptSupportModeEnabled() {
    return myRenderscriptSupportModeEnabled;
  }

  @Override
  @Nullable
  public Boolean getRenderscriptSupportModeBlasEnabled() {
    return myRenderscriptSupportModeBlasEnabled;
  }

  @Override
  @Nullable
  public Boolean getRenderscriptNdkModeEnabled() {
    return myRenderscriptNdkModeEnabled;
  }

  @Override
  @Nullable
  public String getTestApplicationId() {
    return myTestApplicationId;
  }

  @Override
  @Nullable
  public String getTestInstrumentationRunner() {
    return myTestInstrumentationRunner;
  }

  @Override
  @Nullable
  public Boolean getTestHandleProfiling() {
    return myTestHandleProfiling;
  }

  @Override
  @Nullable
  public Boolean getTestFunctionalTest() {
    return myTestFunctionalTest;
  }

  @Override
  @Nullable
  public SigningConfig getSigningConfig() {
    return mySigningConfig;
  }

  @Override
  @Nullable
  public Boolean getWearAppUnbundled() {
    return myWearAppUnbundled;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ProductFlavor)) {
      return false;
    }
    ProductFlavor that = (ProductFlavor)o;
    return Objects.equals(getName(), that.getName()) &&
           Objects.equals(getResValues(), that.getResValues()) &&
           Objects.equals(getProguardFiles(), that.getProguardFiles()) &&
           Objects.equals(getConsumerProguardFiles(), that.getConsumerProguardFiles()) &&
           Objects.equals(getManifestPlaceholders(), that.getManifestPlaceholders()) &&
           Objects.equals(getApplicationIdSuffix(), that.getApplicationIdSuffix()) &&
           Objects.equals(getVersionNameSuffix(), that.getVersionNameSuffix()) &&
           Objects.equals(getTestInstrumentationRunnerArguments(), that.getTestInstrumentationRunnerArguments()) &&
           Objects.equals(getResourceConfigurations(), that.getResourceConfigurations()) &&
           equals(that, ProductFlavor::getVectorDrawables) &&
           Objects.equals(getDimension(), that.getDimension()) &&
           Objects.equals(getApplicationId(), that.getApplicationId()) &&
           Objects.equals(getVersionCode(), that.getVersionCode()) &&
           Objects.equals(getVersionName(), that.getVersionName()) &&
           Objects.equals(getMinSdkVersion(), that.getMinSdkVersion()) &&
           Objects.equals(getTargetSdkVersion(), that.getTargetSdkVersion()) &&
           Objects.equals(getMaxSdkVersion(), that.getMaxSdkVersion()) &&
           equals(that, ProductFlavor::getRenderscriptTargetApi) &&
           equals(that, ProductFlavor::getRenderscriptSupportModeEnabled) &&
           equals(that, ProductFlavor::getRenderscriptSupportModeBlasEnabled) &&
           equals(that, ProductFlavor::getRenderscriptNdkModeEnabled) &&
           Objects.equals(getTestApplicationId(), that.getTestApplicationId()) &&
           Objects.equals(getTestInstrumentationRunner(), that.getTestInstrumentationRunner()) &&
           equals(that, ProductFlavor::getTestHandleProfiling) &&
           equals(that, ProductFlavor::getTestFunctionalTest) &&
           Objects.equals(getSigningConfig(), that.getSigningConfig()) &&
           equals(that, ProductFlavor::getWearAppUnbundled);
  }

  @Override
  public int hashCode() {
    return Objects.hash(getName(), getResValues(), getProguardFiles(), getConsumerProguardFiles(), getManifestPlaceholders(),
                        getApplicationIdSuffix(), getVersionNameSuffix(), getTestInstrumentationRunnerArguments(),
                        getResourceConfigurations(), getVectorDrawables(), getDimension(), getApplicationId(), getVersionCode(),
                        getVersionName(), getMinSdkVersion(), getTargetSdkVersion(), getMaxSdkVersion(), getRenderscriptTargetApi(),
                        getRenderscriptSupportModeEnabled(), getRenderscriptSupportModeBlasEnabled(), getRenderscriptNdkModeEnabled(),
                        getTestApplicationId(), getTestInstrumentationRunner(), getTestHandleProfiling(), getTestFunctionalTest(),
                        getSigningConfig(), getWearAppUnbundled());
  }

  @Override
  public String toString() {
    return "ProductFlavorStub{" +
           "myTestInstrumentationRunnerArguments=" + myTestInstrumentationRunnerArguments +
           ", myResourceConfigurations=" + myResourceConfigurations +
           ", myVectorDrawables=" + myVectorDrawables +
           ", myDimension='" + myDimension + '\'' +
           ", myApplicationId='" + myApplicationId + '\'' +
           ", myVersionCode=" + myVersionCode +
           ", myVersionName='" + myVersionName + '\'' +
           ", myMinSdkVersion=" + myMinSdkVersion +
           ", myTargetSdkVersion=" + myTargetSdkVersion +
           ", myMaxSdkVersion=" + myMaxSdkVersion +
           ", myRenderscriptTargetApi=" + myRenderscriptTargetApi +
           ", myRenderscriptSupportModeEnabled=" + myRenderscriptSupportModeEnabled +
           ", myRenderscriptSupportModeBlasEnabled=" + myRenderscriptSupportModeBlasEnabled +
           ", myRenderscriptNdkModeEnabled=" + myRenderscriptNdkModeEnabled +
           ", myTestApplicationId='" + myTestApplicationId + '\'' +
           ", myTestInstrumentationRunner='" + myTestInstrumentationRunner + '\'' +
           ", myTestHandleProfiling=" + myTestHandleProfiling +
           ", myTestFunctionalTest=" + myTestFunctionalTest +
           ", mySigningConfig=" + mySigningConfig +
           ", myWearAppUnbundled=" + myWearAppUnbundled +
           "} " + super.toString();
  }
}