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
package com.android.tools.idea.gradle.structure.configurables.dependencies.treeview

import com.android.tools.idea.gradle.dsl.api.dependencies.DependencyModel
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsModelNode
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsNode
import com.android.tools.idea.gradle.structure.model.*

abstract class AbstractDependencyNode<T : PsBaseDependency> : AbstractPsModelNode<T> {

  val isDeclared: Boolean get() = models.any { it.isDeclared }

  protected constructor(parent: AbstractPsNode, dependency: T) : super(parent, dependency, parent.uiSettings)
  protected constructor(parent: AbstractPsNode, dependencies: List<T>) : super(parent, dependencies, parent.uiSettings)

  companion object {
    fun createResolvedNode(parent: AbstractPsNode,
                           dependency: PsBaseDependency): AbstractDependencyNode<*>? =
      when (dependency) {
        is PsResolvedLibraryDependency -> createResolvedLibraryDependencyNode(parent, dependency, forceGroupId = false)
        is PsResolvedModuleDependency -> ModuleDependencyNode(parent, dependency)
        else -> null
      }

    fun getDependencyParsedModels(model: PsBaseDependency): List<DependencyModel> =
      when (model) {
        is PsResolvedDependency -> model.getParsedModels()
        is PsDeclaredDependency -> listOf(model.parsedModel)
        else -> listOf()
      }
  }
}
