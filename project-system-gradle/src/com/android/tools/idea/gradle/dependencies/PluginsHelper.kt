/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.gradle.dependencies

import com.android.tools.idea.gradle.dependencies.AddDependencyPolicy.Companion.calculateAddDependencyPolicy
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel

/**
 * Similar to DependenciesHelper to manage adding plugins
 * to studio projects
 */
abstract class PluginsHelper {
  companion object {
    @JvmStatic
    fun withModel(projectModel: ProjectBuildModel): PluginsInserter =
      when (calculateAddDependencyPolicy(projectModel)) {
        AddDependencyPolicy.VERSION_CATALOG -> CatalogPluginsInserter(projectModel)
        AddDependencyPolicy.BUILD_FILE -> CommonPluginsInserter(projectModel)
        AddDependencyPolicy.DECLARATIVE -> DeclarativePluginsInserter(projectModel)
      }
  }
}