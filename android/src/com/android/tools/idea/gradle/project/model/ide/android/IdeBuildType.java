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
package com.android.tools.idea.gradle.project.model.ide.android;

import com.android.builder.model.BuildType;
import com.android.builder.model.SigningConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Creates a deep copy of a {@link BuildType}.
 */
public final class IdeBuildType extends IdeBaseConfig implements BuildType {
  // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
  private static final long serialVersionUID = 1L;

  private final boolean myDebuggable;
  private final boolean myJniDebuggable;
  private final boolean myRenderscriptDebuggable;
  private final int myRenderscriptOptimLevel;
  private final boolean myMinifyEnabled;
  private final boolean myZipAlignEnabled;
  private final int myHashCode;

  public IdeBuildType(@NotNull BuildType buildType, @NotNull ModelCache modelCache) {
    super(buildType, modelCache);
    myDebuggable = buildType.isDebuggable();
    myJniDebuggable = buildType.isJniDebuggable();
    myRenderscriptDebuggable = buildType.isRenderscriptDebuggable();
    myRenderscriptOptimLevel = buildType.getRenderscriptOptimLevel();
    //noinspection deprecation
    myMinifyEnabled = buildType.isMinifyEnabled();
    myZipAlignEnabled = buildType.isZipAlignEnabled();

    myHashCode = calculateHashCode();
  }

  @Override
  @Nullable
  public SigningConfig getSigningConfig() {
    throw new UnusedModelMethodException("getSigningConfig");
  }

  @Override
  public boolean isDebuggable() {
    return myDebuggable;
  }

  @Override
  public boolean isTestCoverageEnabled() {
    throw new UnusedModelMethodException("isTestCoverageEnabled");
  }

  @Override
  public boolean isPseudoLocalesEnabled() {
    throw new UnusedModelMethodException("isPseudoLocalesEnabled");
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
    throw new UnusedModelMethodException("isEmbedMicroApp");
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof IdeBuildType)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    IdeBuildType type = (IdeBuildType)o;
    return type.canEqual(this) &&
           myDebuggable == type.myDebuggable &&
           myJniDebuggable == type.myJniDebuggable &&
           myRenderscriptDebuggable == type.myRenderscriptDebuggable &&
           myRenderscriptOptimLevel == type.myRenderscriptOptimLevel &&
           myMinifyEnabled == type.myMinifyEnabled &&
           myZipAlignEnabled == type.myZipAlignEnabled;
  }

  @Override
  public boolean canEqual(Object other) {
    return other instanceof IdeBuildType;
  }

  @Override
  public int hashCode() {
    return myHashCode;
  }

  @Override
  protected int calculateHashCode() {
    return Objects.hash(super.calculateHashCode(), myDebuggable, myJniDebuggable, myRenderscriptDebuggable, myRenderscriptOptimLevel,
                        myMinifyEnabled, myZipAlignEnabled);
  }

  @Override
  public String toString() {
    return "IdeBuildType{" +
           super.toString() +
           ", myDebuggable=" + myDebuggable +
           ", myJniDebuggable=" + myJniDebuggable +
           ", myRenderscriptDebuggable=" + myRenderscriptDebuggable +
           ", myRenderscriptOptimLevel=" + myRenderscriptOptimLevel +
           ", myMinifyEnabled=" + myMinifyEnabled +
           ", myZipAlignEnabled=" + myZipAlignEnabled +
           "}";
  }
}
