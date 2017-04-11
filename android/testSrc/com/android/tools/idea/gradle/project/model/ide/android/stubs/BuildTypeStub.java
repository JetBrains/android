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
import com.android.builder.model.ClassField;
import com.android.builder.model.SigningConfig;
import com.android.tools.idea.gradle.project.model.ide.android.UnusedModelMethodException;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Map;
import java.util.Objects;

public final class BuildTypeStub extends BaseConfigStub implements BuildType {
  private final boolean myDebuggable;
  private final boolean myJniDebuggable;
  private final boolean myRenderscriptDebuggable;
  private final int myRenderscriptOptimLevel;
  private final boolean myMinifyEnabled;
  private final boolean myZipAlignEnabled;

  public BuildTypeStub() {
    this("name", ImmutableMap.of("name", new ClassFieldStub()), new File("proguardFile"), new File("consumerProguardFile"),
         ImmutableMap.of("key", "value"), "one", "two", true, true, true, 1, true, true);
  }

  public BuildTypeStub(@NotNull String name,
                       @NotNull Map<String, ClassField> values,
                       @NotNull File proguardFile,
                       @NotNull File consumerProguardFile,
                       @NotNull Map<String, Object> placeholders,
                       @Nullable String applicationIdSuffix,
                       @Nullable String versionNameSuffix,
                       boolean debuggable,
                       boolean jniDebuggable,
                       boolean renderscriptDebuggable,
                       int level,
                       boolean minifyEnabled,
                       boolean zipAlignEnabled) {
    super(name, values, Lists.newArrayList(proguardFile), Lists.newArrayList(consumerProguardFile), placeholders, applicationIdSuffix,
          versionNameSuffix);
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
    return isDebuggable() == that.isDebuggable() &&
           isJniDebuggable() == that.isJniDebuggable() &&
           isRenderscriptDebuggable() == that.isRenderscriptDebuggable() &&
           getRenderscriptOptimLevel() == that.getRenderscriptOptimLevel() &&
           isMinifyEnabled() == that.isMinifyEnabled() &&
           isZipAlignEnabled() == that.isZipAlignEnabled();
  }

  @Override
  public int hashCode() {
    return Objects.hash(isDebuggable(), isJniDebuggable(), isRenderscriptDebuggable(), getRenderscriptOptimLevel(), isMinifyEnabled(),
                        isZipAlignEnabled());
  }
}
