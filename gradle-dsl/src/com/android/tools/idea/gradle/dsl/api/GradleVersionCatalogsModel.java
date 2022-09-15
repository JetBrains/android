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
package com.android.tools.idea.gradle.dsl.api;

import com.android.tools.idea.gradle.dsl.api.ext.ExtModel;
import java.util.Set;

/**
 * Version Catalogs Model covers all catalog files that relate to a project.
 * Each ExtModel returned from this model represents the entries in the corresponding table from a single catalog
 *
 * Effective model of GradleVersionCatalogsModel is a map of maps. VersionCatalogModel -> ExtModel -> name-value properties.
 */
public interface GradleVersionCatalogsModel {
  // Having ExtModels, as return value is the closest thing we currently have (an arbitrary-sized collection of
  // arbitrary named Dsl values).  The ExtModel for versions in particular might be doing double
  // duty in order to support exposing its contents as PsVariables. (b/238982664)
  ExtModel libraries(String catalogName);

  ExtModel plugins(String catalogName);

  ExtModel versions(String catalogName);

  ExtModel bundles(String catalogName);

  /**
   * Get names for all catalogs that are in use for particular project
   */
  Set<String> catalogNames();

  GradleVersionCatalogModel getVersionCatalogModel(String catalogName);
}
