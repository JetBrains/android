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
package com.android.tools.idea.gradle.stubs.android;

import com.android.builder.model.*;
import com.android.tools.idea.model.IdeApiVersion;
import com.android.tools.idea.model.IdeSigningConfig;
import com.android.tools.idea.model.IdeVectorDrawablesOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Creates a version of {@link ProductFlavorStub} that does not cause unsupported exceptions, used for testing {@link IdeAndroidProject}.
 *
 */
public class IdeProductFlavorStub extends ProductFlavorStub {
  @NotNull private final static Map<String,ClassField> myBuildConfigFields = Collections.emptyMap();
  @NotNull private final static Map<String,String> myTestInstrumentationRunnerArguments = Collections.emptyMap();
  @NotNull private final static Collection<String> myResourceConfigurations = Collections.emptyList();
  @NotNull private final static VectorDrawablesOptions myVectorDrawables = new IdeVectorDrawablesOptions(Collections.emptySet(), true);
  @NotNull private final static Map<String,Object> myManifestPlaceholders = Collections.emptyMap();
  @NotNull private final static List<File> myJarJarRuleFiles = Collections.emptyList();
  @NotNull private final static Map<String,ClassField> myResValues = Collections.emptyMap();
  @NotNull private final static List<File> myProguardFiles = Collections.emptyList();
  @NotNull private final static List<File> myConsumerProguardFiles = Collections.emptyList();
  @NotNull private final static Collection<File> myTestProguardFiles = Collections.emptyList();
  @Nullable private final static String myVersionNameSuffix = "VersionNameSuffix";
  @Nullable private final static String myApplicationIdSuffix = "ApplicationIdSuffix";
  @Nullable private final static String myDimension = "Dimension";
  @Nullable private final static String myApplicationId = "ApplicationId";
  @Nullable private final static String myVersionName = "VersionName";
  @Nullable private final static Integer myVersionCode = 2;
  @Nullable private final static ApiVersion myMinSdkVersion = new IdeApiVersion("MinApiString", "MinCodename", 3);
  @Nullable private final static ApiVersion myTargetSdkVersion = new IdeApiVersion("TargetedApiString", "TargetedCodename", 4);
  @Nullable private final static Integer myMaxSdkVersion = 5;
  @Nullable private final static Integer myRenderscriptTargetApi = 6;
  @Nullable private final static Boolean myRenderscriptSupportModeEnabled = true;
  @Nullable private final static Boolean myRenderscriptSupportModeBlasEnabled = true;
  @Nullable private final static Boolean myRenderscriptNdkModeEnabled = true;
  @Nullable private final static String myTestApplicationId = "TestApplicationId";
  @Nullable private final static String myTestInstrumentationRunner = "TestInstrumentationRunner";
  @Nullable private final static Boolean myTestHandleProfiling = true;
  @Nullable private final static Boolean myTestFunctionalTest = true;
  @Nullable private final static SigningConfig mySigningConfig = new IdeSigningConfig("FlavorSigningName", null,"storePassword",
                                                               "keyAlias", "keyPassword", "storeType",
                                                               true, true, true);
  @Nullable private final static Boolean myWearAppUnbundled = true;
  @Nullable private final static Boolean myMultiDexEnabled = false;
  @Nullable private final static File myMultiDexKeepFile = null;
  @Nullable private final static File myMultiDexKeepProguard = null;

  /**
   * Creates a new {@code IdeProductFlavorStub}.
   *
   * @param name the name of the product flavor.
   */
  public IdeProductFlavorStub(@NotNull String name) {
    super(name);
  }

