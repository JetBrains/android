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
import com.android.tools.idea.diagnostics.hprof.util.HeapReportUtils.toPaddedShortStringAsSize
import com.android.tools.idea.diagnostics.hprof.util.HeapReportUtils.toShortStringAsCount
import com.android.tools.idea.diagnostics.hprof.util.HeapReportUtils.toShortStringAsSize
import com.android.tools.idea.diagnostics.hprof.util.TreeNode
import com.android.tools.idea.diagnostics.hprof.util.TreeVisualizer
import com.android.tools.idea.diagnostics.hprof.util.TruncatingPrintBuffer
import com.intellij.util.ExceptionUtil
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntCollection
import it.unimi.dsi.fastutil.ints.IntConsumer
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.longs.LongArrayList
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import java.util.ArrayDeque
import java.util.Stack

class AnalyzeDisposer(private val analysisContext: AnalysisContext) {

  private var prepareException: Exception? = null

  data class Grouping(val childClass: ClassDefinition,
                      val parentClass: ClassDefinition?,
                      val rootClass: ClassDefinition)

  class InstanceStats {
    private val parentIds = LongArrayList()
    private val rootIds = LongOpenHashSet()

    fun parentCount() = LongOpenHashSet(parentIds).count()
    fun rootCount() = rootIds.count()
    fun objectCount() = parentIds.count()

    fun registerObject(parentId: Long, rootId: Long) {
      parentIds.add(parentId)
      rootIds.add(rootId)
    }
  }

  data class DisposedDominatorReportEntry(val classDefinition: ClassDefinition, val count: Long, val size: Long)

  companion object {
    val TOP_REPORTED_CLASSES = setOf(
      "com.intellij.openapi.project.impl.ProjectImpl"
    )
  }

  fun prepareDisposerChildren() {
    prepareException = null
    val result = analysisContext.disposerParentToChildren
    result.clear()

    if (!analysisContext.classStore.containsClass("com.intellij.openapi.util.Disposer")) return

    try {
      val nav = analysisContext.navigator

      nav.goToStaticField("com.intellij.openapi.util.Disposer", "ourTree")
      val objectTreeClass = nav.getClass()
      analysisContext.disposerTreeObjectId = nav.id.toInt()

      when {
        // current implementation of Disposer (object->parent mapping)
        objectTreeClass.hasRefField("myObject2ParentNode") ->
          getDisposerParentToChildrenFromMyObject2ParentNode(result)

        // handles old Disposer implementations
        objectTreeClass.hasRefField("myObject2NodeMap") ->
          getDisposerParentToChildrenFromMyObject2NodeMapField(result)
      }

      // trim the result
      result.values.forEach(IntArrayList::trim)
      result.trim()
    } catch (ex : Exception) {
      prepareException = ex
    }
  }

  private fun getDisposerParentToChildrenFromMyObject2ParentNode(
    result: Int2ObjectOpenHashMap<IntArrayList>
  ) {
    val nav = analysisContext.navigator
    nav.goToStaticField("com.intellij.openapi.util.Disposer", "ourTree")
    nav.goToInstanceField(null, "myObject2ParentNode")
    val keyObjectId = nav.getInstanceFieldObjectId(null, "key")
    val valueObjectId = nav.getInstanceFieldObjectId(null, "value")
    nav.goTo(valueObjectId)
    val values = nav.getReferencesCopy()
    nav.goTo(keyObjectId)
    val keys = nav.getReferencesCopy()
    for (i in 0 until keys.count()) {
      val childId = keys.getLong(i)
      val parentObjectNodeObjectId = values.getLong(i)

      if (childId == 0L || parentObjectNodeObjectId == 0L)
        continue

      nav.goTo(parentObjectNodeObjectId)
      val parentId = nav.getInstanceFieldObjectId(null, "myObject")

      result.getOrPut(parentId.toInt()) { IntArrayList() }.add(childId.toInt())
    }
  }

