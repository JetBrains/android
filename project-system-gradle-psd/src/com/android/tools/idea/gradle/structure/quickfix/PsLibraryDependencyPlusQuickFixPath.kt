/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.quickfix

import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.android.tools.idea.gradle.structure.model.PsLibraryDependency
import com.android.tools.idea.gradle.structure.model.PsQuickFix
import java.io.Serializable

data class PsLibraryDependencyPlusQuickFixPath(
  val moduleName : String,
  val dependencyGroup : String?,
  val dependencyName : String,
  val configurationName : String
) : PsQuickFix, Serializable {
  override val text: String = "View Declaration"

  constructor(dependency : PsLibraryDependency) : this(
    dependency.parent.name,
    dependency.spec.group,
    dependency.spec.name,
    dependency.spec.compactNotation()
  )

  override fun execute(context: PsContext) {
    val module = context.project.findModuleByName(moduleName) ?: return
    val dependency = module.dependencies.findLibraryDependencies(dependencyGroup, dependencyName).firstOrNull() ?: return

    context.mainConfigurable.navigateTo(
      dependency.path.getPlaceDestination(context),
      true
    )
  }

  override fun toString(): String = "View declaration of ${dependencyGroup}:${dependencyName}"
}
