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
package com.android.tools.idea.diagnostics.hprof.analysis

import com.android.tools.idea.diagnostics.hprof.classstore.ClassDefinition
import com.android.tools.idea.diagnostics.hprof.navigator.ObjectNavigator
import com.android.tools.idea.diagnostics.hprof.util.IntList
import com.android.tools.idea.diagnostics.hprof.util.TruncatingPrintBuffer
import gnu.trove.TLongArrayList
import gnu.trove.TLongHashSet

class AnalyzeDisposer(private val nav: ObjectNavigator) {

  fun createDisposerTreeReport(): String {
    if (!nav.classStore.containsClass("com.intellij.openapi.util.Disposer")) {
      return ""
    }

    val result = StringBuilder()

    nav.goToStaticField("com.intellij.openapi.util.Disposer", "ourTree")
    assert(!nav.isNull())
    nav.goToInstanceField("com.intellij.openapi.util.objectTree.ObjectTree", "myObject2NodeMap")
    nav.goToInstanceField("gnu.trove.THashMap", "_values")

    val classPairToObjectIds = HashMap<Pair<ClassDefinition, ClassDefinition?>, TLongArrayList>()
    nav.getReferencesCopy().forEach {
      if (it == 0L) return@forEach true
      nav.goTo(it)
      val myParentObjectNodeId = nav.getInstanceFieldObjectId("com.intellij.openapi.util.objectTree.ObjectNode", "myParent")
      val myObjectId = nav.getInstanceFieldObjectId("com.intellij.openapi.util.objectTree.ObjectNode", "myObject")
      nav.goTo(myParentObjectNodeId)
      val myParentId = if (nav.isNull()) 0L
      else run {
        nav.getInstanceFieldObjectId("com.intellij.openapi.util.objectTree.ObjectNode", "myObject")
      }
      val myParentClass = if (myParentId == 0L) null
      else run {
        nav.goTo(myParentId)
        nav.getClass()
      }
      nav.goTo(myObjectId)
      val myObjectClass = nav.getClass()
      classPairToObjectIds.getOrPut(Pair(myObjectClass, myParentClass)) { TLongArrayList() }.add(myParentId)
      true
    }

    TruncatingPrintBuffer(200, 0, result::appendln).use { buffer ->
      buffer.println("Disposer tree stats:")

      classPairToObjectIds.entries.sortedByDescending { it.value.size() }.forEach { (pair, list) ->
        val set = TLongHashSet(list.toNativeArray())

        val sourceClass = pair.first
        val parentClass = pair.second
        val parentString: String
        if (parentClass == null) {
          parentString = "(no parent)"
        }
        else {
          val parentClassName = nav.classStore.getShortPrettyNameForClass(parentClass)
          parentString = "<-- ${set.size()} ${parentClassName}"
        }
        val sourceClassName = nav.classStore.getShortPrettyNameForClass(sourceClass)
        buffer.println("${String.format("%6d", list.size())} $sourceClassName $parentString")
      }
    }
    return result.toString()
  }

  fun analyzeDisposedObjects(parentList: IntList): String {
    if (!nav.classStore.containsClass("com.intellij.openapi.util.Disposer")) {
      return ""
    }

    val result = StringBuilder()
    nav.goToStaticField("com.intellij.openapi.util.Disposer", "ourTree")
    assert(!nav.isNull())
    nav.goToInstanceField("com.intellij.openapi.util.objectTree.ObjectTree", "myDisposedObjects")
    nav.goToInstanceField("com.intellij.util.containers.WeakHashMap", "myMap")
    nav.goToInstanceField("com.intellij.util.containers.RefHashMap\$MyMap", "_set")
    val weakKeyClass = nav.classStore["com.intellij.util.containers.WeakHashMap\$WeakKey"]
    val leakedByClass = HashMap<Pair<ClassDefinition, ClassDefinition?>, TLongArrayList>()
    nav.getReferencesCopy().forEach {
      if (it == 0L) {
        return@forEach true
      }
      nav.goTo(it, true)
      if (nav.getClass() != weakKeyClass) {
        return@forEach true
      }
      nav.goToInstanceField("com.intellij.util.containers.WeakHashMap\$WeakKey", "referent")
      if (nav.id == 0L) return@forEach true

      val leakClass = nav.getClass()
      val leakId = nav.id
      nav.goTo(parentList[nav.id.toInt()].toLong())
      val parentClass = if (nav.isNull()) null else nav.getClass()
      leakedByClass.getOrPut(Pair(leakClass, parentClass)) { TLongArrayList() }.add(leakId)
      true
    }
    TruncatingPrintBuffer(500, 0, result::appendln).use { buffer ->
      leakedByClass.values.sortedByDescending { it.size() }.forEach { instances ->
        nav.goTo(instances[0])
        buffer.println(
          "Disposed but still strong-referenced objects: ${instances.size()} ${nav.getClass().prettyName}, most common paths from GC-roots:")
        val gcRootPathsTree = GCRootPathsTree(parentList, nav)
        instances.forEach { leakId ->
          gcRootPathsTree.registerObject(leakId.toInt())
          true
        }
        gcRootPathsTree.printTree(50, 5).split("\n").forEach(buffer::println)
      }
    }
    return result.toString()
  }

}