  private fun getDisposerParentToChildrenFromMyObject2NodeMapField(
    result: Int2ObjectOpenHashMap<IntArrayList>
  ) {
    val nav = analysisContext.navigator

    goToArrayOfDisposableObjectNodes(nav)
    nav.getReferencesCopy().forEach {
      if (it == 0L) return@forEach

      nav.goTo(it)
      verifyClassIsObjectNode(nav.getClass())
      val objectNodeParentId = nav.getInstanceFieldObjectId(null, "myParent")
      val childId = nav.getInstanceFieldObjectId(null, "myObject")
      nav.goTo(objectNodeParentId)

      val parentId =
        if (nav.isNull())
          0L
        else
          nav.getInstanceFieldObjectId(null, "myObject")

      result.getOrPut(parentId.toInt()) { IntArrayList() }.add(childId.toInt())
    }
  }

  private fun goToArrayOfDisposableObjectNodes(nav: ObjectNavigator) {
    nav.goToStaticField("com.intellij.openapi.util.Disposer", "ourTree")

    analysisContext.disposerTreeObjectId = nav.id.toInt()

    verifyClassIsObjectTree(nav.getClass())

    if (nav.isNull()) {
      throw ObjectNavigator.NavigationException("Disposer.ourTree == null")
    }
    nav.goToInstanceField(null, "myObject2NodeMap")
    if (nav.getClass().name == "gnu.trove.THashMap") {
      nav.goToInstanceField("gnu.trove.THashMap", "_values")
    }
    else {
      nav.goToInstanceField("it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap", "value")
    }
    if (nav.isNull()) {
      throw ObjectNavigator.NavigationException("Collection of children is null")
    }
    if (!nav.getClass().isArray()) {
      throw ObjectNavigator.NavigationException("Invalid type of map values collection: ${nav.getClass().name}")
    }
  }

  private fun verifyClassIsObjectNode(clazzObjectTree: ClassDefinition) {
    if (clazzObjectTree.undecoratedName != "com.intellij.openapi.util.objectTree.ObjectNode" &&
        clazzObjectTree.undecoratedName != "com.intellij.openapi.util.ObjectNode") {
      throw ObjectNavigator.NavigationException("Wrong type, expected ObjectNode: ${clazzObjectTree.name}")
    }
  }

  private fun verifyClassIsObjectTree(clazzObjectTree: ClassDefinition) {
    if (clazzObjectTree.undecoratedName != "com.intellij.openapi.util.objectTree.ObjectTree" &&
        clazzObjectTree.undecoratedName != "com.intellij.openapi.util.ObjectTree") {
      throw ObjectNavigator.NavigationException("Wrong type, expected ObjectTree: ${clazzObjectTree.name}")
    }
  }

  private class DisposerNode(val className: String) : TreeNode {
    var count = 0
    var subtreeSize = 0
    var filteredSubtreeSize = 0
    val children = HashMap<String, DisposerNode>()

    fun equals(other: DisposerNode): Boolean = className == other.className

    override fun equals(other: Any?): Boolean = other != null && other is DisposerNode && equals(other)
    override fun hashCode() = className.hashCode()
    override fun description(): String = "[$subtreeSize] $count $className"
    override fun children(): Collection<TreeNode> = children.values.sortedByDescending { it.subtreeSize }

    fun addInstance() {
       count++
    }

    fun getChildForClassName(name: String): DisposerNode = children.getOrPut(name, { DisposerNode(name) })
  }

  private enum class SubTreeUpdaterOperation  { PROCESS_CHILDREN, UPDATE_SIZE }

