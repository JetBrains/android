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

import static com.android.tools.idea.gradle.dsl.api.settings.VersionCatalogModel.VersionCatalogSource.FILES;
import static com.android.tools.idea.gradle.dsl.api.settings.VersionCatalogModel.VersionCatalogSource.IMPORTED;

import com.android.tools.idea.gradle.dsl.api.settings.DependencyResolutionManagementModel;
import com.android.tools.idea.gradle.dsl.api.settings.VersionCatalogModel;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelBuilder;
import com.android.tools.idea.gradle.dsl.model.ext.transforms.SingleArgumentMethodTransform;
import com.android.tools.idea.gradle.dsl.parser.settings.VersionCatalogDslElement;
import org.jetbrains.annotations.NotNull;

public class VersionCatalogModelImpl extends GradleDslBlockModel implements VersionCatalogModel {
  public static String FROM = "mFrom";
  private DependencyResolutionManagementModel _dependencyResolutionManagement;
  private VersionCatalogDslElement catalogDslElement;

  public VersionCatalogModelImpl(VersionCatalogDslElement element, DependencyResolutionManagementModel dependencyResolutionManagement) {
    super(element);
    catalogDslElement = element;
    _dependencyResolutionManagement = dependencyResolutionManagement;
  }

  @Override
  public @NotNull String getName() {
    return myDslElement.getName();
  }

  @Override
  public @NotNull FromCatalogResolvedProperty from() {
    GradlePropertyModelBuilder builder = GradlePropertyModelBuilder.create(myDslElement, FROM);
    builder = getBuilderForDefaultCatalog(builder);
    if (catalogDslElement.isFile() || catalogDslElement.getPropertyElement(FROM) == null) {
      return new FromCatalogResolvedProperty(catalogDslElement,
                                             builder.addTransform(new SingleArgumentMethodTransform("files")).buildResolved(),
                                             FILES);
    }
    else {
      return new FromCatalogResolvedProperty(catalogDslElement, builder.buildResolved(), IMPORTED);
    }
  }

  private GradlePropertyModelBuilder getBuilderForDefaultCatalog(GradlePropertyModelBuilder builder){
    String name = _dependencyResolutionManagement.catalogDefaultName();
    if (myDslElement.getName().equals(name)) {
      return builder.withDefault(DEFAULT_CATALOG_FILE);
    }
    return builder;
  }
}
