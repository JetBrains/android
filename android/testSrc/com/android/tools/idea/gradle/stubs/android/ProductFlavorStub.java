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
import java.util.*;

public class ProductFlavorStub implements ProductFlavor {
  @NotNull private final String myName;

  /**
   * Creates a new {@code ProductFlavorStub}.
   *
   * @param name the name of the product flavor.
   */
  ProductFlavorStub(@NotNull String name) {
    this.myName = name;
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
    throw new UnsupportedOperationException();
  }

  @Override
  @Nullable
  public String getDimension() {
    throw new UnsupportedOperationException();
  }

  @Override
  @Nullable
  public String getApplicationId() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Integer getVersionCode() {
    throw new UnsupportedOperationException();
  }

  @Override
  @Nullable
  public String getVersionName() {
    throw new UnsupportedOperationException();
  }

  @Override
  public ApiVersion getMinSdkVersion() {
    throw new UnsupportedOperationException();
  }

  @Override
  public ApiVersion getTargetSdkVersion() {
    throw new UnsupportedOperationException();
  }

  @Override
  @Nullable
  public Integer getMaxSdkVersion() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Integer getRenderscriptTargetApi() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Boolean getRenderscriptSupportModeEnabled() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Boolean getRenderscriptNdkModeEnabled() {
    throw new UnsupportedOperationException();
  }

  @Override
  @Nullable
  public String getTestApplicationId() {
    throw new UnsupportedOperationException();
  }

  @Override
  @Nullable
  public String getTestInstrumentationRunner() {
    throw new UnsupportedOperationException();
  }

  @Override
  @NotNull
  public Map<String, String> getTestInstrumentationRunnerArguments() {
    throw new UnsupportedOperationException();
  }

  @Override
  @Nullable
  public Boolean getTestHandleProfiling() {
    throw new UnsupportedOperationException();
  }

  @Override
  @Nullable
  public Boolean getTestFunctionalTest() {
    throw new UnsupportedOperationException();
  }

  @Override
  @NotNull
  public Collection<String> getResourceConfigurations() {
    throw new UnsupportedOperationException();
  }

  @Override
  @Nullable
  public SigningConfig getSigningConfig() {
    throw new UnsupportedOperationException();
  }

  @Override
  @NotNull
  public VectorDrawablesOptions getVectorDrawables() {
    throw new UnsupportedOperationException();
  }

  @Override
  @NotNull
  public Map<String, Object> getManifestPlaceholders() {
    throw new UnsupportedOperationException();
  }

  @Override
  @Nullable
  public Boolean getMultiDexEnabled() {
    throw new UnsupportedOperationException();
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
  public List<File> getJarJarRuleFiles() {
    return Collections.emptyList();
  }

  @Override
  @NotNull
  public Map<String, ClassField> getBuildConfigFields() {
    throw new UnsupportedOperationException();
  }

  @Override
  @NotNull
  public Map<String, ClassField> getResValues() {
    return Collections.emptyMap();
  }

  @Override
  @NotNull
  public List<File> getProguardFiles() {
    throw new UnsupportedOperationException();
  }

  @Override
  @NotNull
  public List<File> getConsumerProguardFiles() {
    throw new UnsupportedOperationException();
  }

  @Override
  @NotNull
  public Collection<File> getTestProguardFiles() {
    throw new UnsupportedOperationException();
  }
}
