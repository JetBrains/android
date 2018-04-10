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
package com.android.tools.idea.gradle.structure.model

import com.android.tools.idea.gradle.dsl.api.dependencies.DependencyModel

abstract class PsDependency protected constructor(
  parent: PsModule,
  parsedModels: Collection<DependencyModel>
) : PsChildModel(parent), PsBaseDependency {
  private val parsedModelCollection = LinkedHashSet(parsedModels)

  override val joinedConfigurationNames: String get() = configurationNames.joinToString(separator = ", ")

  val configurationNames: List<String> get() = parsedModels.map { it.configurationName() }.distinct()

  override val isDeclared: Boolean get() = !parsedModels.isEmpty()

  val parsedModels: Set<DependencyModel> get() = parsedModelCollection

  init {
    parsedModelCollection.addAll(parsedModels)
  }

  open fun addParsedModel(parsedModel: DependencyModel) {
    parsedModelCollection.add(parsedModel)
  }

  enum class TextType {
    PLAIN_TEXT, FOR_NAVIGATION
  }
}
