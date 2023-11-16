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

data class PsiRange(val startOffset: Int, val endOffset: Int) {
  fun containsOffset(offset: Int): Boolean = offset in startOffset..endOffset
}

class ComposeGroupTree(groups: List<FunctionKeyMeta>) {
  private val parentMap = buildParentMap(groups)
  private val childMap = buildChildMap(parentMap)

  val roots = parentMap.filter { it.value == null }.keys
  val groups = parentMap.keys
  fun getParent(group: FunctionKeyMeta): FunctionKeyMeta? = parentMap[group]
  fun getChildren(group: FunctionKeyMeta): Set<FunctionKeyMeta>? = childMap[group]

  fun getGroupIds(methodOffsets: PsiRange): Set<Int> {
    val groupIds = mutableSetOf<Int>()
    roots.map { findBestGroups(methodOffsets, it) }.forEach { groupIds.addAll(it) }
    return groupIds
  }

  private fun findBestGroups(methodOffsets: PsiRange, root: FunctionKeyMeta): Set<Int> {
    val groupsToInvalidate = mutableSetOf<Int>()
    val queue = ArrayDeque<FunctionKeyMeta>()
    queue.addLast(root)

    // If the modified method's lines overlap a root group, ensure we at least invalidate that group. This handles situations like the
    // following, where an annotation (or some other code) precedes the start of a group on the same line:
    //
    //             v @Composable group start offset
    // @Composable fun MyFun() { ...
    // ^ method start offset
    if (methodOffsets.containsOffset(root.startOffset) || methodOffsets.containsOffset(root.endOffset)) {
      groupsToInvalidate.add(root.key)
    }

    while (queue.isNotEmpty()) {
      val group = queue.removeFirst()

      // If the modified text range is fully contained in this group:
      if (group.contains(methodOffsets.startOffset, methodOffsets.endOffset)) {
        // Add this group to the set to be invalidated
        groupsToInvalidate.add(group.key)
        // Remove the parent from the set to invalidate, since a narrower range is better
        parentMap[group]?.let { groupsToInvalidate.remove(it.key) }
        // Add this group's children, if any, to the queue to check
        getChildren(group)?.forEach { queue.addLast(it) }
      }
    }

    return groupsToInvalidate
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