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

import com.android.annotations.NonNull;
import com.android.builder.model.BuildType;
import com.android.builder.model.ClassField;
import com.android.builder.model.NdkConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Map;

public class BuildTypeStub implements BuildType {
  @NotNull private final String myName;

  BuildTypeStub(@NotNull String name) {
    myName = name;
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  public boolean isDebuggable() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isTestCoverageEnabled() {
    return false;
  }

  @Override
  public boolean isJniDebugBuild() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isRenderscriptDebugBuild() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getRenderscriptOptimLevel() {
    throw new UnsupportedOperationException();
  }

  @Override
  @Nullable
  public String getPackageNameSuffix() {
    throw new UnsupportedOperationException();
  }

  @Override
  @Nullable
  public String getVersionNameSuffix() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isRunProguard() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isZipAlign() {
    throw new UnsupportedOperationException();
  }

  @Override
  @Nullable
  public NdkConfig getNdkConfig() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean getEmbedMicroApp() {
    throw new UnsupportedOperationException();
  }

  @Override
  @NotNull
  public Map<String, ClassField> getBuildConfigFields() {
    throw new UnsupportedOperationException();
  }

  @NonNull
  @Override
  public Map<String, ClassField> getResValues() {
    throw new UnsupportedOperationException();
  }

  @Override
  @NotNull
  public Collection<File> getProguardFiles() {
    throw new UnsupportedOperationException();
  }

  @Override
  @NotNull
  public Collection<File> getConsumerProguardFiles() {
    throw new UnsupportedOperationException();
  }
}
