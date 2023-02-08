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

import com.android.tools.idea.gradle.dsl.api.ext.ExtModel;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * Version Catalog Model covers one version catalog.
 * Each ExtModel returned from this model represents the entries in the corresponding table
 *
 * Effective model of GradleVersionCatalogModel is a list of maps. VersionCatalogModel -> ExtModel -> name-value properties.
 */
public interface GradleVersionCatalogModel {

  ExtModel libraries();

  ExtModel plugins();

  ExtModel versions();

  ExtModel bundles();

  String catalogName();

  VirtualFile getFile();

  boolean isDefault();
}
