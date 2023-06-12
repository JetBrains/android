/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.profilers.memory.adapters.classifiers

import com.android.tools.adtui.model.filter.Filter
import com.android.tools.profilers.CachedFunction
import com.android.tools.profilers.memory.adapters.InstanceObject
import com.android.tools.profilers.memory.adapters.MemoryObject
import com.android.tools.profilers.memory.adapters.instancefilters.CaptureObjectInstanceFilter
import java.util.Collections
import java.util.IdentityHashMap
import java.util.Objects
import java.util.stream.Stream
import kotlin.math.min
import kotlin.streams.toList

/**
 * A general base class for classifying/filtering objects into categories.
 *
 * Supports lazy-loading the ClassifierSet's name in case it is expensive.
 */
abstract class ClassifierSet(supplyName: () -> String) : MemoryObject {
  private sealed class State {
    sealed class Coalesced(
      // The set of instances that make up our baseline snapshot (e.g. live objects at the left of a selection range).
      val snapshotInstances: MutableSet<InstanceObject>,
      // The set of instances that have delta events (e.g. delta allocations/deallocations within a selection range).
      // Note that instances here can also appear in the set of snapshot instances (e.g. when a instance is allocated before the selection
      // and deallocation within the selection).
      val deltaInstances: MutableSet<InstanceObject>): State() {
      var retainedSize: Long = -1 // cached retained size. `-1` means stale
      class Leaf(snapshotInstances: MutableSet<InstanceObject>, deltaInstances: MutableSet<InstanceObject>)
        : Coalesced(snapshotInstances, deltaInstances)
      class Delayed(val makeClassifier: () -> Classifier,
                    snapshotInstances: MutableSet<InstanceObject>,
                    deltaInstances: MutableSet<InstanceObject>)
        : Coalesced(snapshotInstances, deltaInstances)
    }
    class Partitioned(val classifier: Classifier): State()

    fun retracted(makeClassifier: () -> Classifier): Coalesced = when (this) {
      is Coalesced -> this
      is Partitioned -> classifier.allClassifierSets.let { subs ->
        fun instances(extract: (ClassifierSet) -> Stream<InstanceObject>) =
          LinkedHashSet<InstanceObject>().apply { addAll(subs.stream().flatMap(extract).toList())}
        Coalesced.Delayed(makeClassifier, instances { it.snapshotInstanceStream }, instances { it.deltaInstanceStream })
      }
    }

    fun forced(): State /* Leaf | Partitioned */ = when (this) {
      is Partitioned, is Coalesced.Leaf -> this
      is Coalesced.Delayed -> when (val c = makeClassifier()) {
        is Classifier.Id -> Coalesced.Leaf(snapshotInstances, deltaInstances)
        is Classifier.Join<*> -> Partitioned(c.also { it.partition(snapshotInstances, deltaInstances) })
      }
    }
  }

  constructor(name: String): this({ name })
  private val _name by lazy(supplyName)

  private var state: State = initState()

  var totalObjectSetCount = 0
    private set
  var filteredObjectSetCount = 0
    private set
  private var snapshotObjectCount = 0
  var deltaAllocationCount = 0
    private set
  var deltaDeallocationCount = 0
    private set
  var allocationSize = 0L
    private set
  var deallocationSize = 0L
    private set
  var totalNativeSize = 0L
    private set
  var totalShallowSize = 0L
    private set
  open val totalRetainedSize: Long get() = when (val s = state) {
    is State.Coalesced -> when (s.retainedSize) {
      -1L -> {
        // b(234174316) for an arbitrary classifier, neither summing over the classes
        // nor the instances is guaranteed to give the tighter estimation, so we take their min.
        // In practice, this problem shows up when we support classstacks in the heap dump,
        // where a callstack may only have some of the instances of the classes.
        val maxRetainedSizeByClass =
          (s.snapshotInstances.asSequence() + s.deltaInstances.asSequence())
            .map { it.classEntry }
            .distinct()
            .fold(0L) { sum, entry -> when {
              entry.retainedSize == -1L || sum == Long.MAX_VALUE -> Long.MAX_VALUE
              else -> sum + entry.retainedSize
            }}
        val maxRetainedSizeByInstances =
          (s.snapshotInstances.asSequence() + s.deltaInstances.asSequence())
            .sumOf { it.retainedSize.validOrZero() }
        val maxRetainedSize = min(maxRetainedSizeByClass, maxRetainedSizeByInstances)
        s.retainedSize = maxRetainedSize
        maxRetainedSize
      }
      else -> s.retainedSize
    }
    is State.Partitioned -> s.classifier.allClassifierSets.sumOf { it.totalRetainedSize }
  }
  var deltaShallowSize = 0L
    private set
  private var instancesWithStackInfoCount = 0

