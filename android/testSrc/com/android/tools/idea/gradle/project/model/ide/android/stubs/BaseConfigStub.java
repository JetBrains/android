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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class BaseConfigStub extends BaseStub implements BaseConfig {
  @NotNull private final String myName;
  @NotNull private final Map<String,ClassField> myBuildConfigFields;
  @NotNull private final Map<String, ClassField> myResValues;
  @NotNull private final Collection<File> myProguardFiles;
  @NotNull private final Collection<File> myConsumerProguardFiles;
  @NotNull private final Collection<File> myTestProguardFiles;
  @NotNull private final Map<String, Object> myManifestPlaceholders;
  @NotNull private final List<File> myJarJarRuleFiles;
  @Nullable private final String myApplicationIdSuffix;
  @Nullable private final String myVersionNameSuffix;
  @Nullable private final Boolean myMultiDexEnabled;
  @Nullable private final File myMultiDexKeepFile;
  @Nullable private final File myMultiDexKeepProguard;

  public BaseConfigStub() {
    this("name", ImmutableMap.of("buildConfigField", new ClassFieldStub()), ImmutableMap.of("resValue", new ClassFieldStub()),
         ImmutableMap.of("flavorSelection", "value"),
         Lists.newArrayList(new File("proguardFile")), Lists.newArrayList(new File("consumerProguardFile")),
         Lists.newArrayList(new File("testProguardFile")), ImmutableMap.of("key", "value"), Lists.newArrayList(new File("jarJarRuleFile")),
         "one", "two", true, new File("multiDexKeepFile"), new File("multiDexKeepProguard"));
  }

  public BaseConfigStub(@NotNull String name,
                        @NotNull Map<String, ClassField> buildConfigFields,
                        @NotNull Map<String, ClassField> resValues,
                        @NotNull Map<String, String> flavorSelections,
                        @NotNull Collection<File> proguardFiles,
                        @NotNull Collection<File> consumerProguardFiles,
                        @NotNull Collection<File> testProguardFiles,
                        @NotNull Map<String, Object> manifestPlaceholders,
                        @NotNull List<File> jarJarRuleFiles,
                        @Nullable String applicationIdSuffix,
                        @Nullable String versionNameSuffix,
                        @Nullable Boolean multiDexEnabled,
                        @Nullable File multiDexKeepFile,
                        @Nullable File multiDexKeepProguard) {
    myName = name;
    myBuildConfigFields = buildConfigFields;
    myResValues = resValues;
    myProguardFiles = proguardFiles;
    myConsumerProguardFiles = consumerProguardFiles;
    myTestProguardFiles = testProguardFiles;
    myManifestPlaceholders = manifestPlaceholders;
    myJarJarRuleFiles = jarJarRuleFiles;
    myApplicationIdSuffix = applicationIdSuffix;
    myVersionNameSuffix = versionNameSuffix;
    myMultiDexEnabled = multiDexEnabled;
    myMultiDexKeepFile = multiDexKeepFile;
    myMultiDexKeepProguard = multiDexKeepProguard;
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

  @Override
  public String toString() {
    return "BaseConfigStub{" +
           "myName='" + myName + '\'' +
           ", myResValues=" + myResValues +
           ", myProguardFiles=" + myProguardFiles +
           ", myConsumerProguardFiles=" + myConsumerProguardFiles +
           ", myManifestPlaceholders=" + myManifestPlaceholders +
           ", myApplicationIdSuffix='" + myApplicationIdSuffix + '\'' +
           ", myVersionNameSuffix='" + myVersionNameSuffix + '\'' +
           "}";
  }
}
