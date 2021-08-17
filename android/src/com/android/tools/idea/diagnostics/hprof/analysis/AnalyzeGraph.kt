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
import com.android.tools.idea.diagnostics.hprof.histogram.Histogram
import com.android.tools.idea.diagnostics.hprof.navigator.ObjectNavigator
import com.android.tools.idea.diagnostics.hprof.util.HeapReportUtils.sectionHeader
import com.android.tools.idea.diagnostics.hprof.util.HeapReportUtils.toPaddedShortStringAsCount
import com.android.tools.idea.diagnostics.hprof.util.HeapReportUtils.toPaddedShortStringAsSize
import com.android.tools.idea.diagnostics.hprof.util.HeapReportUtils.toShortStringAsCount
import com.android.tools.idea.diagnostics.hprof.util.HeapReportUtils.toShortStringAsSize
import com.android.tools.idea.diagnostics.hprof.util.PartialProgressIndicator
import com.android.tools.idea.diagnostics.hprof.util.TruncatingPrintBuffer
import com.android.tools.idea.diagnostics.hprof.visitors.HistogramVisitor
import com.google.common.base.Stopwatch
import com.intellij.openapi.progress.ProgressIndicator
import gnu.trove.TIntArrayList
import gnu.trove.TIntHashSet
import gnu.trove.TIntIntHashMap
import gnu.trove.TLongArrayList

class AnalyzeGraph(private val analysisContext: AnalysisContext) {

  private val unreachableDisposableObjects = TIntArrayList()
  private var strongRefHistogram: Histogram? = null
  private var softWeakRefHistogram: Histogram? = null
  private var traverseReport: String? = null

  private val parentList = analysisContext.parentList

  private fun setParentForObjectId(objectId: Long, parentId: Long) {
    parentList[objectId.toInt()] = parentId.toInt()
  }

  private fun getParentIdForObjectId(objectId: Long): Long {
    return parentList[objectId.toInt()].toLong()
  }

  private val nominatedInstances = HashMap<ClassDefinition, TIntHashSet>()

  fun analyze(progress: ProgressIndicator): String = buildString {
    val includePerClassSection = analysisContext.config.perClassOptions.classNames.isNotEmpty()

    val traverseProgress =
      if (includePerClassSection) PartialProgressIndicator(progress, 0.0, 0.5) else progress

    val analyzeDisposer = AnalyzeDisposer(analysisContext)
    analyzeDisposer.prepareDisposerChildren()

    traverseInstanceGraph(traverseProgress)

    analyzeDisposer.computeDisposedObjectsIDs()

    // Histogram section
    val histogramOptions = analysisContext.config.histogramOptions
    if (histogramOptions.includeByCount || histogramOptions.includeBySize) {
      appendln(sectionHeader("Histogram"))
      append(prepareHistogramSection())
    }

    if (histogramOptions.includeSummary) {
      appendln(sectionHeader("Heap summary"))
      append(traverseReport)
    }

    // Per-class section
    if (includePerClassSection) {
      val perClassProgress = PartialProgressIndicator(progress, 0.5, 0.5)
      appendln(sectionHeader("Instances of each nominated class"))
      append(preparePerClassSection(perClassProgress))
    }

    // Disposer sections
    if (config.disposerOptions.includeDisposerTree) {
      appendln(sectionHeader("Disposer tree"))
      append(analyzeDisposer.prepareDisposerTreeSection())
    }
    if (config.disposerOptions.includeDisposerTreeSummary) {
      appendln(sectionHeader("Disposer tree summary"))
      append(analyzeDisposer.prepareDisposerTreeSummarySection(config.disposerOptions.disposerTreeSummaryOptions))
    }
    if (config.disposerOptions.includeDisposedObjectsSummary || config.disposerOptions.includeDisposedObjectsDetails) {
      appendln(sectionHeader("Disposed objects"))
      append(analyzeDisposer.prepareDisposedObjectsSection())
    }
  }

