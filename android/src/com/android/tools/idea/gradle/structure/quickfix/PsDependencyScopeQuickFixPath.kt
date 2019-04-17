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
import com.android.tools.idea.gradle.structure.model.PsDeclaredDependency
import com.android.tools.idea.gradle.structure.model.PsJarDependency
import com.android.tools.idea.gradle.structure.model.PsLibraryDependency
import com.android.tools.idea.gradle.structure.model.PsModuleDependency
import com.android.tools.idea.gradle.structure.model.PsQuickFix
import java.io.Serializable

enum class PsDependencyKind {
  LIBRARY,
  JAR,
  MODULE,
  UNKNOWN,
}

fun dependencyKind(dependency: PsDeclaredDependency) : PsDependencyKind {
  return when(dependency) {
    is PsLibraryDependency -> PsDependencyKind.LIBRARY
    is PsJarDependency -> PsDependencyKind.JAR
    is PsModuleDependency -> PsDependencyKind.MODULE
    else -> PsDependencyKind.UNKNOWN
  }
}

data class PsDependencyScopeQuickFixPath(
  val moduleName: String,
  val dependencyKind: PsDependencyKind,
  val dependencyKey: String,
  val oldConfigurationName: String,
  val newConfigurationName: String
) : PsQuickFix, Serializable {
  override val text = "Update $oldConfigurationName to $newConfigurationName"

  constructor(
    dependency: PsDeclaredDependency,
    newConfigurationName: String
  ): this(
    dependency.parent.name, dependencyKind(dependency), dependency.toKey(), dependency.joinedConfigurationNames, newConfigurationName
  )

  override fun execute(context: PsContext) {
    val module = context.project.findModuleByName(moduleName) ?: return
    // TODO(xof): factor into method(s) on PsDeclaredDependency classes
    // (would need dependencyKey to be unique across all kinds of dependency, or some similar mechanism)
    val dependencies = when (dependencyKind) {
      PsDependencyKind.LIBRARY -> module.dependencies.findLibraryDependencies(dependencyKey)
      PsDependencyKind.JAR -> module.dependencies.findJarDependencies(dependencyKey)
      PsDependencyKind.MODULE -> module.dependencies.findModuleDependencies(dependencyKey)
      else -> return
    }

    dependencies
      .filter { it.configurationName == oldConfigurationName }
      .forEach { module.modifyDependencyConfiguration(it, newConfigurationName) }
  }

  override fun toString(): String = "$dependencyKey ($oldConfigurationName)"
}
