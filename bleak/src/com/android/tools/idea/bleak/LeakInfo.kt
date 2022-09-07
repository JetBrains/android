/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.bleak

import java.util.IdentityHashMap

class LeakInfo(val g: HeapGraph, val leakRoot: Node, val prevLeakRoot: Node) {
  val leaktrace: Leaktrace = leakRoot.getLeaktrace()
  val childrenObjects = leakRoot.childObjects.uniqueByIdentity()
  val prevChildren = prevLeakRoot.children
  val prevChildrenObjects = prevLeakRoot.childObjects.uniqueByIdentity()
  val addedChildrenObjects = childrenObjects.filter { c -> prevChildrenObjects.all { it !== c } }
  val addedChildren = leakRoot.children.filter { c -> prevChildren.all { it.obj !== c.obj }}
  val retainedByNewChildren = mutableListOf<Node>()
  val retainedByAllChildren = mutableListOf<Node>()

  override fun toString() = buildString {
    appendln(leaktrace)
    appendln(" ${leakRoot.degree} child nodes (+${leakRoot.degree - prevLeakRoot.degree}) [${childrenObjects.size} distinct child objects (+${addedChildrenObjects.size})]. New child nodes: ${addedChildren.size}")
    addedChildren.take(20).forEach {
        appendln("   Added: ${it.objString()}")
    }
    // if many objects are added, print summary information about their most common classes
    if (addedChildren.size > 20) {
      appendln("   ...")
      appendln("  Most common classes of added objects: ")
      mostCommonClassesOf(addedChildren, 5).forEach {
        appendln("    ${it.second} ${it.first.name}")
      }
    }

    // print information about objects retained by the added children:
    if (retainedByAllChildren.isEmpty()) {
      appendln("\nDominator information omitted: timeout exceeded.")
      return@buildString
    }
    appendln("\nRetained by new children: ${retainedByNewChildren.size} objects (${retainedByNewChildren.map { it.getApproximateSize() }.sum()} bytes)")
    mostCommonClassesOf(retainedByNewChildren, 50).forEach {
      appendln("    ${it.second} ${it.first.name}")
    }

    // print information about objects retained by all of the children. Sometimes severity is considerably underestimated by
    // just looking at the children added in the last iteration, since often the same heavy data structures (Projects, etc.) are held
    // by all of the leaked objects (and so are not retained by the last-iteration children alone, but are retained by all of the children
    // in aggregate). However, this may also overestimate the severity, since there can be many other objects in the array unrelated to the
    // actual leak in question.
    appendln("\nRetained by all children: ${retainedByAllChildren.size} objects (${retainedByAllChildren.map { it.getApproximateSize() }.sum()} bytes)")
    mostCommonClassesOf(retainedByAllChildren, 50).forEach {
      appendln("    ${it.second} ${it.first.name}")
    }
  }

  private fun Node.objString() = type.name + ": " + try {
    val s = obj.toString()
    if (s.length > 80) s.take(77) + "..." else s
  } catch (e: NullPointerException) {
    "[NPE in toString]"
  }

  private fun Collection<Any>.uniqueByIdentity(): List<Any> {
    val set = IdentityHashMap<Any, Any>()
    forEach { set.put(it, it) }
    return set.keys.toList()
  }

  private fun classCounts(nodes: Collection<Node>): Map<Class<*>, Int> {
    val classCounts = mutableMapOf<Class<*>, Int>()
    nodes.forEach { classCounts.merge(it.type, 1) { currentCount, _ -> currentCount + 1 } }
    return classCounts
  }

  private fun mostCommonClassesOf(nodes: Collection<Node>, maxResults: Int): List<Pair<Class<*>, Int>> {
    return classCounts(nodes).toList().sortedByDescending { it.second }.take(maxResults)
  }

}