  private fun preparePerClassSection(progress: PartialProgressIndicator): String = buildString {
    val histogram = analysisContext.histogram
    val perClassOptions = analysisContext.config.perClassOptions

    if (perClassOptions.includeClassList) {
      appendln("Nominated classes:")
      perClassOptions.classNames.forEach { name ->
        val (classDefinition, totalInstances, totalBytes) =
          histogram.entries.find { entry -> entry.classDefinition.name == name } ?: return@forEach
        val prettyName = classDefinition.prettyName
        appendln(" --> [${toShortStringAsCount(totalInstances)}/${toShortStringAsSize(totalBytes)}] " + prettyName)
      }
      appendln()
    }

    val nav = analysisContext.navigator
    var counter = 0
    val nominatedClassNames = config.perClassOptions.classNames
    val stopwatch = Stopwatch.createUnstarted()
    nominatedClassNames.forEach { className ->
      val classDefinition = nav.classStore[className]
      val set = nominatedInstances[classDefinition]!!
      progress.fraction = counter.toDouble() / nominatedInstances.size
      progress.text2 = "Processing: ${set.size()} ${classDefinition.prettyName}"
      stopwatch.reset().start()
      appendln("CLASS: ${classDefinition.prettyName} (${set.size()} objects)")
      val referenceRegistry = GCRootPathsTree(analysisContext, perClassOptions.treeDisplayOptions, classDefinition)
      set.forEach { objectId ->
        referenceRegistry.registerObject(objectId)
        true
      }
      set.clear()
      append(referenceRegistry.printTree())
      if (config.metaInfoOptions.include) {
        appendln("Report for ${classDefinition.prettyName} created in $stopwatch")
      }
      appendln()
      counter++
    }
    progress.fraction = 1.0
  }

