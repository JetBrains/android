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
package com.android.tools.idea.model.ide;

import com.android.annotations.Nullable;
import com.android.builder.model.BuildType;
import com.android.builder.model.ClassField;
import com.android.builder.model.SigningConfig;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.Serializable;
import java.util.*;

/**
 * Creates a deep copy of {@link BuildType}.
 *
 * @see IdeAndroidProject
 */
final public class IdeBuildType implements BuildType, Serializable {
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
    myName = type.getName();

    Map<String, ClassField> flBuildConfigFields = type.getBuildConfigFields();
    myBuildConfigFields = new HashMap<>(flBuildConfigFields.size());
    for (String e:flBuildConfigFields.keySet()) {
      myBuildConfigFields.put(e, new IdeClassField(flBuildConfigFields.get(e)));
    }

    Map<String, ClassField> flResValues = type.getResValues();
    myResValues = new HashMap<>(flResValues.size());
    for (String e:flResValues.keySet()) {
      myBuildConfigFields.put(e, new IdeClassField(flResValues.get(e)));
    }

    myProguardFiles = new ArrayList<>(type.getProguardFiles());
    myConsumerProguardFiles = new ArrayList<>(type.getConsumerProguardFiles());
    myTestProguardFiles = new ArrayList<>(type.getTestProguardFiles());
    myManifestPlaceholders = new HashMap<>(type.getManifestPlaceholders());
    myJarJarRuleFiles = new ArrayList<>(type.getJarJarRuleFiles());
    myApplicationIdSuffix = type.getApplicationIdSuffix();
    myVersionNameSuffix = type.getVersionNameSuffix();
    myMultiDexEnabled = type.getMultiDexEnabled();
    myMultiDexKeepFile = type.getMultiDexKeepFile();
    myMultiDexKeepProguard = type.getMultiDexKeepProguard();

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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof BuildType)) return false;
    BuildType type = (BuildType)o;
    return Objects.equals(getName(), type.getName()) &&
           Objects.equals(getApplicationIdSuffix(), type.getApplicationIdSuffix()) &&
           Objects.equals(getVersionNameSuffix(), type.getVersionNameSuffix()) &&
           Objects.equals(getBuildConfigFields(), type.getBuildConfigFields()) &&
           Objects.equals(getResValues(), type.getResValues()) &&
           Objects.equals(getProguardFiles(), type.getProguardFiles()) &&
           Objects.equals(getConsumerProguardFiles(), type.getConsumerProguardFiles()) &&
           Objects.equals(getTestProguardFiles(), type.getTestProguardFiles()) &&
           Objects.equals(getManifestPlaceholders(), type.getManifestPlaceholders()) &&
           Objects.equals(getMultiDexEnabled(), type.getMultiDexEnabled()) &&
           Objects.equals(getMultiDexKeepFile(), type.getMultiDexKeepFile()) &&
           Objects.equals(getMultiDexKeepProguard(), type.getMultiDexKeepProguard()) &&
           Objects.equals(getJarJarRuleFiles(), type.getJarJarRuleFiles()) &&
           isDebuggable() == type.isDebuggable() &&
           isTestCoverageEnabled() == type.isTestCoverageEnabled() &&
           isPseudoLocalesEnabled() == type.isPseudoLocalesEnabled() &&
           isJniDebuggable() == type.isJniDebuggable() &&
           isRenderscriptDebuggable() == type.isRenderscriptDebuggable() &&
           getRenderscriptOptimLevel() == type.getRenderscriptOptimLevel() &&
           isMinifyEnabled() == type.isMinifyEnabled() &&
           isZipAlignEnabled() == type.isZipAlignEnabled() &&
           isEmbedMicroApp() == type.isEmbedMicroApp() &&
           Objects.equals(getSigningConfig(), type.getSigningConfig());
  }

  @Override
  public int hashCode() {
    return Objects
      .hash(getName(), getBuildConfigFields(), getResValues(), getProguardFiles(), getConsumerProguardFiles(), getTestProguardFiles(),
            getManifestPlaceholders(), getJarJarRuleFiles(), getApplicationIdSuffix(), getVersionNameSuffix(), getMultiDexEnabled(),
            getMultiDexKeepFile(), getMultiDexKeepProguard(), getSigningConfig(), isDebuggable(), isTestCoverageEnabled(),
            isPseudoLocalesEnabled(), isJniDebuggable(), isRenderscriptDebuggable(), getRenderscriptOptimLevel(), isMinifyEnabled(),
            isZipAlignEnabled(), isEmbedMicroApp());
  }
}
