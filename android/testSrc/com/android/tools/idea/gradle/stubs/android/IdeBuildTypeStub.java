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

import com.android.annotations.Nullable;
import com.android.builder.model.BuildType;
import com.android.builder.model.SigningConfig;
import com.android.tools.idea.model.IdeSigningConfig;
import org.jetbrains.annotations.NotNull;

/**
 * Creates a version of {@link BuildType} that does not cause unsupported exceptions, used for testing {@link IdeAndroidProject}.
 *
 */
public class IdeBuildTypeStub extends IdeBaseConfigStub implements BuildType {

  @Nullable private final SigningConfig mySigningConfig = new IdeSigningConfig("typeSigningConfig", null, "storePassword",
                                                                               "keyAlias", "keyPassword", "storeType",
                                                                               true, true,  true);
  private final static boolean myDebuggable = true;
  private final static boolean myTestCoverageEnabled = true;
  private final static boolean myPseudoLocalesEnabled = true;
  private final static boolean myJniDebuggable = true;
  private final static boolean myRenderscriptDebuggable = true;
  private final static int myRenderscriptOptimLevel = 1;
  private final static boolean myMinifyEnabled = true;
  private final static boolean myZipAlignEnabled = true;
  private final static boolean myEmbedMicroApp = true;


  public IdeBuildTypeStub(@NotNull String name) {
    super(name);
  }

  @Nullable
  @Override
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
