/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.dependencies.module

import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.android.tools.idea.gradle.structure.configurables.ui.dependencies.AbstractDeclaredDependenciesTableModel
import com.android.tools.idea.gradle.structure.configurables.ui.dependencies.PsDependencyComparator
import com.android.tools.idea.gradle.structure.model.PsBaseDependency
import com.android.tools.idea.gradle.structure.model.PsModule

/**
 * Model for the table displaying the "editable" dependencies of a module.
 */
internal class DeclaredDependenciesTableModel(
  module: PsModule,
  context: PsContext
) : AbstractDeclaredDependenciesTableModel<PsBaseDependency>(module, context) {

  override fun reset() {
    reset(null)
  }

  fun reset(changedDependency: PsBaseDependency?) {
    val dependencies = module.dependencies.items.sortedWith(PsDependencyComparator(context.uiSettings))
    val allItems = dependencies.toMutableSet()

    for (index in items.size - 1 downTo 0) {
      if (!allItems.contains(items.get(index))) removeRow(index)
    }

    dependencies.forEachIndexed { index, dependency ->
      fun current() = if (index <= items.size - 1) items.get(index) else null

      var atIndex = current()
      if (atIndex === dependency) {
        if (atIndex === changedDependency) {
          this.fireTableRowsUpdated(index, index)
        }
      }
      else {
        while (atIndex != null && atIndex !== dependency) {
          removeRow(index)
          atIndex = current()
        }
        insertRow(index, dependency)
      }
    }
  }
}
