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
package com.android.tools.idea.gradle.dsl.model.settings;

import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.api.settings.VersionCatalogModel;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelBuilder;
import com.android.tools.idea.gradle.dsl.model.ext.transforms.SingleArgumentMethodTransform;
import com.android.tools.idea.gradle.dsl.parser.settings.VersionCatalogDslElement;
import org.jetbrains.annotations.NotNull;

public class VersionCatalogModelImpl extends GradleDslBlockModel implements VersionCatalogModel {
  public static String FROM = "mFrom";

  public VersionCatalogModelImpl(VersionCatalogDslElement element) {
    super(element);
  }

  @Override
  public @NotNull String getName() {
    return myDslElement.getName();
  }

  @Override
  public @NotNull ResolvedPropertyModel from() {
    GradlePropertyModelBuilder builder = GradlePropertyModelBuilder.create(myDslElement, FROM);
    if (myDslElement.getName().equals(DEFAULT_CATALOG_NAME)) {
      builder = builder.withDefault(DEFAULT_CATALOG_FILE);
    }
    return builder.addTransform(new SingleArgumentMethodTransform("files")).buildResolved();
  }
}
