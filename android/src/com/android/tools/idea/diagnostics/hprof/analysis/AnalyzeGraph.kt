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
import com.android.tools.idea.diagnostics.hprof.util.PartialProgressIndicator
import com.google.common.base.Stopwatch
import com.intellij.openapi.progress.ProgressIndicator
import gnu.trove.TIntArrayList
import gnu.trove.TIntHashSet
import gnu.trove.TLongArrayList

class AnalyzeGraph(private val nav: ObjectNavigator,
                   private val parentList: IntList,
                   private val nominatedClassNames: Set<String>,
                   private val includeMetaInfo: Boolean) {
  private fun ObjectNavigator.getParentId(): Long = getParentIdForObjectId(id)

  private fun setParentForObjectId(objectId: Long, parentId: Long) {
    parentList[objectId.toInt()] = parentId.toInt()
  }

  private fun getParentIdForObjectId(objectId: Long): Long {
    return parentList[objectId.toInt()].toLong()
  }

  fun prepareReport(progress: ProgressIndicator): String {
    val result = StringBuilder()
    val roots = nav.createRootsIterator()
    val nominatedInstances = HashMap<ClassDefinition, TIntHashSet>()
    nominatedClassNames.forEach {
      nominatedInstances[nav.classStore[it]] = TIntHashSet()
    }

    progress.text2 = "Collect all object roots"

    var toVisit = TIntArrayList()
    var toVisit2 = TIntArrayList()

    // Mark all class object as to be visited, set them as their own parents
    nav.classStore.forEachClass { classDefinition ->
      if (getParentIdForObjectId(classDefinition.id) == 0L) {
        toVisit.add(classDefinition.id.toInt())
        setParentForObjectId(classDefinition.id, classDefinition.id)
      }
      classDefinition.staticFields.forEach {
        val objectId = it.objectId
        if (objectId != 0L) {
          toVisit.add(objectId.toInt())
          setParentForObjectId(objectId, objectId)
        }
      }
      classDefinition.constantFields.forEach { objectId ->
        if (objectId != 0L) {
          toVisit.add(objectId.toInt())
          setParentForObjectId(objectId, objectId)
        }
      }
    }

    var leafCounter = 0
    // Mark all roots to be visited, set them as their own parents
    while (roots.hasNext()) {
      val rootObjectId = roots.next()
      nav.goTo(rootObjectId)
      if (nav.getParentId() == 0L) {
        toVisit.add(nav.id.toInt())
        setParentForObjectId(nav.id, nav.id)
      }
    }
    result.appendln("Roots count: ${toVisit.size()}")
    result.appendln("Classes count: ${nav.classStore.size()}")

    progress.text2 = "Walking object graph"
    val walkProgress = PartialProgressIndicator(progress, 0.1, 0.5)
    var visitedInstancesCount = 0
    val stopwatch = Stopwatch.createStarted()
    val references = TLongArrayList()
    while (!toVisit.isEmpty) {
      for (i in 0 until toVisit.size()) {
        val id = toVisit[i]
        nav.goTo(id.toLong())
        visitedInstancesCount++

        nav.copyReferencesTo(references)

        nominatedInstances[nav.getClass()]?.add(id)

        var isLeaf = true
        for (j in 0 until references.size()) {
          val it = references[j]
          if (it != 0L && getParentIdForObjectId(it) == 0L) {
            setParentForObjectId(it, id.toLong())
            toVisit2.add(it.toInt())
            isLeaf = false
          }
        }
        if (isLeaf) leafCounter++
      }
      walkProgress.fraction = (1.0 * visitedInstancesCount / nav.instanceCount)
      toVisit.resetQuick()
      val tmp = toVisit
      toVisit = toVisit2
      toVisit2 = tmp
    }
    if (includeMetaInfo) {
      result.appendln("Analysis completed! Visited instances: $visitedInstancesCount, time: $stopwatch")
      result.appendln("Leaves found: $leafCounter")
    }

    progress.text2 = "Calculating most frequent paths to roots"
    val generateReportProgress = PartialProgressIndicator(progress, 0.6, 0.4)

    var counter = 0
    nominatedInstances.forEach { classDefinition, set ->
      generateReportProgress.fraction = counter.toDouble() / nominatedInstances.size
      stopwatch.reset().start()
      result.appendln()
      result.appendln("CLASS: ${classDefinition.prettyName} (${set.size()} objects)")
      val referenceRegistry = GCRootPathsTree(parentList, nav)
      set.forEach { objectId ->
        referenceRegistry.registerObject(objectId)
        true
      }
      result.append(referenceRegistry.printTree(45, 25))
      if (includeMetaInfo) {
        result.appendln("Report for ${classDefinition.prettyName} created in $stopwatch")
      }
      counter++
    }
    generateReportProgress.fraction = 1.0
    return result.toString()
  }


}
