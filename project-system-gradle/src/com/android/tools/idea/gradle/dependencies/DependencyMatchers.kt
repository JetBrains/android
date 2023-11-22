/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.ide.common.gradle.Dependency
import com.android.tools.idea.gradle.dsl.api.PluginModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel
import com.android.tools.idea.gradle.dsl.api.dependencies.LibraryDeclarationModel
import com.android.tools.idea.gradle.dsl.api.dependencies.PluginDeclarationModel

// Able to compare version catalog declarations (LibraryDeclarationModel)
// and build script declarations (ArtifactDependencyModel)
interface DependencyMatcher {
  fun match(model: LibraryDeclarationModel): Boolean
  fun match(model: ArtifactDependencyModel): Boolean
}

// Able to compare plugins declared in catalog (PluginDeclarationModel)
// and plugins from build scripts (PluginModel)
interface PluginMatcher {
  fun match(model: PluginDeclarationModel): Boolean
  fun match(model: PluginModel): Boolean
}

class ExactDependencyMatcher(val configuration: String, val compactNotation: String) : DependencyMatcher {
  override fun match(model: LibraryDeclarationModel) =
    model.compactNotation() == compactNotation
  override fun match(model: ArtifactDependencyModel): Boolean =
    model.configurationName() == configuration && model.compactNotation() == compactNotation
}

class GroupNameDependencyMatcher(val configuration: String, val compactNotation: String) : DependencyMatcher {
  val group: String?
  val name: String

  init {
    val dep = Dependency.parse(compactNotation)
    group = dep.group
    name = dep.name
  }

  override fun match(model: LibraryDeclarationModel) =
    with(model) { group().toString() == group && name().toString() == name }
  override fun match(model: ArtifactDependencyModel): Boolean =
    with(model) {
      configurationName() == configuration &&
        group().toString() == group &&
        name().toString() == name
    }
}

class IdPluginMatcher(val id: String) : PluginMatcher {
  override fun match(model: PluginDeclarationModel) = model.id().toString().defaultPluginName() == id.defaultPluginName()
  override fun match(model: PluginModel): Boolean = model.name().toString().defaultPluginName() == id.defaultPluginName()
}

// Always return does not match - for cases when special verification already been done and we just need to
// go through comparison
class FalsePluginMatcher : PluginMatcher {
  override fun match(model: PluginDeclarationModel) = false
  override fun match(model: PluginModel): Boolean = false
}

fun String.defaultPluginName() = when (this) {
  "kotlin-android" -> "org.jetbrains.kotlin.android"
  "kotlin" -> "org.jetbrains.kotlin.jvm"
  else -> this
}