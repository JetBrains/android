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
package com.android.tools.idea.gradle.dsl.api.settings;

import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.api.util.GradleBlockModel;
import org.jetbrains.annotations.NotNull;

public interface VersionCatalogModel extends GradleBlockModel {
  String DEFAULT_CATALOG_NAME = "libs";
  String DEFAULT_CATALOG_FILE = "gradle/libs.versions.toml";

  @NotNull String getName();

  /**
   * Strictly speaking, from() takes a Dependency, rather than (as currently modelled) a string specification within a call to
   * `files()`.  At some point this interface method may change its signature to reflect that.
   *
   * @return a resolved property model
   */
  @NotNull ResolvedPropertyModel from();
}
