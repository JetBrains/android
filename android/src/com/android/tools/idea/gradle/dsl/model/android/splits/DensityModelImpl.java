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
package com.android.tools.idea.gradle.dsl.model.android.splits;

import com.android.tools.idea.gradle.dsl.api.android.splits.DensityModel;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelBuilder;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DensityModelImpl extends GradleDslBlockModel implements DensityModel {
  @NonNls private static final String AUTO = "auto";
  @NonNls private static final String COMPATIBLE_SCREENS = "compatibleScreens";
  @NonNls private static final String ENABLE = "enable";
  @NonNls private static final String EXCLUDE = "exclude";
  @NonNls private static final String INCLUDE = "include";
  @NonNls private static final String RESET = "reset";

  public DensityModelImpl(@NotNull GradlePropertiesDslElement dslElement) {
    super(dslElement);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel auto() {
    return GradlePropertyModelBuilder.create(myDslElement, AUTO).asMethod(true).buildResolved();
  }

  @Override
  @NotNull
  public ResolvedPropertyModel compatibleScreens() {
    return GradlePropertyModelBuilder.create(myDslElement, COMPATIBLE_SCREENS).asMethod(true).buildResolved();
  }

  @Override
  @NotNull
  public ResolvedPropertyModel enable() {
    return GradlePropertyModelBuilder.create(myDslElement, ENABLE).asMethod(true).buildResolved();
  }

  @Override
  @NotNull
  public ResolvedPropertyModel exclude() {
    return GradlePropertyModelBuilder.create(myDslElement, EXCLUDE).asMethod(true).buildResolved();
  }

  @Override
  @Nullable
  public ResolvedPropertyModel include() {
    return GradlePropertyModelBuilder.create(myDslElement, INCLUDE).asMethod(true).buildResolved();
  }

  @Override
  public void addReset() {
    GradleDslMethodCall resetMethod = new GradleDslMethodCall(myDslElement, GradleNameElement.empty(), RESET);
    myDslElement.setNewElement(resetMethod); // TODO: reset include
  }

  @Override
  public void removeReset() {
    myDslElement.removeProperty(RESET);
  }
}
