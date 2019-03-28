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
import gnu.trove.TObjectIntHashMap

class AnalyzeDisposer(private val nav: ObjectNavigator) {

  data class Grouping(val childClass: ClassDefinition,
                      val parentClass: ClassDefinition?,
                      val rootClass: ClassDefinition)

  class InstanceStats {
    private val parentIds = TLongArrayList()
    private val rootIds = TLongHashSet()

    fun parentCount() = TLongHashSet(parentIds.toNativeArray()).size()
    fun rootCount() = rootIds.size()
    fun objectCount() = parentIds.size()

    fun registerObject(parentId: Long, rootId: Long) {
      parentIds.add(parentId)
      rootIds.add(rootId)
    }
  }

  companion object {
    val topReportedClasses = hashSetOf(
      "com.intellij.openapi.project.impl.ProjectImpl"
    )
  }

  fun createDisposerTreeReport(): String {
    if (!nav.classStore.containsClass("com.intellij.openapi.util.Disposer")) {
      return ""
    }

    val result = StringBuilder()

    nav.goToStaticField("com.intellij.openapi.util.Disposer", "ourTree")
    assert(!nav.isNull())
    nav.goToInstanceField("com.intellij.openapi.util.objectTree.ObjectTree", "myObject2NodeMap")
    nav.goToInstanceField("gnu.trove.THashMap", "_values")

    val groupingToObjectStats = HashMap<Grouping, InstanceStats>()
    val maxTreeDepth = 200
    val tooDeepObjectClasses = HashSet<ClassDefinition>()
    nav.getReferencesCopy().forEach {
      if (it == 0L) return@forEach true

      nav.goTo(it)
      val objectNodeParentId = nav.getInstanceFieldObjectId("com.intellij.openapi.util.objectTree.ObjectNode", "myParent")
      val objectNodeObjectId = nav.getInstanceFieldObjectId("com.intellij.openapi.util.objectTree.ObjectNode", "myObject")
      nav.goTo(objectNodeParentId)

      val parentId =
        if (nav.isNull())
          0L
        else
          nav.getInstanceFieldObjectId("com.intellij.openapi.util.objectTree.ObjectNode", "myObject")

      val parentClass =
        if (parentId == 0L)
          null
        else {
          nav.goTo(parentId)
          nav.getClass()
        }

      nav.goTo(objectNodeObjectId)
      val objectClass = nav.getClass()

      val rootClass: ClassDefinition
      val rootId: Long

      if (parentId == 0L) {
        rootClass = objectClass
        rootId = objectNodeObjectId
      }
      else {
        var rootObjectNodeId = objectNodeParentId
        var rootObjectId: Long
        var iterationCount = 0
        do {
          nav.goTo(rootObjectNodeId)
          rootObjectNodeId = nav.getInstanceFieldObjectId("com.intellij.openapi.util.objectTree.ObjectNode", "myParent")
          rootObjectId = nav.getInstanceFieldObjectId("com.intellij.openapi.util.objectTree.ObjectNode", "myObject")
          iterationCount++
        }
        while (rootObjectNodeId != 0L && iterationCount < maxTreeDepth)

        if (iterationCount >= maxTreeDepth) {
          tooDeepObjectClasses.add(objectClass)
          rootId = parentId
          rootClass = parentClass!!
        }
        else {
          nav.goTo(rootObjectId)
          rootId = rootObjectId
          rootClass = nav.getClass()
        }
      }

      groupingToObjectStats
        .getOrPut(Grouping(objectClass, parentClass, rootClass)) { InstanceStats() }
        .registerObject(parentId, rootId)
      true
    }

    TruncatingPrintBuffer(400, 0, result::appendln).use { buffer ->
      buffer.println("Disposer tree stats:")

      groupingToObjectStats
        .entries
        .sortedByDescending { it.value.objectCount() }
        .groupBy { it.key.rootClass }
        .forEach { (rootClass, entries) ->
          buffer.println("Root: ${rootClass.name}")
          TruncatingPrintBuffer(100, 0, buffer::println).use { buffer ->
            entries.forEach { (mapping, groupedObjects) ->
              printDisposerTreeReportLine(buffer, mapping, groupedObjects)
            }
          }
          buffer.println()
        }
    }

    if (tooDeepObjectClasses.size > 0) {
      result.appendln("Skipped analysis of objects too deep in disposer tree:")
      tooDeepObjectClasses.forEach {
        result.appendln(" * ${nav.classStore.getShortPrettyNameForClass(it)}")
      }
    }

    return result.toString()
  }

