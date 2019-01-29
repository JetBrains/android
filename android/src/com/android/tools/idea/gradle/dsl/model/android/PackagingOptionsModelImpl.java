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

import com.android.tools.idea.gradle.dsl.api.android.PackagingOptionsModel;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.parser.android.PackagingOptionsDslElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class PackagingOptionsModelImpl extends GradleDslBlockModel implements PackagingOptionsModel {
  @NonNls private static final String EXCLUDES = "excludes";
  @NonNls private static final String MERGES = "merges";
  @NonNls private static final String PICK_FIRSTS = "pickFirsts";

  public PackagingOptionsModelImpl(@NotNull PackagingOptionsDslElement dslElement) {
    super(dslElement);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel excludes() {
    return getModelForProperty(EXCLUDES, true);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel merges() {
    return getModelForProperty(MERGES, true);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel pickFirsts() {
    return getModelForProperty(PICK_FIRSTS, true);
  }
}
