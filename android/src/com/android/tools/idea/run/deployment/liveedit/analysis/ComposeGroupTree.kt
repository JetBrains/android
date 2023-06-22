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
package com.android.tools.idea.run.deployment.liveedit.analysis

class ComposeGroupTree(groups: List<FunctionKeyMeta>) {
  private val parentMap = buildParentMap(groups)
  private val childMap = buildChildMap(parentMap)

  val roots = parentMap.filter { it.value == null }.keys
  fun getParent(group: FunctionKeyMeta): FunctionKeyMeta? = parentMap[group]
  fun getChildren(group: FunctionKeyMeta): Set<FunctionKeyMeta>? = childMap[group]

  fun getGroupIds(startOffset: Int, endOffset: Int): List<Int> {
    return roots.mapNotNull { findBestGroup(startOffset, endOffset, it) }.map { it.key }
  }

  private fun findBestGroup(startOffset: Int, endOffset: Int, group: FunctionKeyMeta): FunctionKeyMeta? {
    var best: FunctionKeyMeta? = null

    val groups = ArrayDeque<FunctionKeyMeta>()
    groups.addLast(group)

    while (groups.isNotEmpty()) {
      val cur = groups.removeFirst()
      if (cur.contains(startOffset, endOffset)) {
        getChildren(cur)?.forEach { groups.addLast(it) }
        best = cur
      }
    }

    return best
  }
}

private fun buildChildMap(parentMap: Map<FunctionKeyMeta, FunctionKeyMeta?>): Map<FunctionKeyMeta, Set<FunctionKeyMeta>> {
  val map = parentMap.toMutableMap()

  val forest = mutableMapOf<FunctionKeyMeta, MutableSet<FunctionKeyMeta>>()
  map.filter { it.value == null }.forEach {
    forest[it.key] = mutableSetOf()
    map.remove(it.key)
  }

  while (map.isNotEmpty()) {
    map.filter { it.value in forest }.forEach {
      val group = it.key
      val parent = it.value
      forest[parent]?.add(group)
      forest[group] = mutableSetOf()
      map.remove(it.key)
    }
  }

  return forest
}

private fun buildParentMap(groups: List<FunctionKeyMeta>): Map<FunctionKeyMeta, FunctionKeyMeta?> {
  val parentMap = mutableMapOf<FunctionKeyMeta, FunctionKeyMeta?>()
  for (group in groups) {
    parentMap[group] = null
  }
  for (group in parentMap.keys) {
    for (other in parentMap.keys) {
      if (group == other) continue
      if (group.contains(other)) {
        val curParent = parentMap[other]
        if (curParent == null || curParent.contains(group)) {
          parentMap[other] = group
        }
      }
    }
  }
  return parentMap
}