  fun prepareDisposerTreeSummarySection(options: AnalysisConfig.DisposerTreeSummaryOptions): String = buildString {
    TruncatingPrintBuffer(options.headLimit, 0, this::appendln).use { buffer ->
      if (!analysisContext.classStore.containsClass("com.intellij.openapi.util.Disposer")) {
        return@buildString
      }

      prepareException?.let {
        buffer.println(ExceptionUtil.getThrowableText(it))
        return@buildString
      }

      val nav = analysisContext.navigator
      val objectId2Children = analysisContext.disposerParentToChildren

      // Build a map: object -> list of its disposable children
      // Collect top-level objects (i.e. have no parent)
      val topLevelObjectIds = IntOpenHashSet(analysisContext.disposerParentToChildren.keys)
      analysisContext.disposerParentToChildren.values.forEach { set -> topLevelObjectIds.removeAll(set) }
      try {
       val rootNode = DisposerNode("<root>")

        data class StackObject(val node: DisposerNode, val childrenIds: IntCollection)

        val stack = Stack<StackObject>()
        stack.push(StackObject(rootNode, IntArrayList(topLevelObjectIds)))

        while (!stack.empty()) {
          val (currentNode, childrenIds) = stack.pop()

          val nodeToChildren = HashMap<DisposerNode, IntArrayList>()
          childrenIds.forEach(IntConsumer {
            val childNode: DisposerNode
            if (it == 0) {
              childNode = rootNode
            } else {
              val childClassName = nav.getClassForObjectId(it.toLong()).name
              childNode = currentNode.getChildForClassName(childClassName)
            }
            childNode.addInstance()
            val tIntArrayList = objectId2Children[it]
            if (tIntArrayList != null) {
              nodeToChildren.getOrPut(childNode) { IntArrayList() }.addAll(tIntArrayList)
            }
          })
          nodeToChildren.forEach { (node, children) -> stack.push(StackObject(node, children)) }
        }

        // Update subtree size
        data class SubtreeSizeUpdateStackObject(val node: DisposerNode, val operation: SubTreeUpdaterOperation)

        val nodeStack = Stack<SubtreeSizeUpdateStackObject>()
        nodeStack.push(SubtreeSizeUpdateStackObject(rootNode, SubTreeUpdaterOperation.PROCESS_CHILDREN))

        while (!nodeStack.isEmpty()) {
          val (currentNode, operation) = nodeStack.pop()
          if (operation == SubTreeUpdaterOperation.PROCESS_CHILDREN) {
            currentNode.subtreeSize = currentNode.count
            currentNode.filteredSubtreeSize = currentNode.count
            nodeStack.push(SubtreeSizeUpdateStackObject(currentNode, SubTreeUpdaterOperation.UPDATE_SIZE))
            currentNode.children.values.forEach {
              nodeStack.push(SubtreeSizeUpdateStackObject(it, SubTreeUpdaterOperation.PROCESS_CHILDREN))
            }
          }
          else {
            assert(operation == SubTreeUpdaterOperation.UPDATE_SIZE)

            currentNode.children.values.forEach { currentNode.subtreeSize += it.subtreeSize }
            currentNode.children.entries.removeIf { it.value.filteredSubtreeSize < options.nodeCutoff }
            currentNode.children.values.forEach { currentNode.filteredSubtreeSize += it.subtreeSize }
          }
        }
        val visualizer = TreeVisualizer()
        buffer.println("Cutoff: ${options.nodeCutoff}")
        buffer.println("Count of disposable objects: ${rootNode.subtreeSize}")
        buffer.println()
        rootNode.children().forEach {
          visualizer.visualizeTree(it, buffer, analysisContext.config.disposerOptions.disposerTreeSummaryOptions)
          buffer.println()
        }
      }
      catch (ex: Exception) {
        buffer.println(ExceptionUtil.getThrowableText(ex))
      }
    }
  }

  fun computeDisposedObjectsIDs() {
    val disposedObjectsIDs = analysisContext.disposedObjectsIDs
    disposedObjectsIDs.clear()

    if (prepareException != null) {
      return
    }

    try {
      val nav = analysisContext.navigator

      if (!nav.classStore.containsClass("com.intellij.openapi.util.Disposer")) {
        return
      }

      nav.goToStaticField("com.intellij.openapi.util.Disposer", "ourTree")
      if (nav.isNull()) {
        throw ObjectNavigator.NavigationException("ourTree is null")
      }
      verifyClassIsObjectTree(nav.getClass())
      nav.goToInstanceField(null, "myDisposedObjects")
      nav.goToInstanceField("com.intellij.util.containers.WeakHashMap", "myMap")

      nav.goToInstanceField("com.intellij.util.containers.RefHashMap\$MyMap", "key")
      val weakKeyClass = nav.classStore.getClassIfExists("com.intellij.util.containers.WeakHashMap\$WeakKey")

      nav.getReferencesCopy().forEach {
        if (it == 0L) {
          return@forEach
        }
        nav.goTo(it, ObjectNavigator.ReferenceResolution.ALL_REFERENCES)
        if (nav.getClass() != weakKeyClass) {
          return@forEach
        }
        nav.goToInstanceField("com.intellij.util.containers.WeakHashMap\$WeakKey", "referent")
        if (nav.id == 0L) return@forEach

        val leakId = nav.id.toInt()
        disposedObjectsIDs.add(leakId)
      }
    } catch (navEx : ObjectNavigator.NavigationException) {
      prepareException = navEx
    }
  }

