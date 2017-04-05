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
import com.android.builder.model.BaseConfig;
import com.android.builder.model.ClassField;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.Serializable;
import java.util.*;

/**
 * Creates a deep copy of {@link BaseConfig}.
 *
 * @see IdeAndroidProject
 */
public class IdeBaseConfig implements BaseConfig, Serializable {
  @NotNull private final String myName;
  @NotNull private final Map<String,ClassField> myBuildConfigFields;
  @NotNull private final Map<String,ClassField> myResValues;
  @NotNull private final Collection<File> myProguardFiles;
  @NotNull private final Collection<File> myConsumerProguardFiles;
  @NotNull private final Collection<File> myTestProguardFiles;
  @NotNull private final Map<String,Object> myManifestPlaceholders;
  @NotNull private final List<File> myJarJarRuleFiles;
  @Nullable private final String myApplicationIdSuffix;
  @Nullable private final String myVersionNameSuffix;
  @Nullable private final Boolean myMultiDexEnabled;
  @Nullable private final File myMultiDexKeepFile;
  @Nullable private final File myMultiDexKeepProguard;

  public IdeBaseConfig(@NotNull BaseConfig config) {

    myName = config.getName();

    Map<String, ClassField> flBuildConfigFields = config.getBuildConfigFields();
    myBuildConfigFields = new HashMap<>(flBuildConfigFields.size());
    for (String e:flBuildConfigFields.keySet()) {
      myBuildConfigFields.put(e, new IdeClassField(flBuildConfigFields.get(e)));
    }

    Map<String, ClassField> flResValues = config.getResValues();
    myResValues = new HashMap<>(flResValues.size());
    for (String e:flResValues.keySet()) {
      myBuildConfigFields.put(e, new IdeClassField(flResValues.get(e)));
    }

    myProguardFiles = new ArrayList<>(config.getProguardFiles());
    myConsumerProguardFiles = new ArrayList<>(config.getConsumerProguardFiles());
    myTestProguardFiles = new ArrayList<>(config.getTestProguardFiles());
    myManifestPlaceholders = new HashMap<>(config.getManifestPlaceholders());
    myJarJarRuleFiles = new ArrayList<>(config.getJarJarRuleFiles());
    myApplicationIdSuffix = config.getApplicationIdSuffix();
    myVersionNameSuffix = config.getVersionNameSuffix();
    myMultiDexEnabled = config.getMultiDexEnabled();
    myMultiDexKeepFile = config.getMultiDexKeepFile();
    myMultiDexKeepProguard = config.getMultiDexKeepProguard();
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