  // Number of ClassifierSet that match the filter.
  var filterMatchCount = 0
    protected set
  private val instanceFilterMatchCounter = CachedFunction(IdentityHashMap(), ::countInstanceFilterMatch)

  // TODO: `myIsMatched` should be `true` initially, as "no filter" means "trivially matched".
  //        But at the moment that would break one test in `HeapSetNodeHRendererTest`
  var isMatched = false
    protected set

  // We need to apply filter to ClassifierSet again after any updates (insertion, deletion etc.)
  @JvmField
  protected var needsRefiltering = false

  val isEmpty: Boolean get() = snapshotObjectCount == 0 && deltaAllocationCount == 0 && deltaDeallocationCount == 0
  val totalObjectCount: Int get() = snapshotObjectCount + deltaAllocationCount - deltaDeallocationCount
  val totalRemainingSize: Long get() = allocationSize - deallocationSize
  val instancesCount: Int get() = instancesStream.count().toInt()

  /**
   * Gets a stream of all instances (including all descendants) in this ClassifierSet.
   */
  val instancesStream: Stream<InstanceObject>
    get() = getStreamOf({true}) { Stream.concat(it.snapshotInstances.stream(), it.deltaInstances.stream()).distinct() }

  /**
   * Return the stream of instance objects that contribute to the delta.
   * Note that there can be duplicated entries as [.getSnapshotInstanceStream].
   */
  protected val deltaInstanceStream: Stream<InstanceObject> get() = getStreamOf({true}) { it.deltaInstances.stream() }

  /**
   * Return the stream of instance objects that contribute to the baseline snapshot.
   * Note that there can duplicated entries as [.getDeltaInstanceStream].
   */
  protected val snapshotInstanceStream: Stream<InstanceObject> get() = getStreamOf({true}) { it.snapshotInstances.stream() }
  val filterMatches: Stream<InstanceObject> get() =
    getStreamOf({it.isMatched}) { Stream.concat(it.snapshotInstances.stream(), it.deltaInstances.stream()) }

  val childrenClassifierSets: List<ClassifierSet> get() = when (val s = ensurePartitioned()) {
    is State.Coalesced -> listOf()
    is State.Partitioned -> s.classifier.filteredClassifierSets
  }

  @JvmField
  protected var myIsFiltered = false

  val isFiltered: Boolean get() = isEmpty || myIsFiltered
  open val stringForMatching: String get() = _name
  override fun getName() = _name

  private fun invalidateRetainedSizeCache() = when (val s = state) {
    is State.Coalesced -> s.retainedSize = -1
    else -> {}
  }
  private fun ensurePartitioned() = state.forced().also { state = it }
  protected fun coalesce() {
    state = state.retracted(::createSubClassifier)
  }

  fun getInstanceFilterMatchCount(filter: CaptureObjectInstanceFilter): Int = instanceFilterMatchCounter.invoke(filter)

  /**
   * Add an instance to the baseline snapshot and update the accounting of the "total" values.
   * Note that instances at the baseline must be an allocation event.
   */
  fun addSnapshotInstanceObject(instanceObject: InstanceObject) =
    changeSnapshotInstanceObject(instanceObject, SetOperation.ADD)

  /**
   * Remove an instance from the baseline snapshot and update the accounting of the "total" values.
   */
  fun removeSnapshotInstanceObject(instanceObject: InstanceObject) =
    changeSnapshotInstanceObject(instanceObject, SetOperation.REMOVE)

