/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyType.UNSPECIFIED_FOR_NOW;

import com.android.tools.idea.gradle.dsl.api.android.KotlinOptionsModel;
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel;
import com.android.tools.idea.gradle.dsl.api.java.LanguageLevelPropertyModel;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.parser.android.KotlinOptionsDslElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyDescription;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class KotlinOptionsModelImpl extends GradleDslBlockModel implements KotlinOptionsModel {
  @NonNls public static final ModelPropertyDescription FREE_COMPILER_ARGS =
    new ModelPropertyDescription("mFreeCompilerArgs", UNSPECIFIED_FOR_NOW);
  @NonNls public static final String JVM_TARGET = "mJvmTarget";
  @NonNls public static final String USE_IR = "mUseIR";

  public KotlinOptionsModelImpl(@NotNull KotlinOptionsDslElement dslElement) {
    super(dslElement);
  }

  @Override
  @NotNull
  public LanguageLevelPropertyModel jvmTarget() {
    return getJvmTargetModelForProperty(JVM_TARGET);
  }

  @NotNull
  @Override
  public GradlePropertyModel useIR() {
    return getModelForProperty(USE_IR);
  }

  @NotNull
  @Override
  public GradlePropertyModel freeCompilerArgs() {
    return getModelForProperty(FREE_COMPILER_ARGS);
  }
}
