/*
 * Copyright (C) 2022 The Android Open Source Project
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

public class GradleVersionCatalogModelImpl extends GradleFileModelImpl implements GradleVersionCatalogModel {
  private String catalogName;
  private GradleVersionCatalogFile catalogFile;
  public GradleVersionCatalogModelImpl(GradleVersionCatalogFile file){
    super(file);
    catalogName = file.getCatalogName();
    catalogFile = file;
  }

  private ExtModel extractByName(String sectionName) {
    GradleDslExpressionMap librariesDslElement = catalogFile.ensurePropertyElement(
      new PropertiesElementDescription<>(sectionName, GradleDslExpressionMap.class, GradleDslExpressionMap::new)
    );
    return new ExtModelImpl(librariesDslElement,
                            GradleVersionCatalogPropertyModel::new,
                            GradleVersionCatalogPropertyModel::new);
  }

  @Override
  public ExtModel libraries() {
    return extractByName("libraries");
  }

  @Override
  public ExtModel plugins() {
    return extractByName("plugins");
  }

  @Override
  public ExtModel versions() {
    return extractByName("versions");
  }

  @Override
  public ExtModel bundles() {
    return extractByName("bundles");
  }

  @Override
  public String catalogName() {
    return catalogName;
  }

  @Override
  public String fileName() {
    return catalogFile.getFile().getName();
  }

  @Override
  public boolean isDefault() {
    return "libs".equals(catalogName);
  }

}
