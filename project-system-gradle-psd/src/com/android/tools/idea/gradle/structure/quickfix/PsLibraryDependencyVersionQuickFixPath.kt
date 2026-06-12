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

data class PsLibraryDependencyVersionQuickFixPath(
  val moduleName: String,
  val dependency: String,
  val configurationName: String,
  val version: String,
  val updateVariable: Boolean? = null,
  val addVersionInText: Boolean = false,
  // Non-serialized fields below
  val onUpdate: (() -> Unit)? = null,
) : PsQuickFix {
  override fun serialize(): String {
    val updateVariableStr =
      when (updateVariable) {
        true -> "true"
        false -> "false"
        null -> "null"
      }
    return "LibraryDependencyVersion|${PsQuickFix.escape(moduleName)}|${PsQuickFix.escape(dependency)}|${PsQuickFix.escape(configurationName)}|${PsQuickFix.escape(version)}|$updateVariableStr|$addVersionInText"
  }

  companion object {
    init {
      PsQuickFix.registerDeserializer("LibraryDependencyVersion", ::deserialize)
    }

    fun deserialize(args: List<String>): PsLibraryDependencyVersionQuickFixPath {
      if (args.size != 6) throw IllegalArgumentException("Invalid number of arguments: ${args.size}")
      val updateVariable =
        when (args[4]) {
          "true" -> true
          "false" -> false
          "null" -> null
          else -> throw IllegalArgumentException("Invalid boolean? value: ${args[4]}")
        }
      return PsLibraryDependencyVersionQuickFixPath(args[0], args[1], args[2], args[3], updateVariable, args[5].toBoolean())
    }
  }

  override val text: String
    get() {
      val updateText =
        when (updateVariable) {
          null -> "Update"
          true -> "Update Variable"
          false -> "Update Dependency"
        }
      return if (addVersionInText) {
        "$updateText\nto $version"
      } else {
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
    dependency.parent.name,
    dependency.spec.compactNotation(),
    dependency.joinedConfigurationNames,
    version,
    updateVariable,
    addVersionInText,
    onUpdate,
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