  private fun changeSnapshotInstanceObject(instanceObject: InstanceObject, op: SetOperation): Boolean {
    val changed: Boolean
    when (val s = state) {
      is State.Partitioned -> {
        val classifierSet = s.classifier.getClassifierSet(instanceObject, op == SetOperation.ADD)
        changed = classifierSet != null && classifierSet.changeSnapshotInstanceObject(instanceObject, op)
      }
      is State.Coalesced -> {
        changed = op == SetOperation.ADD != instanceObject in s.snapshotInstances
        op.invoke(s.snapshotInstances, instanceObject)
      }
    }
    if (changed) {
      snapshotObjectCount += op.countChange
      totalNativeSize += op.countChange * instanceObject.nativeSize.validOrZero()
      totalShallowSize += op.countChange * instanceObject.shallowSize.toLong().validOrZero()
      invalidateRetainedSizeCache()
      if (!instanceObject.isCallStackEmpty) {
        instancesWithStackInfoCount += op.countChange
      }
      instanceFilterMatchCounter.invalidate()
      needsRefiltering = true
    }
    return changed
  }

  // Add delta alloc information into the ClassifierSet
  // Return true if the set did not contain the instance prior to invocation
  fun addDeltaInstanceObject(instanceObject: InstanceObject): Boolean =
    changeDeltaInstanceInformation(instanceObject, true, SetOperation.ADD).instanceChanged

  // Add delta dealloc information into the ClassifierSet
  // Return true if the set did not contain the instance prior to invocation
  fun freeDeltaInstanceObject(instanceObject: InstanceObject): Boolean =
    changeDeltaInstanceInformation(instanceObject, false, SetOperation.ADD).instanceChanged

  // Remove delta instance alloc information
  // Remove instance when it neither has alloc nor dealloc information
  // Return true if the instance is removed
  fun removeAddedDeltaInstanceObject(instanceObject: InstanceObject): Boolean =
    changeDeltaInstanceInformation(instanceObject, true, SetOperation.REMOVE).instanceChanged

  // Remove delta instance dealloc information
  // Remove instance when it neither has alloc nor dealloc information
  // Return true if the instance is removed
  fun removeFreedDeltaInstanceObject(instanceObject: InstanceObject): Boolean =
    changeDeltaInstanceInformation(instanceObject, false, SetOperation.REMOVE).instanceChanged

  private enum class DeltaChange(val countsChanged: Boolean, val instanceChanged: Boolean) {
    UNCHANGED(false, false),
    INSTANCE_MODIFIED(true, false),
    INSTANCE_ADDED_OR_REMOVED(true, true);
  }

  private fun changeDeltaInstanceInformation(instanceObject: InstanceObject, isAllocation: Boolean, op: SetOperation): DeltaChange {
    val change = state.let { s ->
      when {
        s is State.Partitioned -> {
          val classifierSet = s.classifier.getClassifierSet(instanceObject, op == SetOperation.ADD)
          classifierSet?.changeDeltaInstanceInformation(instanceObject, isAllocation, op) ?: DeltaChange.UNCHANGED
        }
        s is State.Coalesced &&
        (op == SetOperation.ADD || !instanceObject.hasTimeData()) &&
        // `contains` is more expensive, so deferred to after above test fails.
        // This line is run often enough to make a difference.
        op == SetOperation.ADD != s.deltaInstances.contains(instanceObject) -> {
          op.invoke(s.deltaInstances, instanceObject)
          DeltaChange.INSTANCE_ADDED_OR_REMOVED
        }
        else -> DeltaChange.INSTANCE_MODIFIED
      }
    }

    if (change.countsChanged) {
      if (isAllocation) {
        deltaAllocationCount += op.countChange * instanceObject.instanceCount
        allocationSize += (op.countChange * instanceObject.shallowSize).toLong()
      } else {
        deltaDeallocationCount += op.countChange * instanceObject.instanceCount
        deallocationSize += (op.countChange * instanceObject.shallowSize).toLong()
      }
      val factor = op.countChange * if (isAllocation) 1 else -1
      val deltaNativeSize = factor * instanceObject.nativeSize.validOrZero()
      val deltaShallowSize = factor * instanceObject.shallowSize.toLong().validOrZero()
      val deltaRetainedSize = factor * instanceObject.retainedSize.validOrZero()
      totalNativeSize += deltaNativeSize
      this.deltaShallowSize += deltaShallowSize
      totalShallowSize += deltaShallowSize
      invalidateRetainedSizeCache()
      if (change.instanceChanged && !instanceObject.isCallStackEmpty) {
        instancesWithStackInfoCount += op.countChange
        needsRefiltering = true
      }
      if (change.instanceChanged) {
        instanceFilterMatchCounter.invalidate()
      }
    }
    return change
  }

