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
package com.android.tools.idea.gradle.structure.quickfix

import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec
import com.android.tools.idea.gradle.structure.model.PsLibraryDependency
import com.android.tools.idea.gradle.structure.model.PsQuickFix
import java.io.Serializable

data class PsLibraryDependencyVersionQuickFixPath(
  val moduleName: String,
  val dependency: String,
  val configurationName: String,
  val version: String,
  val updateVariable: Boolean?,
  val addVersionInText: Boolean = false,
  val onUpdate: (() -> Unit)? = null,
) : PsQuickFix, Serializable {
  override val text: String
    get() {
      val updateText = when (updateVariable) {
        null -> "Update"
        true -> "Update Variable"
        false -> "Update Dependency"
      }
      return if (addVersionInText) {
        "$updateText\nto $version"
      }
      else {
        updateText
      }
    }

  constructor(
    dependency: PsLibraryDependency,
    version: String,
    updateVariable: Boolean? = null,
    addVersionInText: Boolean = false,
    onUpdate: (() -> Unit)? = null,
  ) : this(
    dependency.parent.name, dependency.spec.compactNotation(), dependency.joinedConfigurationNames, version, updateVariable, addVersionInText, onUpdate
  )

  override fun execute(context: PsContext) {
    val module = context.project.findModuleByName(moduleName)
    val spec = PsArtifactDependencySpec.create(dependency)
    if (module != null && spec != null) {
      module.setLibraryDependencyVersion(spec, configurationName, version, updateVariable ?: false)
    }
    onUpdate?.invoke()
  }

  override fun toString(): String = "$dependency ($configurationName)"
}
