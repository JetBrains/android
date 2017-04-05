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
import com.android.builder.model.BaseConfig;
import com.android.builder.model.ClassField;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Creates a version of {@link BaseConfig} with default values, used for testing {@link IdeAndroidProject}.
 *
 */
public class IdeBaseConfigStub implements BaseConfig {
  @NotNull private final String myName;
  @NotNull private final Map<String,ClassField> myBuildConfigFields = Collections.emptyMap();
  @NotNull private final static Map<String,Object> myManifestPlaceholders = Collections.emptyMap();
  @NotNull private final static List<File> myJarJarRuleFiles = Collections.emptyList();
  @NotNull private final static Map<String,ClassField> myResValues = Collections.emptyMap();
  @NotNull private final static List<File> myProguardFiles = Collections.emptyList();
  @NotNull private final static List<File> myConsumerProguardFiles = Collections.emptyList();
  @NotNull private final static Collection<File> myTestProguardFiles = Collections.emptyList();
  @Nullable private final static String myApplicationIdSuffix = "ApplicationIdSuffix";
  @Nullable private final static String myVersionNameSuffix = "VersionNameSuffix";
  @Nullable private final static Boolean myMultiDexEnabled = false;
  @Nullable private final static File myMultiDexKeepFile = null;
  @Nullable private final static File myMultiDexKeepProguard = null;

  public IdeBaseConfigStub(@NotNull String name) {
    myName = name;
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  @NotNull
  public Map<String, ClassField> getBuildConfigFields() {
    return myBuildConfigFields;
  }

  @Override
  @NotNull
  public Map<String, Object> getManifestPlaceholders() {
    return myManifestPlaceholders;
  }

  @Override
  @NotNull
  public List<File> getJarJarRuleFiles() {
    return myJarJarRuleFiles;
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
    return myTestProguardFiles;
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
    return myMultiDexEnabled;
  }

  @Override
  @Nullable
  public File getMultiDexKeepFile() {
    return myMultiDexKeepFile;
  }

  @Override
  @Nullable
  public File getMultiDexKeepProguard() {
    return myMultiDexKeepProguard;
  }
}
