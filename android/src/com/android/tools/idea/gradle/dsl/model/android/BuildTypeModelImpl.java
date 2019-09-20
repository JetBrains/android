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
import com.android.tools.idea.gradle.dsl.parser.android.BuildTypeDslElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyLanguage;

public class BuildTypeModelImpl extends FlavorTypeModelImpl implements BuildTypeModel {
  @NonNls private static final String DEBUGGABLE = "debuggable";
  @NonNls private static final String IS_DEBUGGABLE = "isDebuggable";
  @NonNls private static final String EMBED_MICRO_APP = "embedMicroApp";
  @NonNls private static final String IS_EMBED_MICRO_APP = "isEmbedMicroApp";
  @NonNls private static final String JNI_DEBUGGABLE = "jniDebuggable";
  @NonNls private static final String IS_JNI_DEBUGGABLE = "isJniDebuggable";
  @NonNls private static final String MINIFY_ENABLED = "minifyEnabled";
  @NonNls private static final String IS_MINIFY_ENABLED = "isMinifyEnabled";
  @NonNls private static final String PSEUDO_LOCALES_ENABLED = "pseudoLocalesEnabled";
  @NonNls private static final String IS_PSEUDO_LOCALES_ENABLED = "isPseudoLocalesEnabled";
  @NonNls private static final String RENDERSCRIPT_DEBUGGABLE = "renderscriptDebuggable";
  @NonNls private static final String IS_RENDERSCRIPT_DEBUGGABLE = "isRenderscriptDebuggable";
  @NonNls private static final String RENDERSCRIPT_OPTIM_LEVEL = "renderscriptOptimLevel";
  @NonNls private static final String SHRINK_RESOURCES = "shrinkResources";
  @NonNls private static final String IS_SHRINK_RESOURCES = "isShrinkResources";
  @NonNls private static final String TEST_COVERAGE_ENABLED = "testCoverageEnabled";
  @NonNls private static final String IS_TEST_COVERAGE_ENABLED = "isTestCoverageEnabled";
  @NonNls private static final String ZIP_ALIGN_ENABLED = "zipAlignEnabled";
  @NonNls private static final String IS_ZIP_ALIGN_ENABLED = "isZipAlignEnabled";

  public BuildTypeModelImpl(@NotNull BuildTypeDslElement dslElement) {
    super(dslElement);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel debuggable() {
    String propertyName =
      this.myDslElement.getDslFile().getPsiElement().getLanguage() == GroovyLanguage.INSTANCE ? DEBUGGABLE : IS_DEBUGGABLE;
    return getModelForProperty(propertyName);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel embedMicroApp() {
    String propertyName =
      this.myDslElement.getDslFile().getPsiElement().getLanguage() == GroovyLanguage.INSTANCE ? EMBED_MICRO_APP : IS_EMBED_MICRO_APP;
    return getModelForProperty(propertyName);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel jniDebuggable() {
    String propertyName =
      this.myDslElement.getDslFile().getPsiElement().getLanguage() == GroovyLanguage.INSTANCE ? JNI_DEBUGGABLE : IS_JNI_DEBUGGABLE;
    return getModelForProperty(propertyName);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel minifyEnabled() {
    String propertyName =
      this.myDslElement.getDslFile().getPsiElement().getLanguage() == GroovyLanguage.INSTANCE ? MINIFY_ENABLED : IS_MINIFY_ENABLED;
    return getModelForProperty(propertyName);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel pseudoLocalesEnabled() {
    String propertyName =
      this.myDslElement.getDslFile().getPsiElement().getLanguage() ==
      GroovyLanguage.INSTANCE ? PSEUDO_LOCALES_ENABLED : IS_PSEUDO_LOCALES_ENABLED;
    return getModelForProperty(propertyName);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel renderscriptDebuggable() {
    String propertyName =
      this.myDslElement.getDslFile().getPsiElement().getLanguage() ==
      GroovyLanguage.INSTANCE ? RENDERSCRIPT_DEBUGGABLE : IS_RENDERSCRIPT_DEBUGGABLE;
    return getModelForProperty(propertyName);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel renderscriptOptimLevel() {
    return getModelForProperty(RENDERSCRIPT_OPTIM_LEVEL);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel shrinkResources() {
    String propertyName =
      this.myDslElement.getDslFile().getPsiElement().getLanguage() == GroovyLanguage.INSTANCE ? SHRINK_RESOURCES : IS_SHRINK_RESOURCES;
    return getModelForProperty(propertyName);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel testCoverageEnabled() {
    String propertyName =
      this.myDslElement.getDslFile().getPsiElement().getLanguage() ==
      GroovyLanguage.INSTANCE ? TEST_COVERAGE_ENABLED : IS_TEST_COVERAGE_ENABLED;
    return getModelForProperty(propertyName);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel zipAlignEnabled() {
    String propertyName =
      this.myDslElement.getDslFile().getPsiElement().getLanguage() == GroovyLanguage.INSTANCE ? ZIP_ALIGN_ENABLED : IS_ZIP_ALIGN_ENABLED;
    return getModelForProperty(propertyName);
  }

}