  private fun prepareHistogramSection(): String = buildString {
    val strongRefHistogram = getAndClearStrongRefHistogram()
    val softWeakRefHistogram = getAndClearSoftWeakHistogram()

    val histogram = analysisContext.histogram
    val histogramOptions = analysisContext.config.histogramOptions

    append(
      Histogram.prepareMergedHistogramReport(histogram, "All",
                                             strongRefHistogram, "Strong-ref", histogramOptions))

    val unreachableObjectsCount = histogram.instanceCount - strongRefHistogram.instanceCount - softWeakRefHistogram.instanceCount
    val unreachableObjectsSize = histogram.bytesCount - strongRefHistogram.bytesCount - softWeakRefHistogram.bytesCount
    appendln("Unreachable objects: ${toPaddedShortStringAsCount(
      unreachableObjectsCount)}  ${toPaddedShortStringAsSize(unreachableObjectsSize)}")
  }

  enum class WalkGraphPhase {
    StrongReferencesNonLocalVariables,
    StrongReferencesLocalVariables,
    DisposerTree,
    SoftReferences,
    WeakReferences,
    CleanerFinalizerReferences,
    Finished
  }

  private val config = analysisContext.config

  private fun traverseInstanceGraph(progress: ProgressIndicator)
  {
    val traverseOptions = config.traverseOptions
    val onlyStrongReferences = traverseOptions.onlyStrongReferences
    val includeDisposerRelationships = traverseOptions.includeDisposerRelationships
    val includeFieldInformation = traverseOptions.includeFieldInformation

    val nav = analysisContext.navigator
    val classStore = analysisContext.classStore
    val sizesList = analysisContext.sizesList
    val visitedList = analysisContext.visitedList
    val refIndexList = analysisContext.refIndexList

    val roots = nav.createRootsIterator()
    nominatedInstances.clear()

    val nominatedClassNames = config.perClassOptions.classNames
    nominatedClassNames.forEach {
      nominatedInstances[classStore[it]] = TIntHashSet()
    }

    progress.text2 = "Collect all object roots"

    var toVisit = TIntArrayList()
    var toVisit2 = TIntArrayList()

    val rootsSet = TIntHashSet()
    val frameRootsSet = TIntHashSet()

    // Mark all roots to be visited, set them as their own parents
    while (roots.hasNext()) {
      val rootObject = roots.next()
      val rootObjectId = rootObject.id.toInt()
      if (rootObject.reason.javaFrame) {
        frameRootsSet.add(rootObjectId)
      }
      else {
        addIdToSetIfOrphan(rootsSet, rootObjectId)
      }
    }

    // Mark all class object as to be visited, set them as their own parents
    classStore.forEachClass { classDefinition ->
      addIdToSetIfOrphan(rootsSet, classDefinition.id.toInt())
      classDefinition.staticFields.forEach { staticField ->
        addIdToSetIfOrphan(rootsSet, staticField.objectId.toInt())
      }
      classDefinition.constantFields.forEach { objectId ->
        addIdToSetIfOrphan(rootsSet, objectId.toInt())
      }
    }

    toVisit.add(rootsSet.toArray())
    rootsSet.clear()
    rootsSet.compact()

    var leafCounter = 0

    progress.text2 = "Traversing instance graph"

    val strongRefHistogramEntries = HashMap<ClassDefinition, HistogramVisitor.InternalHistogramEntry>()
    val reachableNonStrongHistogramEntries = HashMap<ClassDefinition, HistogramVisitor.InternalHistogramEntry>()
    val softReferenceIdToParentMap = TIntIntHashMap()
    val weakReferenceIdToParentMap = TIntIntHashMap()

    var visitedInstancesCount = 0
    val stopwatch = Stopwatch.createStarted()
    val references = TLongArrayList()

    var visitedCount = 0
    var strongRefVisitedCount = 0
    var softWeakVisitedCount = 0

    var finalizableBytes = 0L
    var softBytes = 0L
    var weakBytes = 0L

    var phase = WalkGraphPhase.StrongReferencesNonLocalVariables // initial state

    val cleanerObjects = TIntArrayList()
    val sunMiscCleanerClass = classStore.getClassIfExists("sun.misc.Cleaner")
    val finalizerClass = classStore.getClassIfExists("java.lang.ref.Finalizer")

    while (!toVisit.isEmpty) {
      for (i in 0 until toVisit.size()) {
        val id = toVisit[i]

        // Disposer.ourTree is only visited during DisposerTree phase to give opportunity for
        if (includeDisposerRelationships &&
            id == analysisContext.diposerTreeObjectId &&
            phase < WalkGraphPhase.DisposerTree) {
          continue
        }

        nav.goTo(id.toLong(), ObjectNavigator.ReferenceResolution.ALL_REFERENCES)
        val currentObjectClass = nav.getClass()

        if ((currentObjectClass == sunMiscCleanerClass || currentObjectClass == finalizerClass)
            && phase < WalkGraphPhase.CleanerFinalizerReferences) {
          if (!onlyStrongReferences) {
            // Postpone visiting sun.misc.Cleaner and java.lang.ref.Finalizer objects until later phase
            cleanerObjects.add(id)
          }
          continue
        }

        visitedInstancesCount++
        nominatedInstances[currentObjectClass]?.add(id)

        var isLeaf = true
        nav.copyReferencesTo(references)
        val currentObjectIsArray = currentObjectClass.isArray()

        // Postpone any soft references encountered before the phase that handles them
        if (phase < WalkGraphPhase.SoftReferences && nav.getSoftReferenceId() != 0L) {
          if (!onlyStrongReferences) {
            softReferenceIdToParentMap.put(nav.getSoftReferenceId().toInt(), id)
          }
          references[nav.getSoftWeakReferenceIndex()] = 0L
        }

        // Postpone any weak references encountered before the phase that handles them
        if (phase < WalkGraphPhase.WeakReferences && nav.getWeakReferenceId() != 0L) {
          if (!onlyStrongReferences) {
            weakReferenceIdToParentMap.put(nav.getWeakReferenceId().toInt(), id)
          }
          references[nav.getSoftWeakReferenceIndex()] = 0L
        }

        val size = nav.getObjectSize()
        val nonDisposerReferences = references.size()

        // Inline children from the disposer tree
        if (includeDisposerRelationships && analysisContext.disposerParentToChildren.contains(id)) {
          if (phase >= WalkGraphPhase.DisposerTree) {
            unreachableDisposableObjects.add(id)
          }
          analysisContext.disposerParentToChildren[id].forEach {
            references.add(it.toLong())
            true
          }
        }

        for (j in 0 until references.size()) {
          val referenceId = references[j].toInt()

          if (addIdToListAndSetParentIfOrphan(toVisit2, referenceId, id)) {
            if (includeFieldInformation) {
              refIndexList[referenceId] = when {
                currentObjectIsArray -> RefIndexUtil.ARRAY_ELEMENT
                j >= nonDisposerReferences -> RefIndexUtil.DISPOSER_CHILD
                j < RefIndexUtil.MAX_FIELD_INDEX -> j + 1
                else -> RefIndexUtil.FIELD_OMITTED // Too many reference fields
              }
            }
            isLeaf = false
          }
        }

        // Ordered list of visited nodes. Parent is always before its children.
        visitedList[visitedCount++] = id

        // Store size of the object. Later pass will update the size by adding sizes of children
        // Size in DWORDs to support graph sizes up to 10GB.
        var sizeDivBy4 = (size + 3) / 4
        if (sizeDivBy4 == 0) sizeDivBy4 = 1
        sizesList[id] = sizeDivBy4

        // Update histogram (separately for Strong-references and other reachable objects)
        var histogramEntries: HashMap<ClassDefinition, HistogramVisitor.InternalHistogramEntry>
        if (phase == WalkGraphPhase.StrongReferencesNonLocalVariables || phase == WalkGraphPhase.StrongReferencesLocalVariables ||
          phase == WalkGraphPhase.DisposerTree) {
          histogramEntries = strongRefHistogramEntries
          if (isLeaf) {
            leafCounter++
          }
          strongRefVisitedCount++
        }
        else {
          histogramEntries = reachableNonStrongHistogramEntries
          when (phase) {
            WalkGraphPhase.CleanerFinalizerReferences -> finalizableBytes += size
            WalkGraphPhase.SoftReferences -> softBytes += size
            else -> {
              assert(phase == WalkGraphPhase.WeakReferences)
              weakBytes += size
            }
          }
          softWeakVisitedCount++
        }
        histogramEntries.getOrPut(currentObjectClass) {
          HistogramVisitor.InternalHistogramEntry(currentObjectClass)
        }.addInstance(size.toLong())
      }

      // Update process
      progress.fraction = (1.0 * visitedInstancesCount / nav.instanceCount)

      // Prepare next level of objects for processing
      toVisit.resetQuick()
      val tmp = toVisit
      toVisit = toVisit2
      toVisit2 = tmp

      // If no more object to visit at this phase, transition to the next
      while (toVisit.size() == 0 && phase != WalkGraphPhase.Finished) {
        // Next state
        phase = WalkGraphPhase.values()[phase.ordinal + 1]

        when (phase) {
          WalkGraphPhase.StrongReferencesLocalVariables ->
            frameRootsSet.forEach { id ->
              addIdToListAndSetParentIfOrphan(toVisit, id, id)
              true
            }
          WalkGraphPhase.CleanerFinalizerReferences -> {
            toVisit.add(cleanerObjects.toNativeArray())
            cleanerObjects.clear()
          }
          WalkGraphPhase.SoftReferences -> {
            softReferenceIdToParentMap.forEachEntry { softId, parentId ->
              if (addIdToListAndSetParentIfOrphan(toVisit, softId, parentId)) {
                refIndexList[softId] = RefIndexUtil.SOFT_REFERENCE
              }

              true
            }
            // No need to store the list anymore
            softReferenceIdToParentMap.clear()
            softReferenceIdToParentMap.compact()
          }
          WalkGraphPhase.WeakReferences -> {
            weakReferenceIdToParentMap.forEachEntry { weakId, parentId ->
              if (addIdToListAndSetParentIfOrphan(toVisit, weakId, parentId)) {
                refIndexList[weakId] = RefIndexUtil.WEAK_REFERENCE
              }
              true
            }
            // No need to store the list anymore
            weakReferenceIdToParentMap.clear()
            weakReferenceIdToParentMap.compact()
          }
          WalkGraphPhase.DisposerTree -> {
            if (analysisContext.diposerTreeObjectId != 0) {
              toVisit.add(analysisContext.diposerTreeObjectId)
            }
          }
          else -> Unit // No work for other state transitions
        }
      }
    }

    // Assert that any postponed objects have been handled
    assert(cleanerObjects.isEmpty)
    assert(softReferenceIdToParentMap.isEmpty)
    assert(weakReferenceIdToParentMap.isEmpty)

    // Histograms are accessible publicly after traversal is complete
    strongRefHistogram = Histogram(
      strongRefHistogramEntries
        .values
        .map { it.asHistogramEntry() }
        .sortedByDescending { it.totalInstances },
      strongRefVisitedCount.toLong())

    softWeakRefHistogram = Histogram(
      reachableNonStrongHistogramEntries
        .values
        .map { it.asHistogramEntry() }
        .sortedByDescending { it.totalInstances },
      softWeakVisitedCount.toLong())

    // Update size field in non-leaves to reflect the size of the whole subtree. Right after traversal
    // the size field is initialized to the size of the given object.
    // Traverses objects in the reverse order, so a child is always visited before its parent.
    // This assures size field is correctly set for a given before its added to the size field of the parent.
    val stopwatchUpdateSizes = Stopwatch.createStarted()
    var index = visitedCount - 1
    while (index >= 0) {
      val id = visitedList[index]
      val parentId = parentList[id]
      if (id != parentId) {
        sizesList[parentId] += sizesList[id]
      }
      index--
    }
    stopwatchUpdateSizes.stop()

    traverseReport = buildString {
      if (config.metaInfoOptions.include) {
        appendln("Analysis completed! Visited instances: $visitedInstancesCount, time: $stopwatch")
        appendln("Update sizes time: $stopwatchUpdateSizes")
        appendln("Leaves found: $leafCounter")
      }

      appendln("Class count: ${classStore.size()}")

      // Adds summary of object count by reachability
      appendln("Finalizable size: ${toShortStringAsSize(finalizableBytes)}")
      appendln("Soft-reachable size: ${toShortStringAsSize(softBytes)}")
      appendln("Weak-reachable size: ${toShortStringAsSize(weakBytes)}")
      appendln("Reachable only from disposer tree: ${unreachableDisposableObjects.size()}")
      TruncatingPrintBuffer(10, 0, this::appendln).use { buffer ->
        val unreachableChildren = TIntHashSet()
        unreachableDisposableObjects.forEach { id ->
          analysisContext.disposerParentToChildren[id]?.let { unreachableChildren.addAll(it.toNativeArray()) }
          true
        }
        unreachableDisposableObjects.forEach { id ->
          if (unreachableChildren.contains(id)) {
            return@forEach true
          }
          buffer.println(" * ${nav.getClassForObjectId(id.toLong()).name} (${toShortStringAsSize(sizesList[id].toLong() * 4)})")
          true
        }
      }
    }
  }

  /**
   * Adds object id to the list, only if the object does not have a parent object. Object will
   * also have a parent assigned.
   * For root objects, parentId should point back to the object making the object its own parent.
   * For other cases parentId can be provided.
   *
   * @return true if object was added to the list.
   */
  private fun addIdToListAndSetParentIfOrphan(list: TIntArrayList, id: Int, parentId: Int = id): Boolean {
    if (id != 0 && getParentIdForObjectId(id.toLong()) == 0L) {
      setParentForObjectId(id.toLong(), parentId.toLong())
      list.add(id)
      return true
    }
    return false
  }

  /**
   * Adds object id to the set, only if the object does not have a parent object and is not yet in the set.
   * Object will also have a parent assigned.
   * For root objects, parentId should point back to the object making the object its own parent.
   * For other cases parentId can be provided.
   *
   * @return true if object was added to the set.
   */
  private fun addIdToSetIfOrphan(set: TIntHashSet, id: Int, parentId: Int = id): Boolean {
    if (id != 0 && getParentIdForObjectId(id.toLong()) == 0L && set.add(id)) {
      setParentForObjectId(id.toLong(), parentId.toLong())
      return true
    }
    return false
  }

  private fun getAndClearStrongRefHistogram(): Histogram {
    val result = strongRefHistogram
    strongRefHistogram = null
    return result ?: throw IllegalStateException("Graph not analyzed.")
  }

  private fun getAndClearSoftWeakHistogram(): Histogram {
    val result = softWeakRefHistogram
    softWeakRefHistogram = null
    return result ?: throw IllegalStateException("Graph not analyzed.")
  }
}
