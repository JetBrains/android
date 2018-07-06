/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.tools.idea.gradle.structure.configurables.DependenciesPerspectiveConfigurable
import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.android.tools.idea.gradle.structure.configurables.issues.GoToPathLinkHandler.GO_TO_PATH_TYPE
import com.android.tools.idea.gradle.structure.model.PsLibraryDependency
import com.android.tools.idea.gradle.structure.model.PsModulePath
import com.android.tools.idea.gradle.structure.model.PsPath
import com.android.tools.idea.gradle.structure.navigation.Places.serialize
import com.android.tools.idea.structure.dialog.ProjectStructureConfigurable.putPath
import com.intellij.ui.navigation.Place

data class PsLibraryDependencyNavigationPath(override val parent: PsModulePath, val dependency: String) : PsPath {
  constructor (dependency: PsLibraryDependency) : this(PsModulePath(dependency.parent), dependency.spec.compactNotation())

  override fun getHyperlinkDestination(context: PsContext): String {
    val place = Place()

    val mainConfigurable = context.mainConfigurable
    val target = mainConfigurable.findConfigurable(DependenciesPerspectiveConfigurable::class.java)!!

    putPath(place, target)
    target.putNavigationPath(place, parent.moduleName, dependency)

    return GO_TO_PATH_TYPE + serialize(place)
  }

  override fun toString(): String = dependency
}