  fun prepareDisposedObjectsSection(): String = buildString {
    val leakedInstancesByClass = HashMap<ClassDefinition, LongArrayList>()
    val countByClass = Object2IntOpenHashMap<ClassDefinition>()
    var totalCount = 0

    val nav = analysisContext.navigator
    val strongReferencedDisposedObjectsIDs = analysisContext.strongReferencedDisposedObjectsIDs
    val disposerOptions = analysisContext.config.disposerOptions

    strongReferencedDisposedObjectsIDs.forEach {
      nav.goTo(it.toLong(), ObjectNavigator.ReferenceResolution.ALL_REFERENCES)

      val leakClass = nav.getClass()
      val leakId = nav.id

      leakedInstancesByClass.getOrPut(leakClass) { LongArrayList() }.add(leakId)

      countByClass.put(leakClass, countByClass.getInt(leakClass) + 1)
      totalCount++
    }

    val entries = countByClass.object2IntEntrySet()

    if (disposerOptions.includeDisposedObjectsSummary) {
      // Print counts of disposed-but-strong-referenced objects
      TruncatingPrintBuffer(100, 0, this::appendln).use { buffer ->
        buffer.println("Count of disposed-but-strong-referenced objects: $totalCount")
        entries
          .sortedBy { it.key.name }
          .sortedByDescending { it.intValue }
          .partition { TOP_REPORTED_CLASSES.contains(it.key.name) }
          .let { it.first + it.second }
          .forEach { entry ->
            buffer.println("  ${entry.intValue} ${entry.key.prettyName}")
          }
      }
      appendln()
    }

    val disposedTree = GCRootPathsTree(analysisContext, AnalysisConfig.TreeDisplayOptions.all(), null)
    for (disposedObjectsID in strongReferencedDisposedObjectsIDs.intIterator()) {
      disposedTree.registerObject(disposedObjectsID)
    }

    val disposedDominatorNodesByClass = disposedTree.getDisposedDominatorNodes()
    var allDominatorsCount = 0L
    var allDominatorsSubgraphSize = 0L
    val disposedDominatorClassSizeList = mutableListOf<DisposedDominatorReportEntry>()
    disposedDominatorNodesByClass.forEach { (classDefinition, nodeList) ->
      var dominatorClassSubgraphSize = 0L
      var dominatorClassInstanceCount = 0L
      nodeList.forEach {
        dominatorClassInstanceCount += it.instances.size()
        dominatorClassSubgraphSize += it.totalSizeInDwords.toLong() * 4
      }
      allDominatorsCount += dominatorClassInstanceCount
      allDominatorsSubgraphSize += dominatorClassSubgraphSize
      disposedDominatorClassSizeList.add(
        DisposedDominatorReportEntry(classDefinition, dominatorClassInstanceCount, dominatorClassSubgraphSize))
    }

    if (disposerOptions.includeDisposedObjectsSummary) {
      TruncatingPrintBuffer(30, 0, this::appendln).use { buffer ->
        buffer.println("Disposed-but-strong-referenced dominator object count: $allDominatorsCount")
        buffer.println(
          "Disposed-but-strong-referenced dominator sub-graph size: ${toShortStringAsSize(allDominatorsSubgraphSize)}")
        disposedDominatorClassSizeList
          .sortedBy { it.classDefinition.name }
          .sortedByDescending { it.size }
          .forEach { entry ->
            buffer.println(
              "  ${toPaddedShortStringAsSize(entry.size)} - ${toShortStringAsCount(entry.count)} ${entry.classDefinition.name}")
          }
      }
      appendln()
    }

    if (disposerOptions.includeDisposedObjectsDetails) {
      val instancesListInOrder = getInstancesListInPriorityOrder(
        leakedInstancesByClass,
        disposedDominatorClassSizeList
      )

      TruncatingPrintBuffer(700, 0, this::appendln).use { buffer ->
        instancesListInOrder
          .forEach { instances ->
            // Pick first instance to get class name
            nav.goTo(instances.getLong(0))
            buffer.println(
              "Disposed but still strong-referenced objects: ${instances.count()} ${nav.getClass().prettyName}, most common paths from GC-roots:")
            val gcRootPathsTree = GCRootPathsTree(analysisContext, disposerOptions.disposedObjectsDetailsTreeDisplayOptions, nav.getClass())
            instances.forEach { leakId ->
              gcRootPathsTree.registerObject(leakId.toInt())
            }
            gcRootPathsTree.printTree().lineSequence().forEach(buffer::println)
          }
      }
    }
  }

