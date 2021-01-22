/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model.android.packagingOptions;

import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyType.MUTABLE_SET;

import com.android.tools.idea.gradle.dsl.api.android.packagingOptions.JniLibsModel;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.parser.android.packagingOptions.JniLibsDslElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyDescription;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class JniLibsModelImpl extends GradleDslBlockModel implements JniLibsModel {
  @NonNls public static final ModelPropertyDescription EXCLUDES = new ModelPropertyDescription("mExcludes", MUTABLE_SET);
  @NonNls public static final ModelPropertyDescription PICK_FIRSTS = new ModelPropertyDescription("mPickFirsts", MUTABLE_SET);
  @NonNls public static final ModelPropertyDescription KEEP_DEBUG_SYMBOLS = new ModelPropertyDescription("mKeepDebugSymbols", MUTABLE_SET);
  @NonNls public static final String USE_LEGACY_PACKAGING = "mUseLegacyPackaging";

  public JniLibsModelImpl(@NotNull JniLibsDslElement element) {
    super(element);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel excludes() {
    return getModelForProperty(EXCLUDES);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel pickFirsts() {
    return getModelForProperty(PICK_FIRSTS);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel keepDebugSymbols() {
    return getModelForProperty(KEEP_DEBUG_SYMBOLS);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel useLegacyPackaging() {
    return getModelForProperty(USE_LEGACY_PACKAGING);
  }
}
