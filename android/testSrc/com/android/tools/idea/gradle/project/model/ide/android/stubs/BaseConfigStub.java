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

import com.android.builder.model.BaseConfig;
import com.android.builder.model.ClassField;
import com.android.tools.idea.gradle.project.model.ide.android.UnusedModelMethodException;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

/**
 * Creates a deep copy of a {@link BaseConfig}.
 */
public class BaseConfigStub implements BaseConfig {
  @NotNull private final String myName;
  @NotNull private final Map<String, ClassField> myResValues;
  @NotNull private final Collection<File> myProguardFiles;
  @NotNull private final Collection<File> myConsumerProguardFiles;
  @NotNull private final Map<String, Object> myManifestPlaceholders;
  @Nullable private final String myApplicationIdSuffix;
  @Nullable private final String myVersionNameSuffix;

  public BaseConfigStub() {
    this("name", ImmutableMap.of("name", new ClassFieldStub()), Lists.newArrayList(new File("proguardFile")),
         Lists.newArrayList(new File("consumerProguardFile")), ImmutableMap.of("key", "value"), "one", "two");
  }

  public BaseConfigStub(@NotNull String name,
                        @NotNull Map<String, ClassField> values,
                        @NotNull Collection<File> proguardFiles,
                        @NotNull Collection<File> consumerProguardFiles,
                        @NotNull Map<String, Object> placeholders,
                        @Nullable String applicationIdSuffix,
                        @Nullable String versionNameSuffix) {
    myName = name;
    myResValues = values;
    myProguardFiles = proguardFiles;
    myConsumerProguardFiles = consumerProguardFiles;
    myManifestPlaceholders = placeholders;
    myApplicationIdSuffix = applicationIdSuffix;
    myVersionNameSuffix = versionNameSuffix;
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  @NotNull
  public Map<String, ClassField> getBuildConfigFields() {
    throw new UnusedModelMethodException("getBuildConfigFields");
  }

  @Override
  @NotNull
  public Map<String, ClassField> getResValues() {
    return myResValues;
  }

  @Override
  @NotNull
  public Collection<File> getProguardFiles() {
    return myProguardFiles;
  }

  @Override
  @NotNull
  public Collection<File> getConsumerProguardFiles() {
    return myConsumerProguardFiles;
  }

  @Override
  @NotNull
  public Collection<File> getTestProguardFiles() {
    throw new UnusedModelMethodException("getTestProguardFiles");
  }

  @Override
  @NotNull
  public Map<String, Object> getManifestPlaceholders() {
    return myManifestPlaceholders;
  }

  @Override
  @NotNull
  public List<File> getJarJarRuleFiles() {
    throw new UnusedModelMethodException("getJarJarRuleFiles");
  }

  @Override
  @Nullable
  public String getApplicationIdSuffix() {
    return myApplicationIdSuffix;
  }

  @Override
  @Nullable
  public String getVersionNameSuffix() {
    return myVersionNameSuffix;
  }

  @Override
  @Nullable
  public Boolean getMultiDexEnabled() {
    throw new UnusedModelMethodException("getMultiDexEnabled");
  }

  @Override
  @Nullable
  public File getMultiDexKeepFile() {
    throw new UnusedModelMethodException("getMultiDexKeepFile");
  }

  @Override
  @Nullable
  public File getMultiDexKeepProguard() {
    throw new UnusedModelMethodException("getMultiDexKeepProguard");
  }
}
