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

import com.android.builder.model.ProductFlavor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

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

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @Nullable
  @Override
  public String getPackageName() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getVersionCode() {
    throw new UnsupportedOperationException();
  }

  @Nullable
  @Override
  public String getVersionName() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getMinSdkVersion() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getTargetSdkVersion() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getRenderscriptTargetApi() {
    throw new UnsupportedOperationException();
  }

  @Nullable
  @Override
  public String getTestPackageName() {
    throw new UnsupportedOperationException();
  }

  @Nullable
  @Override
  public String getTestInstrumentationRunner() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public List<String> getBuildConfig() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public List<File> getProguardFiles() {
    throw new UnsupportedOperationException();
  }
}
