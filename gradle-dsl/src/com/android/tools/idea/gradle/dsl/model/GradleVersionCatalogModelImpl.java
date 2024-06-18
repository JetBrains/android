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

import static com.android.tools.idea.gradle.dsl.api.settings.VersionCatalogModel.DEFAULT_CATALOG_FILE_NAME;

import com.android.tools.idea.gradle.dsl.api.GradleVersionCatalogModel;
import com.android.tools.idea.gradle.dsl.api.catalog.GradleVersionCatalogPlugins;
import com.android.tools.idea.gradle.dsl.api.catalog.GradleVersionCatalogVersions;
import com.android.tools.idea.gradle.dsl.api.catalog.GradleVersionCatalogLibraries;
import com.android.tools.idea.gradle.dsl.api.ext.ExtModel;
import com.android.tools.idea.gradle.dsl.model.catalog.GradleVersionCatalogPluginsImpl;
import com.android.tools.idea.gradle.dsl.model.catalog.GradleVersionCatalogVersionsImpl;
import com.android.tools.idea.gradle.dsl.model.catalog.GradleVersionCatalogLibrariesImpl;
import com.android.tools.idea.gradle.dsl.model.ext.ExtModelImpl;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap;
import com.android.tools.idea.gradle.dsl.parser.files.GradleVersionCatalogFile;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import org.jetbrains.annotations.NotNull;

public class GradleVersionCatalogModelImpl extends GradleFileModelImpl implements GradleVersionCatalogModel {
  private final String catalogName;
  private final GradleVersionCatalogFile catalogFile;
  public GradleVersionCatalogModelImpl(@NotNull GradleVersionCatalogFile file){
    super(file);
    catalogName = file.getCatalogName();
    catalogFile = file;
  }

  private GradleDslExpressionMap ensureMap(String sectionName){
   return  catalogFile.ensurePropertyElement(
      new PropertiesElementDescription<>(sectionName, GradleDslExpressionMap.class, GradleDslExpressionMap::new)
    );
  }

  private ExtModel extractByName(String sectionName) {
    GradleDslExpressionMap librariesDslElement = ensureMap(sectionName);
    return new ExtModelImpl(librariesDslElement,
                            GradleVersionCatalogPropertyModel::new,
                            GradleVersionCatalogPropertyModel::new);
  }

  @NotNull
  @Override
  public ExtModel libraries() {
    return extractByName("libraries");
  }

  @NotNull
  @Override
  public GradleVersionCatalogLibraries libraryDeclarations(){
    GradleDslExpressionMap librariesDslElement = ensureMap("libraries");
    return new GradleVersionCatalogLibrariesImpl(librariesDslElement);
  }

  @NotNull
  @Override
  public GradleVersionCatalogVersions versionDeclarations() {
    GradleDslExpressionMap versionsDslElement = ensureMap("versions");
    return new GradleVersionCatalogVersionsImpl(versionsDslElement);
  }

  @NotNull
  @Override
  public GradleVersionCatalogPlugins pluginDeclarations() {
    GradleDslExpressionMap pluginsDslElement = ensureMap("plugins");
    return new GradleVersionCatalogPluginsImpl(pluginsDslElement);
  }

  @NotNull
  @Override
  public ExtModel plugins() {
    return extractByName("plugins");
  }

  @NotNull
  @Override
  public ExtModel versions() {
    return extractByName("versions");
  }

  @NotNull
  @Override
  public ExtModel bundles() {
    return extractByName("bundles");
  }

  @NotNull
  @Override
  public String catalogName() {
    return catalogName;
  }

  @Override
  public boolean isDefault() {
    return DEFAULT_CATALOG_FILE_NAME.equals(catalogFile.getFile().getName());
  }
}
