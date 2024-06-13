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
package com.android.tools.idea.gradle.dsl.model.android.productFlavors;

import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyType.MUTABLE_LIST;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyType.MUTABLE_SET;

import com.android.tools.idea.gradle.dsl.api.android.productFlavors.NdkOptionsModel;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.parser.android.productFlavors.NdkOptionsDslElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyDescription;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class NdkOptionsModelImpl extends GradleDslBlockModel implements NdkOptionsModel {
  @NonNls public static final ModelPropertyDescription ABI_FILTERS = new ModelPropertyDescription("mAbiFilters", MUTABLE_SET);
  @NonNls public static final String MODULE_NAME = "mModuleName";
  @NonNls public static final String C_FLAGS = "mcFlags";
  @NonNls public static final ModelPropertyDescription LD_LIBS = new ModelPropertyDescription("mLdLibs", MUTABLE_LIST);
  @NonNls public static final String STL = "mStl";
  @NonNls public static final String JOBS = "mJobs";

  public NdkOptionsModelImpl(@NotNull NdkOptionsDslElement dslElement) {
    super(dslElement);
  }

  @Override
  public @NotNull ResolvedPropertyModel abiFilters() {
    return getModelForProperty(ABI_FILTERS);
  }

  @Override
  public @NotNull ResolvedPropertyModel cFlags() {
    return getModelForProperty(C_FLAGS);
  }

  @Override
  public @NotNull ResolvedPropertyModel jobs() {
    return getModelForProperty(JOBS);
  }

  @Override
  public @NotNull ResolvedPropertyModel ldLibs() {
    return getModelForProperty(LD_LIBS);
  }

  @Override
  public @NotNull ResolvedPropertyModel moduleName() {
    return getModelForProperty(MODULE_NAME);
  }

  @Override
  public @NotNull ResolvedPropertyModel stl() {
    return getModelForProperty(STL);
  }
}
