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
package com.android.tools.idea.gradle.dsl.model;

import com.android.tools.idea.gradle.dsl.api.GradleVersionCatalogModel;
import com.android.tools.idea.gradle.dsl.api.ext.ExtModel;
import com.android.tools.idea.gradle.dsl.model.ext.ExtModelImpl;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap;
import com.android.tools.idea.gradle.dsl.parser.files.GradleVersionCatalogFile;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import org.jetbrains.annotations.NotNull;

public class GradleVersionCatalogModelImpl extends GradleFileModelImpl implements GradleVersionCatalogModel {

  GradleVersionCatalogModelImpl(@NotNull GradleVersionCatalogFile versionCatalogFile) {
    super(versionCatalogFile);
  }

  @Override
  public @NotNull ExtModel libraries() {
    GradleDslExpressionMap librariesDslElement = myGradleDslFile.ensurePropertyElement(
      new PropertiesElementDescription<>("libraries", GradleDslExpressionMap.class, GradleDslExpressionMap::new)
    );
    return new ExtModelImpl(librariesDslElement);
  }

  @Override
  public @NotNull ExtModel versions() {
    GradleDslExpressionMap versionsDslElement = myGradleDslFile.ensurePropertyElementAt(
      new PropertiesElementDescription<>("versions", GradleDslExpressionMap.class, GradleDslExpressionMap::new), 0);
    return new ExtModelImpl(versionsDslElement);
  }
}
