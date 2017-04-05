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
package com.android.tools.idea.model;

import com.android.annotations.Nullable;
import com.android.builder.model.*;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.*;

/**
 * Creates a deep copy of {@link ProductFlavor}.
 *
 * @see IdeAndroidProject
 */
public class IdeProductFlavor extends IdeBaseConfig implements ProductFlavor, Serializable {
  @NotNull private final Map<String,String> myTestInstrumentationRunnerArguments;
  @NotNull private final Collection<String> myResourceConfigurations;
  @NotNull private final VectorDrawablesOptions myVectorDrawables;
  @Nullable private final String myDimension;
  @Nullable private final String myApplicationId;
  @Nullable private final Integer myVersionCode;
  @Nullable private final String myVersionName;
  @Nullable private final ApiVersion myMinSdkVersion;
  @Nullable private final ApiVersion myTargetedSdkVersion;
  @Nullable private final Integer myMaxSdkVersion;
  @Nullable private final Integer myRenderscriptTargetApi;
  @Nullable private final Boolean myRenderscriptSupporModeEnabled;
  @Nullable private final Boolean myRenderscriptSupporModeBlasEnabled;
  @Nullable private final Boolean myRenderscriptNdkModeEnabled;
  @Nullable private final String myTestApplicationId;
  @Nullable private final String myTestInstrumentationRunner;
  @Nullable private final Boolean myTestHandleProfiling;
  @Nullable private final Boolean myTestFunctionalTest;
  @Nullable private final SigningConfig mySigningConfig;
  @Nullable private final Boolean myWearAppUnbundled;

  public IdeProductFlavor(@NotNull ProductFlavor flavor) {
    super(flavor);

    myTestInstrumentationRunnerArguments = new HashMap<>(flavor.getTestInstrumentationRunnerArguments());
    myResourceConfigurations = new ArrayList<>(flavor.getResourceConfigurations());
    myVectorDrawables = new IdeVectorDrawablesOptions(flavor.getVectorDrawables());
    myDimension = flavor.getDimension();
    myApplicationId = flavor.getApplicationId();
    myVersionCode = flavor.getVersionCode();
    myVersionName = flavor.getVersionName();

    ApiVersion flMinSdkVersion = flavor.getMinSdkVersion();
    myMinSdkVersion = flMinSdkVersion == null ? null : new IdeApiVersion(flMinSdkVersion);

    ApiVersion flTargetSdkVersion = flavor.getTargetSdkVersion();
    myTargetedSdkVersion = flTargetSdkVersion == null ? null : new IdeApiVersion(flTargetSdkVersion);

    myMaxSdkVersion = flavor.getMaxSdkVersion();
    myRenderscriptTargetApi = flavor.getRenderscriptTargetApi();
    myRenderscriptSupporModeEnabled = flavor.getRenderscriptSupportModeEnabled();
    myRenderscriptSupporModeBlasEnabled = flavor.getRenderscriptSupportModeBlasEnabled();
    myRenderscriptNdkModeEnabled = flavor.getRenderscriptNdkModeEnabled();
    myTestApplicationId = flavor.getTestApplicationId();
    myTestInstrumentationRunner = flavor.getTestInstrumentationRunner();
    myTestHandleProfiling = flavor.getTestHandleProfiling();
    myTestFunctionalTest = flavor.getTestFunctionalTest();

    SigningConfig flSigningConfig = flavor.getSigningConfig();
    mySigningConfig = flSigningConfig == null ? null : new IdeSigningConfig(flSigningConfig);

    myWearAppUnbundled = flavor.getWearAppUnbundled();
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
    return myTargetedSdkVersion;
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
    return myRenderscriptSupporModeEnabled;
  }

  @Override
  @Nullable
  public Boolean getRenderscriptSupportModeBlasEnabled() {
    return myRenderscriptSupporModeBlasEnabled;
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
}