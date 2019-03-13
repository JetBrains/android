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
import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec
import com.android.tools.idea.gradle.structure.model.PsLibraryDependency
import com.android.tools.idea.gradle.structure.model.PsQuickFix
import java.io.Serializable

data class PsLibraryDependencyScopeQuickFixPath(
  val moduleName: String,
  val dependency: String,
  val oldConfigurationName: String,
  val newConfigurationName: String
) : PsQuickFix, Serializable {
  override val text = "Update $oldConfigurationName to $newConfigurationName"

  constructor(
    dependency: PsLibraryDependency,
    newConfigurationName: String
  ): this(
    dependency.parent.name, dependency.spec.compactNotation(), dependency.joinedConfigurationNames, newConfigurationName
  )

  override fun execute(context: PsContext) {
    val module = context.project.findModuleByName(moduleName)
    val spec = PsArtifactDependencySpec.create(dependency)
    if (module != null && spec != null) {
      module.setLibraryDependencyConfiguration(spec, oldConfigurationName, newConfigurationName)
    }
  }

  override fun toString(): String = "$dependency ($oldConfigurationName)"
}
