/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.navigation

import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.android.tools.idea.gradle.structure.configurables.dependencies.module.MODULE_DEPENDENCIES_PLACE_NAME
import com.android.tools.idea.gradle.structure.model.PsJarDependency
import com.android.tools.idea.gradle.structure.model.PsModulePath
import com.android.tools.idea.gradle.structure.model.PsPlaceBasedPath
import com.intellij.ui.navigation.Place

data class PsJarDependencyNavigationPath(override val parent: PsDependenciesNavigationPath, val dependency: String) : PsPlaceBasedPath() {
  constructor (dependency: PsJarDependency) :
    this(PsDependenciesNavigationPath(PsModulePath(dependency.parent)), "${dependency.joinedConfigurationNames}/${dependency.filePath}")

  override fun queryPlace(place: Place, context: PsContext) {
    parent.queryPlace(place, context)
    place.putPath(MODULE_DEPENDENCIES_PLACE_NAME, dependency)
  }

  override fun toString(): String = dependency
}
