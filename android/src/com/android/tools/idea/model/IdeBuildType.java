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
import com.android.builder.model.BuildType;
import com.android.builder.model.SigningConfig;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

/**
 * Creates a deep copy of {@link BuildType}.
 *
 * @see IdeAndroidProject
 */
public class IdeBuildType extends IdeBaseConfig implements BuildType, Serializable {
  @Nullable private final SigningConfig mySigningConfig;
  private final boolean myDebuggable;
  private final boolean myTestCoverageEnabled;
  private final boolean myPseudoLocalesEnabled;
  private final boolean myJniDebuggable;
  private final boolean myRenderscriptDebuggable;
  private final int myRenderscriptOptimLevel;
  private final boolean myMinifyEnabled;
  private final boolean myZipAlignEnabled;
  private final boolean myEmbedMicroApp;

  public IdeBuildType(@NotNull BuildType type) {
    super(type);

    SigningConfig tySigningConfig = type.getSigningConfig();
    mySigningConfig = tySigningConfig == null ? null : new IdeSigningConfig(tySigningConfig);

    myDebuggable = type.isDebuggable();
    myTestCoverageEnabled = type.isTestCoverageEnabled();
    myPseudoLocalesEnabled = type.isPseudoLocalesEnabled();
    myJniDebuggable = type.isJniDebuggable();
    myRenderscriptDebuggable = type.isRenderscriptDebuggable();
    myRenderscriptOptimLevel = type.getRenderscriptOptimLevel();
    myMinifyEnabled = type.isMinifyEnabled();
    myZipAlignEnabled = type.isZipAlignEnabled();
    myEmbedMicroApp = type.isEmbedMicroApp();
  }

  @Override
  @Nullable
  public SigningConfig getSigningConfig() {
    return mySigningConfig;
  }

  @Override
  public boolean isDebuggable() {
    return myDebuggable;
  }

  @Override
  public boolean isTestCoverageEnabled() {
    return myTestCoverageEnabled;
  }

  @Override
  public boolean isPseudoLocalesEnabled() {
    return myPseudoLocalesEnabled;
  }

  @Override
  public boolean isJniDebuggable() {
    return myJniDebuggable;
  }

  @Override
  public boolean isRenderscriptDebuggable() {
    return myRenderscriptDebuggable;
  }

  @Override
  public int getRenderscriptOptimLevel() {
    return myRenderscriptOptimLevel;
  }

  @Override
  public boolean isMinifyEnabled() {
    return myMinifyEnabled;
  }

  @Override
  public boolean isZipAlignEnabled() {
    return myZipAlignEnabled;
  }

  @Override
  public boolean isEmbedMicroApp() {
    return myEmbedMicroApp;
  }
}
