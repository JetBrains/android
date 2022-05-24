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
package com.android.tools.idea.gradle.structure.configurables.ui.dependencies

import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings
import com.android.tools.idea.gradle.structure.model.PsBaseDependency
import com.android.tools.idea.gradle.structure.model.PsJarDependency
import com.android.tools.idea.gradle.structure.model.PsLibraryDependency
import com.android.tools.idea.gradle.structure.model.PsModuleDependency

class PsDependencyComparator(private val myUiSettings: PsUISettings) : Comparator<PsBaseDependency> {

  private fun PsBaseDependency.getTypePriority() = when (this) {
    is PsModuleDependency -> 0
    is PsLibraryDependency -> 1
    is PsJarDependency -> 2
    else -> -1
  }

  private fun PsBaseDependency.getSortText() = when (this) {
    is PsModuleDependency -> toText()
    is PsLibraryDependency -> spec.getDisplayText(myUiSettings)
    is PsJarDependency -> filePath
    else -> name
  }

  override fun compare(d1: PsBaseDependency, d2: PsBaseDependency): Int =
    compareValuesBy(d1, d2, { it.getTypePriority() }, { it.getSortText() })
}
