/*
 * Copyright (C) 2013 The Android Open Source Project
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;

public class ProductFlavorStub implements ProductFlavor {
  @NotNull private final String myName;
  @Nullable private final String myDimension;

  /**
   * Creates a new {@code ProductFlavorStub}.
   *
   * @param name the name of the product flavor.
   */
  ProductFlavorStub(@NotNull String name, @Nullable String dimension) {
    this.myName = name;
    this.myDimension = dimension;
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  @Nullable
  public String getApplicationIdSuffix() {
    return null;
  }

  @Override
  @Nullable
  public String getVersionNameSuffix() {
    return null;
  }

  @Override
  @Nullable
  public String getDimension() {
    return myDimension;
  }

  @Override
  @Nullable
  public String getApplicationId() {
    return null;
  }

  @Override
  public Integer getVersionCode() {
    return null;
  }

  @Override
  @Nullable
  public String getVersionName() {
    return null;
  }

  @Override
  @Nullable
  public ApiVersion getMinSdkVersion() {
    return null;
  }

  @Override
  @Nullable
  public ApiVersion getTargetSdkVersion() {
    return null;
  }

  @Override
  @Nullable
  public Integer getMaxSdkVersion() {
    return null;
  }

  @Override
  @Nullable
  public Integer getRenderscriptTargetApi() {
    return null;
  }

  @Override
  @Nullable
  public Boolean getRenderscriptSupportModeEnabled() {
    return null;
  }

  @Override
  @Nullable
  public Boolean getRenderscriptSupportModeBlasEnabled() {
    return null;
  }

  @Override
  @Nullable
  public Boolean getRenderscriptNdkModeEnabled() {
    return null;
  }

  @Override
  @Nullable
  public String getTestApplicationId() {
    return null;
  }

  @Override
  @Nullable
  public String getTestInstrumentationRunner() {
    return null;
  }

  @Override
  @NotNull
  public Map<String, String> getTestInstrumentationRunnerArguments() {
    return Collections.emptyMap();
  }

  @Override
  @Nullable
  public Boolean getTestHandleProfiling() {
    return null;
  }

  @Override
  @Nullable
  public Boolean getTestFunctionalTest() {
    return null;
  }

  @Override
  @NotNull
  public Collection<String> getResourceConfigurations() {
    return Collections.emptyList();
  }

  @Override
  @Nullable
  public SigningConfig getSigningConfig() {
    return null;
  }

  @Override
  @NotNull
  public VectorDrawablesOptions getVectorDrawables() {
    return mock(VectorDrawablesOptions.class);
  }

  @Override
  @Nullable
  public Boolean getWearAppUnbundled() {
    return null;
  }

  @Override
  @NotNull
  public Map<String, Object> getManifestPlaceholders() {
    return Collections.emptyMap();
  }

  @Override
  @Nullable
  public Boolean getMultiDexEnabled() {
    return null;
  }

  @Override
  @Nullable
  public File getMultiDexKeepFile() {
    return null;
  }

  @Override
  @Nullable
  public File getMultiDexKeepProguard() {
    return null;
  }

  @Override
  @NotNull
  public Map<String, ClassField> getBuildConfigFields() {
    return Collections.emptyMap();
  }

  @Override
  @NotNull
  public Map<String, ClassField> getResValues() {
    return Collections.emptyMap();
  }

  @Override
  @NotNull
  public List<File> getProguardFiles() {
    return Collections.emptyList();
  }

  @Override
  @NotNull
  public List<File> getConsumerProguardFiles() {
    return Collections.emptyList();
  }

  @Override
  @NotNull
  public Collection<File> getTestProguardFiles() {
    return Collections.emptyList();
  }
}
