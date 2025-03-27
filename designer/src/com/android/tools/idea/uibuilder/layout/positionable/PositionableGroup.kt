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
package com.android.tools.idea.uibuilder.layout.positionable

import com.android.tools.idea.common.layout.positionable.PositionableContent

/**
 * [PositionableGroup] organizes [PositionableContent] into groups.
 *
 * @param content list of content
 * @param header an optional header preceding the content.
 */
class PositionableGroup(
  val content: List<PositionableContent>,
  val header: PositionableContent? = null,
) {
  /** If [PositionableGroup] has a [header]. */
  val hasHeader = header != null
}

val NO_GROUP_TRANSFORM: (Collection<PositionableContent>) -> List<PositionableGroup> = {
  listOf(PositionableGroup(it.toList()))
}

/** Group [PositionableContent] into [PositionableGroup]. */
val GROUP_BY_BASE_COMPONENT: (Collection<PositionableContent>) -> List<PositionableGroup> =
  { contents ->
    val groups = mutableMapOf<Any?, MutableList<PositionableContent>>()
    for (content in contents) {
      groups.getOrPut(content.organizationGroup) { mutableListOf() }.add(content)
    }

    groups.values
      .fold(Pair(mutableListOf<PositionableGroup>(), mutableListOf<PositionableContent>())) {
        temp,
        next ->
        val hasHeader = next.any { it is HeaderPositionableContent }
        // If next is not in its own group - keep it in temp.second
        if (!hasHeader) {
          temp.second.addAll(next)
        }

        // Temp.second contains all consecutive previews without its own group.
        // If next is not in a group or if it is the last element, group all collected
        // previews as one group
        if (hasHeader || groups.values.last() == next) {
          if (temp.second.isNotEmpty()) {
            temp.first.add(
              PositionableGroup(
                temp.second.filter { it !is HeaderPositionableContent },
                temp.second.filterIsInstance<HeaderPositionableContent>().singleOrNull(),
              )
            )
            temp.second.clear()
          }
        }

        // If next has its own group - it will have its own PositionableGroup
        if (hasHeader) {
          temp.first.add(
            PositionableGroup(
              next.filter { it !is HeaderPositionableContent },
              next.filterIsInstance<HeaderPositionableContent>().singleOrNull(),
            )
          )
        }

        temp
      }
      .first
  }
