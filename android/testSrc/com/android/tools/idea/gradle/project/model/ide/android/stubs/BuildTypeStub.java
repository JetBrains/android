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
package com.android.tools.idea.gradle.project.model.ide.android.stubs;

import com.android.builder.model.BuildType;
import com.android.builder.model.SigningConfig;
import com.android.tools.idea.gradle.project.model.ide.android.UnusedModelMethodException;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class BuildTypeStub extends BaseConfigStub implements BuildType {
  private final boolean myDebuggable;
  private final boolean myJniDebuggable;
  private final boolean myRenderscriptDebuggable;
  private final int myRenderscriptOptimLevel;
  private final boolean myMinifyEnabled;
  private final boolean myZipAlignEnabled;

  public BuildTypeStub() {
    this(true, true, true, 1, true, true);
  }

  public BuildTypeStub(boolean debuggable,
                       boolean jniDebuggable,
                       boolean renderscriptDebuggable,
                       int level,
                       boolean minifyEnabled,
                       boolean zipAlignEnabled) {
    myDebuggable = debuggable;
    myJniDebuggable = jniDebuggable;
    myRenderscriptDebuggable = renderscriptDebuggable;
    myRenderscriptOptimLevel = level;
    myMinifyEnabled = minifyEnabled;
    myZipAlignEnabled = zipAlignEnabled;
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
    if (!(o instanceof BuildType)) {
      return false;
    }
    BuildType that = (BuildType)o;
    //noinspection deprecation
    return Objects.equals(getName(), that.getName()) &&
           Objects.equals(getResValues(), that.getResValues()) &&
           Objects.equals(getProguardFiles(), that.getProguardFiles()) &&
           Objects.equals(getConsumerProguardFiles(), that.getConsumerProguardFiles()) &&
           Objects.equals(getManifestPlaceholders(), that.getManifestPlaceholders()) &&
           Objects.equals(getApplicationIdSuffix(), that.getApplicationIdSuffix()) &&
           Objects.equals(getVersionNameSuffix(), that.getVersionNameSuffix()) &&
           isDebuggable() == that.isDebuggable() &&
           isJniDebuggable() == that.isJniDebuggable() &&
           isRenderscriptDebuggable() == that.isRenderscriptDebuggable() &&
           getRenderscriptOptimLevel() == that.getRenderscriptOptimLevel() &&
           isMinifyEnabled() == that.isMinifyEnabled() &&
           isZipAlignEnabled() == that.isZipAlignEnabled();
  }

  @Override
  public int hashCode() {
    return Objects.hash(getName(), getResValues(), getProguardFiles(), getConsumerProguardFiles(), getManifestPlaceholders(),
                        getApplicationIdSuffix(), getVersionNameSuffix(), isDebuggable(), isJniDebuggable(), isRenderscriptDebuggable(),
                        getRenderscriptOptimLevel(), isMinifyEnabled(), isZipAlignEnabled());
  }

  @Override
  public String toString() {
    return "BuildTypeStub{" +
           "myDebuggable=" + myDebuggable +
           ", myJniDebuggable=" + myJniDebuggable +
           ", myRenderscriptDebuggable=" + myRenderscriptDebuggable +
           ", myRenderscriptOptimLevel=" + myRenderscriptOptimLevel +
           ", myMinifyEnabled=" + myMinifyEnabled +
           ", myZipAlignEnabled=" + myZipAlignEnabled +
           "} " + super.toString();
  }
}
