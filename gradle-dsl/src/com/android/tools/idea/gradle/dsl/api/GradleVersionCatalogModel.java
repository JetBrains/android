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
package com.android.tools.idea.gradle.dsl.api;

import com.android.tools.idea.gradle.dsl.api.catalog.GradleVersionCatalogLibraries;
import com.android.tools.idea.gradle.dsl.api.catalog.GradleVersionCatalogPlugins;
import com.android.tools.idea.gradle.dsl.api.catalog.GradleVersionCatalogVersions;
import com.android.tools.idea.gradle.dsl.api.ext.ExtModel;
import org.jetbrains.annotations.NotNull;

/**
 * Version Catalog Model covers one version catalog.
 * Each ExtModel returned from this model represents the entries in the corresponding table
 * <p>
 * Effective model of GradleVersionCatalogModel is a list of maps. VersionCatalogModel -> ExtModel -> name-value properties.
 */
public interface GradleVersionCatalogModel extends GradleFileModel {

  @NotNull
  ExtModel libraries();

  /**
   * New API. Returns designated high level library Declaration model
   */
  @NotNull
  GradleVersionCatalogLibraries libraryDeclarations();

  @NotNull
  GradleVersionCatalogVersions versionDeclarations();

  @NotNull
  GradleVersionCatalogPlugins pluginDeclarations();

  @NotNull
  ExtModel plugins();

  @NotNull
  ExtModel versions();

  @NotNull
  ExtModel bundles();

  @NotNull
  String catalogName();

  boolean isDefault();
}