  fun clearClassifierSets() {
    state = initState().forced()
    snapshotObjectCount = 0
    deltaAllocationCount = 0
    deltaDeallocationCount = 0
    allocationSize = 0
    deallocationSize = 0
    totalShallowSize = 0
    totalNativeSize = 0
    invalidateRetainedSizeCache()
    deltaShallowSize = 0
    instancesWithStackInfoCount = 0
    totalObjectSetCount = 0
    filteredObjectSetCount = 0
    filterMatchCount = 0
  }

  /**
   * Collect stream of instances from nodes satisfying |condition|
   */
  private fun getStreamOf(condition: (ClassifierSet) -> Boolean, extract: (State.Coalesced) -> Stream<InstanceObject>): Stream<InstanceObject> =
    state.let { s -> when {
      !condition(this) -> Stream.empty()
      s is State.Coalesced -> extract(s)
      else -> (s as State.Partitioned).classifier.allClassifierSets.stream().flatMap { it.getStreamOf(condition, extract) }
    } }

  fun hasStackInfo(): Boolean = instancesWithStackInfoCount > 0

  /**
   * O(N) search through all descendant ClassifierSet.
   * Note - calling this method would cause the full ClassifierSet tree to be built if it has not been done already, and all InstanceObjects
   * would be partitioned into their corresponding leaf ClassifierSet.
   *
   * @return the set that contains the `target`, or null otherwise.
   */
  fun findContainingClassifierSet(target: InstanceObject): ClassifierSet? = state.let { s -> when {
    s is State.Coalesced && (target in s.snapshotInstances || target in s.deltaInstances) -> when (ensurePartitioned()) {
      is State.Coalesced -> this
      is State.Partitioned -> childrenClassifierSets.firstNonNullResult { it.findContainingClassifierSet(target) }
    }
    s is State.Partitioned -> childrenClassifierSets.firstNonNullResult { it.findContainingClassifierSet(target) }
    else -> null
  }}

  /**
   * O(N) search through all descendant ClassifierSet.
   * Note - calling this method would cause the full ClassifierSet tree to be built if it has not been done already, and all InstanceObjects
   * would be partitioned into their corresponding leaf ClassifierSet.
   *
   * @return the set that satisfies `pred`, or null otherwise.
   */
  fun findClassifierSet(test: (ClassifierSet) -> Boolean): ClassifierSet? = when {
    test(this) -> this
    else -> childrenClassifierSets.firstNonNullResult { it.findClassifierSet(test) }
  }

  private fun<T, A> Iterable<T>.firstNonNullResult(f: (T) -> A?): A? = asSequence().map(f).firstOrNull(Objects::nonNull)

  /**
   * Determines if `this` ClassifierSet's descendant children forms a superset (could be equivalent) of the given
   * `targetSet`'s immediate children.
   */
  fun isSupersetOf(targetSet: Set<InstanceObject>): Boolean {
    val clone = Collections.newSetFromMap(IdentityHashMap<InstanceObject, Boolean>()).apply { addAll(targetSet) }
    filterOutInstances(clone)
    return clone.isEmpty()
  }

  /**
   * Remove this node's and its children's instances from the given set
   */
  private fun filterOutInstances(remainders: MutableSet<InstanceObject>) {
    val s = state
    when {
      s is State.Coalesced -> {
        remainders.removeAll(s.deltaInstances)
        remainders.removeAll(s.snapshotInstances)
      }
      s is State.Partitioned && remainders.isNotEmpty() -> s.classifier.allClassifierSets.forEach { child ->
        child.filterOutInstances(remainders)
        if (remainders.isEmpty()) {
          return
        }
      }
    }
  }

  /**
   * @return Whether the node's immediate instances overlap with `targetSet`
   */
  fun immediateInstancesOverlapWith(targetSet: Set<InstanceObject>): Boolean = state.let { s ->
    s is State.Coalesced && (overlaps(s.deltaInstances, targetSet) || overlaps(s.snapshotInstances, targetSet))
  }

