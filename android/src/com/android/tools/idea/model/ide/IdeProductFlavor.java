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
package com.android.tools.idea.model.ide;

import com.android.annotations.Nullable;
import com.android.builder.model.*;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.Serializable;
import java.util.*;

/**
 * Creates a deep copy of {@link ProductFlavor}.
 *
 * @see IdeAndroidProject
 */
final public class IdeProductFlavor implements ProductFlavor, Serializable {
  @NotNull private final String myName;
  @NotNull private final Map<String,ClassField> myBuildConfigFields;
  @NotNull private final Map<String,ClassField> myResValues;
  @NotNull private final Collection<File> myProguardFiles;
  @NotNull private final Collection<File> myConsumerProguardFiles;
  @NotNull private final Collection<File> myTestProguardFiles;
  @NotNull private final Map<String,Object> myManifestPlaceholders;
  @NotNull private final List<File> myJarJarRuleFiles;
  @NotNull private final Map<String,String> myTestInstrumentationRunnerArguments;
  @NotNull private final Collection<String> myResourceConfigurations;
  @NotNull private final VectorDrawablesOptions myVectorDrawables;
  @Nullable private final String myApplicationIdSuffix;
  @Nullable private final String myVersionNameSuffix;
  @Nullable private final Boolean myMultiDexEnabled;
  @Nullable private final File myMultiDexKeepFile;
  @Nullable private final File myMultiDexKeepProguard;
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