  private fun printDisposerTreeReportLine(buffer: TruncatingPrintBuffer,
                                          mapping: Grouping,
                                          groupedObjects: InstanceStats) {
    val sourceClass = mapping.childClass
    val parentClass = mapping.parentClass
    val rootClass = mapping.rootClass
    val objectCount = groupedObjects.objectCount()
    val parentCount = groupedObjects.parentCount()

    // Ignore 1-1 mappings
    if (parentClass != null && objectCount == parentCount)
      return

    val parentString: String
    if (parentClass == null) {
      parentString = "(no parent)"
    }
    else {
      val parentClassName = nav.classStore.getShortPrettyNameForClass(parentClass)
      val rootCount = groupedObjects.rootCount()
      if (rootClass != parentClass || rootCount != parentCount) {
        val rootClassName = nav.classStore.getShortPrettyNameForClass(rootClass)
        parentString = "<-- $parentCount $parentClassName [...] $rootCount $rootClassName"
      }
      else
        parentString = "<-- $parentCount $parentClassName"
    }

    val sourceClassName = nav.classStore.getShortPrettyNameForClass(sourceClass)
    buffer.println("  ${String.format("%6d", objectCount)} $sourceClassName $parentString")
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
    val leakedByClass = HashMap<Pair<ClassDefinition, ClassDefinition>, TLongArrayList>()
    val countByClass = TObjectIntHashMap<ClassDefinition>()
    var totalCount = 0
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

      // If there is no parent, then the object does not have a strong-reference path to GC root
      if (nav.isNull()) return@forEach true

      val parentClass = nav.getClass()
      leakedByClass.getOrPut(Pair(leakClass, parentClass)) { TLongArrayList() }.add(leakId)

      countByClass.put(leakClass, countByClass[leakClass] + 1)
      totalCount++

      true
    }

    // Convert TObjectIntHashMap to list of entries
    data class TObjectIntMapEntry<T>(val key: T, val value: Int)
    val entries = mutableListOf<TObjectIntMapEntry<ClassDefinition>>()
    countByClass.forEachEntry { key, value ->
      entries.add(TObjectIntMapEntry(key, value))
      true
    }

    // Print counts of disposed-but-strong-referenced objects
    TruncatingPrintBuffer(100, 0, result::appendln).use { buffer ->
      buffer.println("Count of disposed-but-strong-referenced objects: $totalCount")
      entries
        .sortedByDescending { it.value }
        .partition { topReportedClasses.contains(it.key.name) }
        .let { it.first + it.second }
        .forEach { entry ->
          buffer.println("  ${entry.value} ${entry.key.prettyName}")
        }
    }
    result.appendln()

    TruncatingPrintBuffer(700, 0, result::appendln).use { buffer ->
      leakedByClass.entries
        .sortedByDescending { it.value.size() }
        .partition { topReportedClasses.contains(it.key.first.name) }
        .let { it.first + it.second }
        .map { it.value }
        .forEach { instances ->
          nav.goTo(instances[0])
          buffer.println(
            "Disposed but still strong-referenced objects: ${instances.size()} ${nav.getClass().prettyName}, most common paths from GC-roots:")
          val gcRootPathsTree = GCRootPathsTree(parentList, nav)
          instances.forEach { leakId ->
            gcRootPathsTree.registerObject(leakId.toInt())
            true
          }
          gcRootPathsTree.printTree(70, 5).split("\n").forEach(buffer::println)
        }
    }
    return result.toString()
  }

}