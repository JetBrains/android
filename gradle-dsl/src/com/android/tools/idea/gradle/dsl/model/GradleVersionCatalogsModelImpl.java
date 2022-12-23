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

import static java.util.function.Function.identity;

import com.android.tools.idea.gradle.dsl.api.GradleVersionCatalogModel;
import com.android.tools.idea.gradle.dsl.api.GradleVersionCatalogsModel;
import com.android.tools.idea.gradle.dsl.api.ext.ExtModel;
import com.android.tools.idea.gradle.dsl.model.ext.ExtModelImpl;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap;
import com.android.tools.idea.gradle.dsl.parser.files.GradleVersionCatalogFile;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

public class GradleVersionCatalogsModelImpl implements GradleVersionCatalogsModel {
  private Map<String, GradleVersionCatalogFile> versionCatalogFiles;

  GradleVersionCatalogsModelImpl(@NotNull Collection<GradleVersionCatalogFile> versionCatalogFile) {
    this.versionCatalogFiles = versionCatalogFile.stream().collect(Collectors.toMap(
      GradleVersionCatalogFile::getCatalogName,
      identity()));
  }

  private Map<String, ExtModel> extractByName(String sectionName) {
    return versionCatalogFiles.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, keyValue -> {
      GradleDslExpressionMap librariesDslElement = keyValue.getValue().ensurePropertyElement(
        new PropertiesElementDescription<>(sectionName, GradleDslExpressionMap.class, GradleDslExpressionMap::new)
      );
      return new ExtModelImpl(librariesDslElement, GradleVersionCatalogPropertyModel::new, GradleVersionCatalogPropertyModel::new);
    }));
  }

  @Override
  public ExtModel libraries(String catalogName) {
    return extractByName("libraries").get(catalogName);
  }

  @Override
  public ExtModel plugins(String catalogName) {
    return extractByName("plugins").get(catalogName);
  }

  @Override
  public ExtModel versions(String catalogName) {
    return extractByName("versions").get(catalogName);
  }

  @Override
  public ExtModel bundles(String catalogName) {
    return extractByName("bundles").get(catalogName);
  }

  public Set<String> catalogNames(){
    return versionCatalogFiles.keySet();
  }

  @Override
  public GradleVersionCatalogModel getVersionCatalogModel(String catalogName) {
    return new GradleVersionCatalogModelImpl(versionCatalogFiles.get(catalogName));
  }

}