  private fun isStrongReferenced(id: Int): Boolean {
    val refIndexList = analysisContext.refIndexList
    val parentList = analysisContext.parentList
    if (id == 0) return false
    var currentId = id
    do {
      if (refIndexList[currentId] == RefIndexUtil.SOFT_REFERENCE ||
          refIndexList[currentId] == RefIndexUtil.WEAK_REFERENCE) {
        return false
      }
      val next = parentList[currentId]
      if (next == currentId) {
        // At the root with no weak/soft refs
        return true
      }
      currentId = next
    } while (currentId != 0)
    return true
  }


  private fun getInstancesListInPriorityOrder(
    classToLeakedIdsList: HashMap<ClassDefinition, LongArrayList>,
    disposedDominatorReportEntries: List<DisposedDominatorReportEntry>): List<LongArrayList> {
    val result = mutableListOf<LongArrayList>()

    // Make a mutable copy. When a class instances are added to the result list, remove the class entry from the copy.
    val classToLeakedIdsListCopy = HashMap(classToLeakedIdsList)

    // First, all top classes
    TOP_REPORTED_CLASSES.forEach { topClassName ->
      classToLeakedIdsListCopy
        .filterKeys { it.name == topClassName }
        .forEach { (classDefinition, list) ->
          result.add(list)
          classToLeakedIdsListCopy.remove(classDefinition)
        }
    }

    // Alternate between class with most instances leaked and class with most bytes leaked

    // Prepare instance count class list by priority
    val classOrderByInstanceCount = ArrayDeque(
      classToLeakedIdsListCopy
        .entries
        .sortedBy { it.key.name }
        .sortedByDescending { it.value.count() }
        .map { it.key }
    )

    // Prepare dominator bytes count class list by priority
    val classOrderByByteCount = ArrayDeque(
      disposedDominatorReportEntries
        .sortedBy { it.classDefinition.name }
        .sortedByDescending { it.size }
        .map { it.classDefinition }
    )

    // zip, but ignore duplicates
    var nextByInstanceCount = true
    while (!classOrderByInstanceCount.isEmpty() ||
           !classOrderByByteCount.isEmpty()) {
      val nextCollection = if (nextByInstanceCount) classOrderByInstanceCount else classOrderByByteCount
      if (!nextCollection.isEmpty()) {
        val nextClass = nextCollection.removeFirst()
        val list = classToLeakedIdsListCopy.remove(nextClass) ?: continue
        result.add(list)
      }
      nextByInstanceCount = !nextByInstanceCount
    }
    return result
  }

  // This has to run after graph traverse
  fun computeStrongReferencedDisposedObjectsIDs() {
    val strongReferencedDisposedObjectsIDs = analysisContext.strongReferencedDisposedObjectsIDs
    strongReferencedDisposedObjectsIDs.clear()
    analysisContext.disposedObjectsIDs.forEach { id ->
      if (isStrongReferenced(id))
        strongReferencedDisposedObjectsIDs.add(id)
    }
  }

}