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
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.api.values.GradleNotNullValue;
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
  public ResolvedPropertyModel applicationIdSuffix() {
    return getModelForProperty(APPLICATION_ID_SUFFIX);
  }

  @Override
  public void removeApplicationIdSuffix() {
    myDslElement.removeProperty(APPLICATION_ID_SUFFIX);
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
  public void addBuildConfigField(@NotNull BuildConfigField buildConfigField) {
    addTypeNameValueElement(buildConfigField);
  }

  @Override
  public void removeBuildConfigField(@NotNull BuildConfigField buildConfigField) {
    removeTypeNameValueElement(buildConfigField);
  }

  @Override
  public void removeAllBuildConfigFields() {
    myDslElement.removeProperty(BUILD_CONFIG_FIELD);
  }

  @Override
  public void replaceBuildConfigField(@NotNull BuildConfigField oldBuildConfigField,
                                                @NotNull BuildConfigField newBuildConfigField) {
    replaceTypeNameValueElement(oldBuildConfigField, newBuildConfigField);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel debuggable() {
    return getModelForProperty(DEBUGGABLE);
  }

  @Override
  public void removeDebuggable() {
    myDslElement.removeProperty(DEBUGGABLE);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel embedMicroApp() {
    return getModelForProperty(EMBED_MICRO_APP);
  }

  @Override
  public void removeEmbedMicroApp() {
    myDslElement.removeProperty(EMBED_MICRO_APP);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel jniDebuggable() {
    return getModelForProperty(JNI_DEBUGGABLE);
  }

  @Override
  public void removeJniDebuggable() {
    myDslElement.removeProperty(JNI_DEBUGGABLE);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel minifyEnabled() {
    return getModelForProperty(MINIFY_ENABLED);
  }

  @Override
  public void removeMinifyEnabled() {
    myDslElement.removeProperty(MINIFY_ENABLED);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel pseudoLocalesEnabled() {
    return getModelForProperty(PSEUDO_LOCALES_ENABLED);
  }

  @Override
  public void removePseudoLocalesEnabled() {
    myDslElement.removeProperty(PSEUDO_LOCALES_ENABLED);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel renderscriptDebuggable() {
    return getModelForProperty(RENDERSCRIPT_DEBUGGABLE);
  }

  @Override
  public void removeRenderscriptDebuggable() {
    myDslElement.removeProperty(RENDERSCRIPT_DEBUGGABLE);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel renderscriptOptimLevel() {
    return getModelForProperty(RENDERSCRIPT_OPTIM_LEVEL);
  }

  @Override
  public void removeRenderscriptOptimLevel() {
    myDslElement.removeProperty(RENDERSCRIPT_OPTIM_LEVEL);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel shrinkResources() {
    return getModelForProperty(SHRINK_RESOURCES);
  }

  @Override
  public void removeShrinkResources() {
    myDslElement.removeProperty(SHRINK_RESOURCES);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel testCoverageEnabled() {
    return getModelForProperty(TEST_COVERAGE_ENABLED);
  }

  @Override
  public void removeTestCoverageEnabled() {
    myDslElement.removeProperty(TEST_COVERAGE_ENABLED);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel versionNameSuffix() {
    return getModelForProperty(VERSION_NAME_SUFFIX);
  }

  @Override
  public void removeVersionNameSuffix() {
    myDslElement.removeProperty(VERSION_NAME_SUFFIX);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel zipAlignEnabled() {
    return getModelForProperty(ZIP_ALIGN_ENABLED);
  }

  @Override
  public void removeZipAlignEnabled() {
    myDslElement.removeProperty(ZIP_ALIGN_ENABLED);
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
