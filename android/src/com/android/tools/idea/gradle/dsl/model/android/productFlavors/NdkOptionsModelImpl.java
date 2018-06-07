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

import com.android.tools.idea.gradle.dsl.api.android.productFlavors.NdkOptionsModel;
import com.android.tools.idea.gradle.dsl.api.values.GradleNotNullValue;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.parser.android.productFlavors.NdkOptionsDslElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class NdkOptionsModelImpl extends GradleDslBlockModel implements NdkOptionsModel {
  @NonNls private static final String ABI_FILTERS = "abiFilters";

  public NdkOptionsModelImpl(@NotNull NdkOptionsDslElement dslElement) {
    super(dslElement);
  }

  @Override
  @Nullable
  public List<GradleNotNullValue<String>> abiFilters() {
    return myDslElement.getListProperty(ABI_FILTERS, String.class);
  }

  @Override
  @NotNull
  public NdkOptionsModel addAbiFilter(@NotNull String abiFilter) {
    myDslElement.addToNewLiteralList(ABI_FILTERS, abiFilter);
    return this;
  }

  @Override
  @NotNull
  public NdkOptionsModel removeAbiFilter(@NotNull String abiFilter) {
    myDslElement.removeFromExpressionList(ABI_FILTERS, abiFilter);
    return this;
  }

  @Override
  @NotNull
  public NdkOptionsModel removeAllAbiFilters() {
    myDslElement.removeProperty(ABI_FILTERS);
    return this;
  }

  @Override
  @NotNull
  public NdkOptionsModel replaceAbiFilter(@NotNull String oldAbiFilter, @NotNull String newAbiFilter) {
    myDslElement.replaceInExpressionList(ABI_FILTERS, oldAbiFilter, newAbiFilter);
    return this;
  }
}
