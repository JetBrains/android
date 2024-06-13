/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.pipeline.appinspection.compose

import com.android.tools.idea.layoutinspector.properties.PropertyType
import com.android.tools.property.panel.api.LinkPropertyItem
import com.android.tools.property.ptable.PTable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys

/**
 * [ParameterItem] used to load more elements of a [PropertyType.ITERABLE].
 *
 * When creating the sub elements of a [PropertyType.ITERABLE] this item should be added as the last
 * item if there is a [ParameterGroupItem.reference] present.
 */
class ShowMoreElementsItem(val array: ParameterGroupItem) :
  ParameterItem(
    "...",
    PropertyType.SHOW_MORE_LINK,
    null,
    array.section,
    array.viewId,
    array.lookup,
    array.rootId,
    array.lastRealChildReferenceIndex + 1,
  ),
  LinkPropertyItem {
  override val link =
    object : AnAction("Show More") {
      override fun actionPerformed(event: AnActionEvent) {
        val reference = array.reference ?: return
        val startIndex = array.lastRealChildReferenceIndex + 1
        val maxElements = array.children.size - 1
        lookup.resolve(rootId, reference, startIndex, maxElements) { cachedParameter, modification
          ->
          val table = findTable(event)
          if (cachedParameter != null && table != null) {
            modification?.let { table.updateGroupItems(cachedParameter, it) }
            if (array !== cachedParameter) {
              val arrayModification = array.applyReplacement(cachedParameter)
              arrayModification?.let { table.updateGroupItems(array, it) }
            }
          }
        }
      }
    }

  override fun clone(): ParameterItem = ShowMoreElementsItem(array)

  private fun findTable(event: AnActionEvent): PTable? {
    val component = event.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT) ?: return null
    return generateSequence(component) { it.parent }.firstOrNull { it is PTable } as? PTable
  }
}
