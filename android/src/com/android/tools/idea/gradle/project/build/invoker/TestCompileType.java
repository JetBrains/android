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
package com.android.tools.idea.gradle.project.build.invoker;

import com.android.tools.idea.gradle.project.model.ide.android.IdeAndroidArtifact;
import com.android.tools.idea.gradle.project.model.ide.android.IdeBaseArtifact;
import com.android.tools.idea.gradle.project.model.ide.android.IdeJavaArtifact;
import com.android.tools.idea.gradle.project.model.ide.android.IdeVariant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

import static org.jetbrains.android.util.AndroidCommonUtils.isInstrumentationTestConfiguration;
import static org.jetbrains.android.util.AndroidCommonUtils.isTestConfiguration;

public abstract class TestCompileType {
  public static final TestCompileType ALL = new TestCompileType() {
    @NotNull
    @Override
    public Collection<IdeBaseArtifact> getArtifacts(@NotNull IdeVariant variant) {
      return variant.getTestArtifacts();
    }
  };
  public static final TestCompileType ANDROID_TESTS = new TestCompileType() {
    @Override
    @NotNull
    public Collection<IdeBaseArtifact> getArtifacts(@NotNull IdeVariant variant) {
      IdeAndroidArtifact testArtifact = variant.getAndroidTestArtifact();
      return testArtifact != null ? Collections.singleton(testArtifact) : Collections.emptySet();
    }
  };
  public static final TestCompileType UNIT_TESTS = new TestCompileType() {
    @Override
    @NotNull
    public Collection<IdeBaseArtifact> getArtifacts(@NotNull IdeVariant variant) {
      IdeJavaArtifact testArtifact = variant.getUnitTestArtifact();
      return testArtifact != null ? Collections.singleton(testArtifact) : Collections.emptySet();
    }
  };
  public static final TestCompileType NONE = new TestCompileType() {
    @NotNull
    @Override
    public Collection<IdeBaseArtifact> getArtifacts(@NotNull IdeVariant variant) {
      return Collections.emptySet();
    }
  };

  @NotNull
  public abstract Collection<IdeBaseArtifact> getArtifacts(@NotNull IdeVariant variant);

  @NotNull
  public static TestCompileType get(@Nullable String runConfigurationId) {
    if (runConfigurationId != null) {
      if (isInstrumentationTestConfiguration(runConfigurationId)) {
        return ANDROID_TESTS;
      }
      if (isTestConfiguration(runConfigurationId)) {
        return UNIT_TESTS;
      }
    }
    return ALL;
  }
}
