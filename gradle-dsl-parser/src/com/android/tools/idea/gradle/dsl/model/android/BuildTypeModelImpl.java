/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model.android;

import com.android.tools.idea.gradle.dsl.api.android.BuildTypeModel;
import com.android.tools.idea.gradle.dsl.api.values.GradleNotNullValue;
import com.android.tools.idea.gradle.dsl.api.values.GradleNullableValue;
import com.android.tools.idea.gradle.dsl.model.values.GradleNotNullValueImpl;
import com.android.tools.idea.gradle.dsl.parser.android.BuildTypeDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionList;
import com.android.utils.Pair;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class BuildTypeModelImpl extends FlavorTypeModelImpl implements BuildTypeModel {
  @NonNls private static final String APPLICATION_ID_SUFFIX = "applicationIdSuffix";
  @NonNls private static final String BUILD_CONFIG_FIELD = "buildConfigField";
  @NonNls private static final String DEBUGGABLE = "debuggable";
  @NonNls private static final String EMBED_MICRO_APP = "embedMicroApp";
  @NonNls private static final String JNI_DEBUGGABLE = "jniDebuggable";
  @NonNls private static final String MINIFY_ENABLED = "minifyEnabled";
  @NonNls private static final String PSEUDO_LOCALES_ENABLED = "pseudoLocalesEnabled";
  @NonNls private static final String RENDERSCRIPT_DEBUGGABLE = "renderscriptDebuggable";
  @NonNls private static final String RENDERSCRIPT_OPTIM_LEVEL = "renderscriptOptimLevel";
  @NonNls private static final String SHRINK_RESOURCES = "shrinkResources";
  @NonNls private static final String TEST_COVERAGE_ENABLED = "testCoverageEnabled";
  @NonNls private static final String VERSION_NAME_SUFFIX = "versionNameSuffix";
  @NonNls private static final String ZIP_ALIGN_ENABLED = "zipAlignEnabled";

  public BuildTypeModelImpl(@NotNull BuildTypeDslElement dslElement) {
    super(dslElement);
  }

  @Override
  @NotNull
  public GradleNullableValue<String> applicationIdSuffix() {
    return myDslElement.getLiteralProperty(APPLICATION_ID_SUFFIX, String.class);
  }

  @Override
  @NotNull
  public BuildTypeModel setApplicationIdSuffix(@NotNull String applicationIdSuffix) {
    myDslElement.setNewLiteral(APPLICATION_ID_SUFFIX, applicationIdSuffix);
    return this;
  }

  @Override
  @NotNull
  public BuildTypeModel removeApplicationIdSuffix() {
    myDslElement.removeProperty(APPLICATION_ID_SUFFIX);
    return this;
  }

  @Override
  @Nullable
  public List<GradleNotNullValue<BuildConfigField>> buildConfigFields() {
    List<Pair<GradleDslExpressionList, TypeNameValueElement>> typeNameValueElements = getTypeNameValueElements(BUILD_CONFIG_FIELD);
    if (typeNameValueElements == null) {
      return null;
    }

    List<GradleNotNullValue<BuildConfigField>> buildConfigFields = Lists.newArrayListWithCapacity(typeNameValueElements.size());
    for (Pair<GradleDslExpressionList, TypeNameValueElement> pair : typeNameValueElements) {
      GradleDslExpressionList listElement = pair.getFirst();
      TypeNameValueElement typeNameValueElement = pair.getSecond();
      buildConfigFields.add(new GradleNotNullValueImpl<>(listElement,
                                                         new BuildConfigFieldImpl(typeNameValueElement.type(), typeNameValueElement.name(),
                                                                                  typeNameValueElement.value())));
    }

    return buildConfigFields;
  }

  @Override
  @NotNull
  public BuildTypeModel addBuildConfigField(@NotNull BuildConfigField buildConfigField) {
    return (BuildTypeModelImpl)addTypeNameValueElement(buildConfigField);
  }

  @Override
  @NotNull
  public BuildTypeModel removeBuildConfigField(@NotNull BuildConfigField buildConfigField) {
    return (BuildTypeModelImpl)removeTypeNameValueElement(buildConfigField);
  }

  @Override
  @NotNull
  public BuildTypeModel removeAllBuildConfigFields() {
    myDslElement.removeProperty(BUILD_CONFIG_FIELD);
    return this;
  }

  @Override
  @NotNull
  public BuildTypeModel replaceBuildConfigField(@NotNull BuildConfigField oldBuildConfigField,
                                                @NotNull BuildConfigField newBuildConfigField) {
    return (BuildTypeModelImpl)replaceTypeNameValueElement(oldBuildConfigField, newBuildConfigField);
  }

  @Override
  @NotNull
  public GradleNullableValue<Boolean> debuggable() {
    return myDslElement.getLiteralProperty(DEBUGGABLE, Boolean.class);
  }

  @Override
  @NotNull
  public BuildTypeModel setDebuggable(@NotNull Boolean debuggable) {
    myDslElement.setNewLiteral(DEBUGGABLE, debuggable);
    return this;
  }

  @Override
  @NotNull
  public BuildTypeModel removeDebuggable() {
    myDslElement.removeProperty(DEBUGGABLE);
    return this;
  }

  @Override
  @NotNull
  public GradleNullableValue<Boolean> embedMicroApp() {
    return myDslElement.getLiteralProperty(EMBED_MICRO_APP, Boolean.class);
  }

  @Override
  @NotNull
  public BuildTypeModel setEmbedMicroApp(@NotNull Boolean embedMicroApp) {
    myDslElement.setNewLiteral(EMBED_MICRO_APP, embedMicroApp);
    return this;
  }

  @Override
  @NotNull
  public BuildTypeModel removeEmbedMicroApp() {
    myDslElement.removeProperty(EMBED_MICRO_APP);
    return this;
  }

  @Override
  @NotNull
  public GradleNullableValue<Boolean> jniDebuggable() {
    return myDslElement.getLiteralProperty(JNI_DEBUGGABLE, Boolean.class);
  }

  @Override
  @NotNull
  public BuildTypeModel setJniDebuggable(@NotNull Boolean jniDebuggable) {
    myDslElement.setNewLiteral(JNI_DEBUGGABLE, jniDebuggable);
    return this;
  }

  @Override
  @NotNull
  public BuildTypeModel removeJniDebuggable() {
    myDslElement.removeProperty(JNI_DEBUGGABLE);
    return this;
  }

  @Override
  @NotNull
  public GradleNullableValue<Boolean> minifyEnabled() {
    return myDslElement.getLiteralProperty(MINIFY_ENABLED, Boolean.class);
  }

  @Override
  @NotNull
  public BuildTypeModel setMinifyEnabled(@NotNull Boolean minifyEnabled) {
    myDslElement.setNewLiteral(MINIFY_ENABLED, minifyEnabled);
    return this;
  }

  @Override
  @NotNull
  public BuildTypeModel removeMinifyEnabled() {
    myDslElement.removeProperty(MINIFY_ENABLED);
    return this;
  }

  @Override
  @NotNull
  public GradleNullableValue<Boolean> pseudoLocalesEnabled() {
    return myDslElement.getLiteralProperty(PSEUDO_LOCALES_ENABLED, Boolean.class);
  }

  @Override
  @NotNull
  public BuildTypeModel setPseudoLocalesEnabled(@NotNull Boolean pseudoLocalesEnabled) {
    myDslElement.setNewLiteral(PSEUDO_LOCALES_ENABLED, pseudoLocalesEnabled);
    return this;
  }

  @Override
  @NotNull
  public BuildTypeModel removePseudoLocalesEnabled() {
    myDslElement.removeProperty(PSEUDO_LOCALES_ENABLED);
    return this;
  }

  @Override
  @NotNull
  public GradleNullableValue<Boolean> renderscriptDebuggable() {
    return myDslElement.getLiteralProperty(RENDERSCRIPT_DEBUGGABLE, Boolean.class);
  }

  @Override
  @NotNull
  public BuildTypeModel setRenderscriptDebuggable(@NotNull Boolean renderscriptDebuggable) {
    myDslElement.setNewLiteral(RENDERSCRIPT_DEBUGGABLE, renderscriptDebuggable);
    return this;
  }

  @Override
  @NotNull
  public BuildTypeModel removeRenderscriptDebuggable() {
    myDslElement.removeProperty(RENDERSCRIPT_DEBUGGABLE);
    return this;
  }

  @Override
  @NotNull
  public GradleNullableValue<Integer> renderscriptOptimLevel() {
    return myDslElement.getLiteralProperty(RENDERSCRIPT_OPTIM_LEVEL, Integer.class);
  }

  @Override
  @NotNull
  public BuildTypeModel setRenderscriptOptimLevel(@NotNull Integer renderscriptOptimLevel) {
    myDslElement.setNewLiteral(RENDERSCRIPT_OPTIM_LEVEL, renderscriptOptimLevel);
    return this;
  }

  @Override
  @NotNull
  public BuildTypeModel removeRenderscriptOptimLevel() {
    myDslElement.removeProperty(RENDERSCRIPT_OPTIM_LEVEL);
    return this;
  }

  @Override
  @NotNull
  public GradleNullableValue<Boolean> shrinkResources() {
    return myDslElement.getLiteralProperty(SHRINK_RESOURCES, Boolean.class);
  }

  @Override
  @NotNull
  public BuildTypeModel setShrinkResources(@NotNull Boolean shrinkResources) {
    myDslElement.setNewLiteral(SHRINK_RESOURCES, shrinkResources);
    return this;
  }

  @Override
  @NotNull
  public BuildTypeModel removeShrinkResources() {
    myDslElement.removeProperty(SHRINK_RESOURCES);
    return this;
  }

  @Override
  @NotNull
  public GradleNullableValue<Boolean> testCoverageEnabled() {
    return myDslElement.getLiteralProperty(TEST_COVERAGE_ENABLED, Boolean.class);
  }

  @Override
  @NotNull
  public BuildTypeModel setTestCoverageEnabled(@NotNull Boolean testCoverageEnabled) {
    myDslElement.setNewLiteral(TEST_COVERAGE_ENABLED, testCoverageEnabled);
    return this;
  }

  @Override
  @NotNull
  public BuildTypeModel removeTestCoverageEnabled() {
    myDslElement.removeProperty(TEST_COVERAGE_ENABLED);
    return this;
  }

  @Override
  @NotNull
  public GradleNullableValue<String> versionNameSuffix() {
    return myDslElement.getLiteralProperty(VERSION_NAME_SUFFIX, String.class);
  }

  @Override
  @NotNull
  public BuildTypeModel setVersionNameSuffix(@NotNull String versionNameSuffix) {
    myDslElement.setNewLiteral(VERSION_NAME_SUFFIX, versionNameSuffix);
    return this;
  }

  @Override
  @NotNull
  public BuildTypeModel removeVersionNameSuffix() {
    myDslElement.removeProperty(VERSION_NAME_SUFFIX);
    return this;
  }

  @Override
  @NotNull
  public GradleNullableValue<Boolean> zipAlignEnabled() {
    return myDslElement.getLiteralProperty(ZIP_ALIGN_ENABLED, Boolean.class);
  }

  @Override
  @NotNull
  public BuildTypeModel setZipAlignEnabled(@NotNull Boolean zipAlignEnabled) {
    myDslElement.setNewLiteral(ZIP_ALIGN_ENABLED, zipAlignEnabled);
    return this;
  }

  @Override
  @NotNull
  public BuildTypeModel removeZipAlignEnabled() {
    myDslElement.removeProperty(ZIP_ALIGN_ENABLED);
    return this;
  }

  // Overriding the super class method to make them chainable along with the other methods in this class.

  @Override
  @NotNull
  public BuildTypeModel addConsumerProguardFile(@NotNull String consumerProguardFile) {
    return (BuildTypeModelImpl)super.addConsumerProguardFile(consumerProguardFile);
  }

  @Override
  @NotNull
  public BuildTypeModel removeConsumerProguardFile(@NotNull String consumerProguardFile) {
    return (BuildTypeModelImpl)super.removeConsumerProguardFile(consumerProguardFile);
  }

  @Override
  @NotNull
  public BuildTypeModel removeAllConsumerProguardFiles() {
    return (BuildTypeModelImpl)super.removeAllConsumerProguardFiles();
  }

  @Override
  @NotNull
  public BuildTypeModel replaceConsumerProguardFile(@NotNull String oldConsumerProguardFile, @NotNull String newConsumerProguardFile) {
    return (BuildTypeModelImpl)super.replaceConsumerProguardFile(oldConsumerProguardFile, newConsumerProguardFile);
  }

  @Override
  @NotNull
  public BuildTypeModel setManifestPlaceholder(@NotNull String name, @NotNull String value) {
    return (BuildTypeModelImpl)super.setManifestPlaceholder(name, value);
  }

  @Override
  @NotNull
  public BuildTypeModel setManifestPlaceholder(@NotNull String name, int value) {
    return (BuildTypeModelImpl)super.setManifestPlaceholder(name, value);
  }

  @Override
  @NotNull
  public BuildTypeModel setManifestPlaceholder(@NotNull String name, boolean value) {
    return (BuildTypeModelImpl)super.setManifestPlaceholder(name, value);
  }

  @Override
  @NotNull
  public BuildTypeModel removeManifestPlaceholder(@NotNull String name) {
    return (BuildTypeModelImpl)super.removeManifestPlaceholder(name);
  }

  @Override
  @NotNull
  public BuildTypeModel removeAllManifestPlaceholders() {
    return (BuildTypeModelImpl)super.removeAllManifestPlaceholders();
  }

  @Override
  @NotNull
  public BuildTypeModel setMultiDexEnabled(boolean multiDexEnabled) {
    return (BuildTypeModelImpl)super.setMultiDexEnabled(multiDexEnabled);
  }

  @Override
  @NotNull
  public BuildTypeModel removeMultiDexEnabled() {
    return (BuildTypeModelImpl)super.removeMultiDexEnabled();
  }

  @Override
  @NotNull
  public BuildTypeModel addProguardFile(@NotNull String proguardFile) {
    return (BuildTypeModelImpl)super.addProguardFile(proguardFile);
  }

  @Override
  @NotNull
  public BuildTypeModel removeProguardFile(@NotNull String proguardFile) {
    return (BuildTypeModelImpl)super.removeProguardFile(proguardFile);
  }

  @Override
  @NotNull
  public BuildTypeModel removeAllProguardFiles() {
    return (BuildTypeModelImpl)super.removeAllProguardFiles();
  }

  @Override
  @NotNull
  public BuildTypeModel replaceProguardFile(@NotNull String oldProguardFile, @NotNull String newProguardFile) {
    return (BuildTypeModelImpl)super.replaceProguardFile(oldProguardFile, newProguardFile);
  }

  @Override
  @NotNull
  public BuildTypeModel addResValue(@NotNull ResValue resValue) {
    return (BuildTypeModelImpl)super.addResValue(resValue);
  }

  @Override
  @NotNull
  public BuildTypeModel removeResValue(@NotNull ResValue resValue) {
    return (BuildTypeModelImpl)super.removeResValue(resValue);
  }

  @Override
  @NotNull
  public BuildTypeModel removeAllResValues() {
    return (BuildTypeModelImpl)super.removeAllResValues();
  }

  @Override
  @NotNull
  public BuildTypeModel replaceResValue(@NotNull ResValue oldResValue, @NotNull ResValue newResValue) {
    return (BuildTypeModelImpl)super.replaceResValue(oldResValue, newResValue);
  }

  @Override
  @NotNull
  public BuildTypeModel setUseJack(boolean useJack) {
    return (BuildTypeModelImpl)super.setUseJack(useJack);
  }

  @Override
  @NotNull
  public BuildTypeModel removeUseJack() {
    return (BuildTypeModelImpl)super.removeUseJack();
  }


  /**
   * Represents a {@code buildConfigField} statement defined in the build type block of the Gradle file.
   */
  public final static class BuildConfigFieldImpl extends TypeNameValueElementImpl implements BuildConfigField {
    public BuildConfigFieldImpl(@NotNull String type, @NotNull String name, @NotNull String value) {
      super(BUILD_CONFIG_FIELD, type, name, value);
    }
  }
}