  public IdeProductFlavor(@NotNull ProductFlavor flavor) {
    myName = flavor.getName();

    Map<String, ClassField> flBuildConfigFields = flavor.getBuildConfigFields();
    myBuildConfigFields = new HashMap<>(flBuildConfigFields.size());
    for (String e:flBuildConfigFields.keySet()) {
      myBuildConfigFields.put(e, new IdeClassField(flBuildConfigFields.get(e)));
    }

    Map<String, ClassField> flResValues = flavor.getResValues();
    myResValues = new HashMap<>(flResValues.size());
    for (String e:flResValues.keySet()) {
      myBuildConfigFields.put(e, new IdeClassField(flResValues.get(e)));
    }

    myProguardFiles = new ArrayList<>(flavor.getProguardFiles());
    myConsumerProguardFiles = new ArrayList<>(flavor.getConsumerProguardFiles());
    myTestProguardFiles = new ArrayList<>(flavor.getTestProguardFiles());
    myManifestPlaceholders = new HashMap<>(flavor.getManifestPlaceholders());
    myJarJarRuleFiles = new ArrayList<>(flavor.getJarJarRuleFiles());

    myTestInstrumentationRunnerArguments = new HashMap<>(flavor.getTestInstrumentationRunnerArguments());
    myResourceConfigurations = new ArrayList<>(flavor.getResourceConfigurations());
    myVectorDrawables = new IdeVectorDrawablesOptions(flavor.getVectorDrawables());
    myApplicationIdSuffix = flavor.getApplicationIdSuffix();
    myVersionNameSuffix = flavor.getVersionNameSuffix();
    myMultiDexEnabled = flavor.getMultiDexEnabled();
    myMultiDexKeepFile = flavor.getMultiDexKeepFile();
    myMultiDexKeepProguard = flavor.getMultiDexKeepProguard();
    myDimension = flavor.getDimension();
    myApplicationId = flavor.getApplicationId();
    myVersionCode = flavor.getVersionCode();
    myVersionName = flavor.getVersionName();

    ApiVersion flMinSdkVersion = flavor.getMinSdkVersion();
    myMinSdkVersion = flMinSdkVersion == null ? null : new IdeApiVersion(flMinSdkVersion);

    ApiVersion flTargetSdkVersion = flavor.getTargetSdkVersion();
    myTargetSdkVersion = flTargetSdkVersion == null ? null : new IdeApiVersion(flTargetSdkVersion);

    myMaxSdkVersion = flavor.getMaxSdkVersion();
    myRenderscriptTargetApi = flavor.getRenderscriptTargetApi();
    myRenderscriptSupportModeEnabled = flavor.getRenderscriptSupportModeEnabled();
    myRenderscriptSupportModeBlasEnabled = flavor.getRenderscriptSupportModeBlasEnabled();
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
  public String getName() {
    return myName;
  }

  @Override
  @NotNull
  public Map<String, ClassField> getBuildConfigFields() {
    return myBuildConfigFields;
  }

  @Override
  @NotNull
  public Map<String, ClassField> getResValues() {
    return myResValues;
  }

  @Override
  @NotNull
  public Collection<File> getProguardFiles() {
    return myProguardFiles;
  }

  @Override
  @NotNull
  public Collection<File> getConsumerProguardFiles() {
    return myConsumerProguardFiles;
  }

  @Override
  @NotNull
  public Collection<File> getTestProguardFiles() {
    return myTestProguardFiles;
  }

  @Override
  @NotNull
  public Map<String, Object> getManifestPlaceholders() {
    return myManifestPlaceholders;
  }

  @Override
  @NotNull
  public List<File> getJarJarRuleFiles() {
    return myJarJarRuleFiles;
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
  public String getApplicationIdSuffix() {
    return myApplicationIdSuffix;
  }

  @Override
  @Nullable
  public String getVersionNameSuffix() {
    return myVersionNameSuffix;
  }

  @Override
  @Nullable
  public Boolean getMultiDexEnabled() {
    return myMultiDexEnabled;
  }

  @Override
  @Nullable
  public File getMultiDexKeepFile() {
    return myMultiDexKeepFile;
  }

  @Override
  @Nullable
  public File getMultiDexKeepProguard() {
    return myMultiDexKeepProguard;
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
    if (this == o) return true;
    if (!(o instanceof ProductFlavor)) return false;
    ProductFlavor flavor = (ProductFlavor)o;
    return Objects.equals(getName(), flavor.getName()) &&
           Objects.equals(getApplicationIdSuffix(), flavor.getApplicationIdSuffix()) &&
           Objects.equals(getVersionNameSuffix(), flavor.getVersionNameSuffix()) &&
           Objects.equals(getBuildConfigFields(), flavor.getBuildConfigFields()) &&
           Objects.equals(getResValues(), flavor.getResValues()) &&
           Objects.equals(getProguardFiles(), flavor.getProguardFiles()) &&
           Objects.equals(getConsumerProguardFiles(), flavor.getConsumerProguardFiles()) &&
           Objects.equals(getTestProguardFiles(), flavor.getTestProguardFiles()) &&
           Objects.equals(getManifestPlaceholders(), flavor.getManifestPlaceholders()) &&
           Objects.equals(getMultiDexEnabled(), flavor.getMultiDexEnabled()) &&
           Objects.equals(getMultiDexKeepFile(), flavor.getMultiDexKeepFile()) &&
           Objects.equals(getMultiDexKeepProguard(), flavor.getMultiDexKeepProguard()) &&
           Objects.equals(getJarJarRuleFiles(), flavor.getJarJarRuleFiles()) &&
           Objects.equals(getDimension(), flavor.getDimension()) &&
           Objects.equals(getApplicationId(), flavor.getApplicationId()) &&
           Objects.equals(getVersionCode(), flavor.getVersionCode()) &&
           Objects.equals(getVersionName(), flavor.getVersionName()) &&
           Objects.equals(getMinSdkVersion(), flavor.getMinSdkVersion()) &&
           Objects.equals(getTargetSdkVersion(), flavor.getTargetSdkVersion()) &&
           Objects.equals(getMaxSdkVersion(), flavor.getMaxSdkVersion()) &&
           Objects.equals(getRenderscriptTargetApi(), flavor.getRenderscriptTargetApi()) &&
           Objects.equals(getRenderscriptSupportModeEnabled(), flavor.getRenderscriptSupportModeEnabled()) &&
           Objects.equals(getRenderscriptSupportModeBlasEnabled(), flavor.getRenderscriptSupportModeBlasEnabled()) &&
           Objects.equals(getRenderscriptNdkModeEnabled(), flavor.getRenderscriptNdkModeEnabled()) &&
           Objects.equals(getTestApplicationId(), flavor.getTestApplicationId()) &&
           Objects.equals(getTestInstrumentationRunner(), flavor.getTestInstrumentationRunner()) &&
           Objects.equals(getTestInstrumentationRunnerArguments(), flavor.getTestInstrumentationRunnerArguments()) &&
           Objects.equals(getTestHandleProfiling(), flavor.getTestHandleProfiling()) &&
           Objects.equals(getTestFunctionalTest(), flavor.getTestFunctionalTest()) &&
           Objects.equals(getResourceConfigurations(), flavor.getResourceConfigurations()) &&
           Objects.equals(getSigningConfig(), flavor.getSigningConfig()) &&
           Objects.equals(getVectorDrawables(), flavor.getVectorDrawables()) &&
           Objects.equals(getWearAppUnbundled(), flavor.getWearAppUnbundled());
  }

  @Override
  public int hashCode() {
    return Objects
      .hash(getName(), getBuildConfigFields(), getResValues(), getProguardFiles(), getConsumerProguardFiles(), getTestProguardFiles(),
            getManifestPlaceholders(), getJarJarRuleFiles(), getTestInstrumentationRunnerArguments(), getResourceConfigurations(),
            getVectorDrawables(), getApplicationIdSuffix(), getVersionNameSuffix(), getMultiDexEnabled(), getMultiDexKeepFile(),
            getMultiDexKeepProguard(), getDimension(), getApplicationId(), getVersionCode(), getVersionName(), getMinSdkVersion(),
            getTargetSdkVersion(), getMaxSdkVersion(), getRenderscriptTargetApi(), getRenderscriptSupportModeEnabled(),
            getRenderscriptSupportModeBlasEnabled(), getRenderscriptNdkModeEnabled(), getTestApplicationId(),
            getTestInstrumentationRunner(),
            getTestHandleProfiling(), getTestFunctionalTest(), getSigningConfig(), getWearAppUnbundled());
  }
}