  @Override
  @NotNull
  public Map<String, ClassField> getBuildConfigFields() {
    return myBuildConfigFields;
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
  public Map<String, ClassField> getResValues() {
    return myResValues;
  }

  @Override
  @NotNull
  public List<File> getProguardFiles() {
    return myProguardFiles;
  }

  @Override
  @NotNull
  public List<File> getConsumerProguardFiles() {
    return myConsumerProguardFiles;
  }

  @Override
  @NotNull
  public Collection<File> getTestProguardFiles() {
    return myTestProguardFiles;
  }

  @Override
  @Nullable
  public String getVersionNameSuffix() {
    return myVersionNameSuffix;
  }

  @Override
  @Nullable
  public String getApplicationIdSuffix() {
    return myApplicationIdSuffix;
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
  public String getVersionName() {
    return myVersionName;
  }

  @Override
  @Nullable
  public Integer getVersionCode() {
    return myVersionCode;
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
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ProductFlavor)) return false;

    ProductFlavor flavor = (ProductFlavor)o;

    if (getBuildConfigFields() != null
        ? !getBuildConfigFields().equals(flavor.getBuildConfigFields())
        : flavor.getBuildConfigFields() != null) {
      return false;
    }

    if (!this.getApplicationId().equals(flavor.getApplicationId())) return false;
    if (!this.getApplicationIdSuffix().equals(flavor.getApplicationIdSuffix())) return false;
    if (!this.getBuildConfigFields().equals(flavor.getBuildConfigFields())) return false;
    if (!this.getConsumerProguardFiles().equals(flavor.getConsumerProguardFiles())) return false;
    if (!this.getDimension().equals(flavor.getDimension())) return false;
    if (!this.getJarJarRuleFiles().equals(flavor.getJarJarRuleFiles())) return false;
    if (!this.getManifestPlaceholders().equals(flavor.getManifestPlaceholders())) return false;
    if (!this.getMaxSdkVersion().equals(flavor.getMaxSdkVersion())) return false;
    if (!this.getMinSdkVersion().equals(flavor.getMinSdkVersion())) return false;
    if (!this.getMultiDexEnabled().equals(flavor.getMultiDexEnabled())) return false;
    if (flavor.getMultiDexKeepFile() != null) return false;
    if (flavor.getMultiDexKeepProguard() != null) return false;
    if (!this.getName().equals(flavor.getName())) return false;
    if (!this.getProguardFiles().equals(flavor.getProguardFiles())) return false;
    if (!this.getRenderscriptNdkModeEnabled().equals(flavor.getRenderscriptNdkModeEnabled())) return false;
    if (!this.getRenderscriptSupportModeBlasEnabled().equals(flavor.getRenderscriptSupportModeBlasEnabled())) return false;
    if (!this.getRenderscriptSupportModeEnabled().equals(flavor.getRenderscriptSupportModeEnabled())) return false;
    if (!this.getRenderscriptTargetApi().equals(flavor.getRenderscriptTargetApi())) return false;
    if (!this.getResourceConfigurations().equals(flavor.getResourceConfigurations())) return false;
    if (!this.getResValues().equals(flavor.getResValues())) return false;
    if (!this.getSigningConfig().equals(flavor.getSigningConfig())) return false;
    if (!this.getTargetSdkVersion().equals(flavor.getTargetSdkVersion())) return false;
    if (!this.getTestApplicationId().equals(flavor.getTestApplicationId())) return false;
    if (!this.getTestFunctionalTest().equals(flavor.getTestFunctionalTest())) return false;
    if (!this.getTestHandleProfiling().equals(flavor.getTestHandleProfiling())) return false;
    if (!this.getTestInstrumentationRunner().equals(flavor.getTestInstrumentationRunner())) return false;
    if (!this.getTestInstrumentationRunnerArguments().equals(flavor.getTestInstrumentationRunnerArguments())) return false;
    if (!this.getTestProguardFiles().equals(flavor.getTestProguardFiles())) return false;
    if (!this.getVectorDrawables().equals(flavor.getVectorDrawables())) return false;
    if (!this.getVersionCode().equals(flavor.getVersionCode())) return false;
    if (!this.getVersionName().equals(flavor.getVersionName())) return false;
    if (!this.getVersionNameSuffix().equals(flavor.getVersionNameSuffix())) return false;
    if (!this.getWearAppUnbundled().equals(flavor.getWearAppUnbundled())) return false;

    return true;
  }

}
