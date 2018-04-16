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
package com.android.tools.idea.gradle.dsl.model.android.productFlavors.externalNativeBuild;

import com.android.tools.idea.gradle.dsl.api.android.productFlavors.externalNativeBuild.AbstractBuildOptionsModel;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractBuildOptionsModelImpl extends GradleDslBlockModel implements AbstractBuildOptionsModel {
  @NonNls private static final String ABI_FILTERS = "abiFilters";
  @NonNls private static final String ARGUMENTS = "arguments";
  @NonNls private static final String C_FLAGS = "cFlags";
  @NonNls private static final String CPP_FLAGS = "cppFlags";
  @NonNls private static final String TARGETS = "targets";

  protected AbstractBuildOptionsModelImpl(@NotNull GradlePropertiesDslElement dslElement) {
    super(dslElement);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel abiFilters() {
    return getModelForProperty(ABI_FILTERS, true);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel arguments() {
    return getModelForProperty(ARGUMENTS, true);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel cFlags() {
    return getModelForProperty(C_FLAGS, true);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel cppFlags() {
    return getModelForProperty(CPP_FLAGS, true);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel targets() {
    return getModelForProperty(TARGETS, true);
  }
}
