/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.ng.variantonly;

import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;

public class VariantOnlySyncAction implements BuildAction<VariantOnlyProjectModels>, Serializable {
  @NotNull private final VariantOnlySyncOptions myOptions;

  public VariantOnlySyncAction(@NotNull VariantOnlySyncOptions options) {
    myOptions = options;
  }

  @Override
  @Nullable
  public VariantOnlyProjectModels execute(@NotNull BuildController controller) {
    VariantOnlyProjectModels models = new VariantOnlyProjectModels(myOptions);
    models.populate(controller);
    return models;
  }
}