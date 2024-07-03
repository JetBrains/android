/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.structure

import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel

/**
 * @param components list of selected components
 * @param referenced list of selected referenced components
 */
data class SelectedComponent(val components: List<NlComponent>, val referenced: List<NlComponent>)

/** Returns the list of selected components from the tree. */
fun getSelectedComponents(tree: NlComponentTree, model: NlModel?): SelectedComponent {
  val selected = tree.selectionPaths
  val components = ArrayList<NlComponent>()
  val referenced = ArrayList<NlComponent>()

  selected?.forEach {
    if (it == null) {
      return@forEach
    }
    val last = it.lastPathComponent
    if (last is NlComponent) {
      components.add(last)
    } else if (last is String) {
      val component = findComponent(last, model)
      if (component != null) {
        referenced.add(component)
      }
    }
  }

  return SelectedComponent(components, referenced)
}

/** Find the component with the matching id. */
fun findComponent(id: String, model: NlModel?): NlComponent? {
  val optional =
    model?.treeReader?.flattenComponents()?.filter { it.id == id }?.findFirst() ?: return null
  return if (optional.isPresent) optional.get() else null
}