  /**
   * @return Whether the node and its descendants' instances overlap with `targetSet`
   */
  fun overlapsWith(targetSet: Set<InstanceObject>): Boolean = when (val s = state) {
    is State.Coalesced -> immediateInstancesOverlapWith(targetSet)
    is State.Partitioned -> s.classifier.allClassifierSets.any { it.overlapsWith(targetSet) }
  }

  /**
   * Gets the classifier this class will use to classify its instances.
   */
  protected abstract fun createSubClassifier(): Classifier

  fun applyFilter(filter: Filter, filterChanged: Boolean) {
    applyFilter(filter, false, true, filterChanged)
  }

  /**
   * Apply filter and update allocation information
   * Filter children classifierSets that neither match the pattern nor have any matched ancestors
   * Update information base on unfiltered children classifierSets
   *
   * @param filter a pattern to search for in stringForMatching.
   * @param hasMatchedAncestor true if any ancestors matched the pattern.
   * @param isTopLevel true if this has no ancestors.
   * @param filterChanged true if the filter has changed from the last pass.
   */
  private fun applyFilter(filter: Filter, hasMatchedAncestor: Boolean, isTopLevel: Boolean, filterChanged: Boolean) {
    if (!filterChanged && !needsRefiltering) {
      return
    }
    isMatched = !isTopLevel && filter.matches(stringForMatching)
    filterMatchCount = if (isMatched) 1 else 0
    when (val s = ensurePartitioned()) {
      is State.Coalesced.Leaf -> {
        myIsFiltered = !isMatched && !hasMatchedAncestor
        needsRefiltering = false
      }
      is State.Partitioned -> {
        myIsFiltered = true
        snapshotObjectCount = 0
        deltaAllocationCount = 0
        deltaDeallocationCount = 0
        allocationSize = 0
        deallocationSize = 0
        totalShallowSize = 0
        totalNativeSize = 0
        instancesWithStackInfoCount = 0
        totalObjectSetCount = s.classifier.allClassifierSets.size
        filteredObjectSetCount = 0
        for (classifierSet in s.classifier.allClassifierSets) {
          classifierSet.applyFilter(filter, hasMatchedAncestor || isMatched, false, filterChanged)
          totalObjectSetCount += classifierSet.totalObjectSetCount
          if (!classifierSet.isFiltered) {
            myIsFiltered = false
            snapshotObjectCount += classifierSet.snapshotObjectCount
            deltaAllocationCount += classifierSet.deltaAllocationCount
            deltaDeallocationCount += classifierSet.deltaDeallocationCount
            allocationSize += classifierSet.allocationSize
            deallocationSize += classifierSet.deallocationSize
            totalShallowSize += classifierSet.totalShallowSize
            totalNativeSize += classifierSet.totalNativeSize
            deltaShallowSize += classifierSet.deltaShallowSize
            instancesWithStackInfoCount += classifierSet.instancesWithStackInfoCount
            filterMatchCount += classifierSet.filterMatchCount
            filteredObjectSetCount++
          }
        }
      }
      else -> throw IllegalStateException()
    }

    needsRefiltering = false
  }

  private fun initState() = State.Coalesced.Delayed(::createSubClassifier, LinkedHashSet(0), LinkedHashSet(0))

  private fun countInstanceFilterMatch(filter: CaptureObjectInstanceFilter): Int = when (val s = state) {
    is State.Partitioned -> s.classifier.allClassifierSets.sumOf { it.getInstanceFilterMatchCount(filter) }
    is State.Coalesced -> s.deltaInstances.count(filter.instanceTest) +
                          s.snapshotInstances.count { it !in s.deltaInstances && filter.instanceTest(it) }
  }

  private enum class SetOperation(val invoke: (MutableSet<InstanceObject>, InstanceObject) -> Unit, val countChange: Int) {
    ADD(MutableSet<InstanceObject>::add, 1),
    REMOVE(MutableSet<InstanceObject>::remove, -1);
  }

  companion object {
    private fun overlaps(set1: Set<InstanceObject>, set2: Set<InstanceObject>): Boolean {
      val iter = if (set1.size < set2.size) set1 else set2
      val test = if (iter === set1) set2 else set1
      return iter.any(test::contains)
    }

    private fun Long.validOrZero(): Long = if (this == MemoryObject.INVALID_VALUE.toLong()) 0L else this
  }
}