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
import com.android.tools.idea.gradle.dsl.api.dependencies.LibraryDeclarationModel
import com.android.tools.idea.gradle.dsl.api.dependencies.PluginDeclarationModel

interface DependencyMatcher {
  fun match(model: LibraryDeclarationModel): Boolean
}

interface PluginMatcher {
  fun match(model: PluginDeclarationModel): Boolean
}

class ExactDependencyMatcher(val compactNotation: String) : DependencyMatcher {
  override fun match(model: LibraryDeclarationModel) =
    model.compactNotation() == compactNotation
}

class GroupNameDependencyMatcher(val compactNotation: String) : DependencyMatcher {
  val group: String?
  val name: String

  init {
    val dep = Dependency.parse(compactNotation)
    group = dep.group
    name = dep.name
  }

  override fun match(model: LibraryDeclarationModel) =
    with(model) { group().toString() == group && name().toString() == name }
}

class ExactPluginMatcher(val compactNotation: String) : PluginMatcher {
  override fun match(model: PluginDeclarationModel) =
    model.compactNotation() == compactNotation
}

class IdPluginMatcher(val id: String) : PluginMatcher {
  override fun match(model: PluginDeclarationModel) = model.id().toString() == id
}