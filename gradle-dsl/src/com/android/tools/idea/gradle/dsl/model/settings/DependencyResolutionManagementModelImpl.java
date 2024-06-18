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

import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.STRING_TYPE;
import static com.android.tools.idea.gradle.dsl.parser.settings.DependencyResolutionManagementDslElement.DEFAULT_LIBRARIES_EXTENSION_NAME;
import static com.android.tools.idea.gradle.dsl.parser.settings.VersionCatalogDslElement.VERSION_CATALOG;
import static com.android.tools.idea.gradle.dsl.parser.settings.VersionCatalogsDslElement.VERSION_CATALOGS;

import com.android.tools.idea.gradle.dsl.api.repositories.RepositoriesModel;
import com.android.tools.idea.gradle.dsl.api.settings.DependencyResolutionManagementModel;
import com.android.tools.idea.gradle.dsl.api.settings.VersionCatalogModel;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.model.repositories.RepositoriesModelImpl;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.repositories.RepositoriesDslElement;
import com.android.tools.idea.gradle.dsl.parser.settings.DependencyResolutionManagementDslElement;
import com.android.tools.idea.gradle.dsl.parser.settings.VersionCatalogDslElement;
import com.android.tools.idea.gradle.dsl.parser.settings.VersionCatalogsDslElement;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class DependencyResolutionManagementModelImpl extends GradleDslBlockModel implements DependencyResolutionManagementModel {

  public DependencyResolutionManagementModelImpl(DependencyResolutionManagementDslElement element) {
    super(element);
  }

  @Override
  public @NotNull RepositoriesModel repositories() {
    RepositoriesDslElement repositoriesElement = myDslElement.ensurePropertyElement(RepositoriesDslElement.REPOSITORIES);
    return new RepositoriesModelImpl(repositoriesElement);
  }

  @Override
  public @NotNull List<VersionCatalogModel> versionCatalogs() {
    VersionCatalogsDslElement versionCatalogs = myDslElement.ensurePropertyElement(VERSION_CATALOGS);
    return versionCatalogs.get(this);
  }

  @NotNull
  public String catalogDefaultName() {
    String value = getModelForProperty(DEFAULT_LIBRARIES_EXTENSION_NAME).getValue(STRING_TYPE);
    return value == null ? VersionCatalogModel.DEFAULT_CATALOG_NAME : value;
  }

  @Override
  public @NotNull VersionCatalogModel addVersionCatalog(@NotNull String name) {
    VersionCatalogsDslElement versionCatalogs = myDslElement.ensurePropertyElement(VERSION_CATALOGS);
    VersionCatalogDslElement versionCatalog = versionCatalogs.ensureNamedPropertyElement(VERSION_CATALOG, GradleNameElement.create(name));
    return new VersionCatalogModelImpl(versionCatalog, this);
  }

  @Override
  public void removeVersionCatalog(@NotNull String name) {
    VersionCatalogsDslElement versionCatalogs = myDslElement.getPropertyElement(VERSION_CATALOGS);
    if (versionCatalogs != null) {
      versionCatalogs.removeProperty(name);
    }
  }
}
