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

/**
 * A general base class for classifying/filtering objects into categories.
 *
 * Supports lazy-loading the ClassifierSet's name in case it is expensive.
 */
abstract class ClassifierSet(supplyName: () -> String) : MemoryObject {
  constructor(name: String): this({ name })
  private val _name by lazy(supplyName)

  // The set of instances that make up our baseline snapshot (e.g. live objects at the left of a selection range).
  @JvmField
  protected val snapshotInstances = LinkedHashSet<InstanceObject>(0)

  // The set of instances that have delta events (e.g. delta allocations/deallocations within a selection range).
  // Note that instances here can also appear in the set of snapshot instances (e.g. when a instance is allocated before the selection
  // and deallocation within the selection).
  @JvmField
  protected val deltaInstances = LinkedHashSet<InstanceObject>(0)

  // Lazily create the Classifier, as it is configurable and isn't necessary until nodes under this node needs to be classified.
  @JvmField
  protected var classifier: Classifier? = null
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
  var totalRetainedSize = 0L
    private set
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
    get() = getStreamOf { Stream.concat(it.snapshotInstances.stream(), it.deltaInstances.stream()).distinct() }

  /**
   * Return the stream of instance objects that contribute to the delta.
   * Note that there can be duplicated entries as [.getSnapshotInstanceStream].
   */
  protected val deltaInstanceStream: Stream<InstanceObject> get() = getStreamOf { it.deltaInstances.stream() }

  /**
   * Return the stream of instance objects that contribute to the baseline snapshot.
   * Note that there can duplicated entries as [.getDeltaInstanceStream].
   */
  protected val snapshotInstanceStream: Stream<InstanceObject> get() = getStreamOf { it.snapshotInstances.stream() }
  val filterMatches: Stream<InstanceObject> get() = getStreamOf { if (it.isMatched) it.instancesStream else Stream.empty() }

  val childrenClassifierSets: List<ClassifierSet>
    get() {
      ensurePartition()
      return classifier!!.filteredClassifierSets
    }

  @JvmField
  protected var myIsFiltered = false

  val isFiltered: Boolean get() = isEmpty || myIsFiltered

  override fun getName() = _name

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
    if (classifier != null && !classifier!!.isTerminalClassifier) {
      val classifierSet = classifier!!.getClassifierSet(instanceObject, op == SetOperation.ADD)
      changed = classifierSet != null &&
        classifierSet.changeSnapshotInstanceObject(instanceObject, op)
    } else {
      changed = op == SetOperation.ADD != snapshotInstances.contains(instanceObject)
      op.invoke(snapshotInstances, instanceObject)
    }
    if (changed) {
      snapshotObjectCount += op.countChange
      totalNativeSize += op.countChange * instanceObject.nativeSize.validOrZero()
      totalShallowSize += op.countChange * instanceObject.shallowSize.toLong().validOrZero()
      totalRetainedSize += op.countChange * instanceObject.retainedSize.validOrZero()
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
    val change =
      when {
        classifier != null && !classifier!!.isTerminalClassifier -> {
          val classifierSet = classifier!!.getClassifierSet(instanceObject, op == SetOperation.ADD)
          classifierSet?.changeDeltaInstanceInformation(instanceObject, isAllocation, op) ?: DeltaChange.UNCHANGED
        }
        (op == SetOperation.ADD || !instanceObject.hasTimeData()) &&
        // `contains` is more expensive, so deferred to after above test fails.
        // This line is run often enough to make a difference.
        op == SetOperation.ADD != deltaInstances.contains(instanceObject) -> {
          op.invoke(deltaInstances, instanceObject)
          DeltaChange.INSTANCE_ADDED_OR_REMOVED
        }
        else -> DeltaChange.INSTANCE_MODIFIED
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
      totalRetainedSize += deltaRetainedSize
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
    snapshotInstances.clear()
    deltaInstances.clear()
    classifier = createSubClassifier()
    snapshotObjectCount = 0
    deltaAllocationCount = 0
    deltaDeallocationCount = 0
    totalShallowSize = 0
    totalNativeSize = 0
    totalRetainedSize = 0
    deltaShallowSize = 0
    instancesWithStackInfoCount = 0
    totalObjectSetCount = 0
    filteredObjectSetCount = 0
    filterMatchCount = 0
  }

  private fun getStreamOf(extract: (ClassifierSet) -> Stream<InstanceObject>): Stream<InstanceObject> =
    when (classifier) {
      null -> extract(this)
      else -> Stream.concat(classifier!!.allClassifierSets.stream().flatMap { it.getStreamOf(extract) },
                            extract(this))
    }

  fun hasStackInfo(): Boolean = instancesWithStackInfoCount > 0

  /**
   * O(N) search through all descendant ClassifierSet.
   * Note - calling this method would cause the full ClassifierSet tree to be built if it has not been done already, and all InstanceObjects
   * would be partitioned into their corresponding leaf ClassifierSet.
   *
   * @return the set that contains the `target`, or null otherwise.
   */
  fun findContainingClassifierSet(target: InstanceObject): ClassifierSet? {
    val instancesContainsTarget = target in snapshotInstances || target in deltaInstances
    return when {
      instancesContainsTarget && classifier != null -> this
      instancesContainsTarget || classifier != null -> {
        val childrenClassifierSets = childrenClassifierSets
        // mySnapshotInstances/myDeltaInstances can be updated after getChildrenClassiferSets so rebuild the stream.
        val stillContainsTarget = target in snapshotInstances || target in deltaInstances
        when {
          // If after the partition the target still falls within the instances within this set, then return this set.
          instancesContainsTarget && stillContainsTarget -> this
          else -> childrenClassifierSets.firstNonNullResult { it.findContainingClassifierSet(target) }
        }
      }
      else -> null
    }
  }

  /**
   * O(N) search through all descendant ClassifierSet.
   * Note - calling this method would cause the full ClassifierSet tree to be built if it has not been done already, and all InstanceObjects
   * would be partitioned into their corresponding leaf ClassifierSet.
   *
   * @return the set that satisfies `pred`, or null otherwise.
   */
  fun findClassifierSet(test: (ClassifierSet) -> Boolean): ClassifierSet? = when {
    test(this) -> this
    classifier != null -> childrenClassifierSets.firstNonNullResult { it.findClassifierSet(test) }
    else -> null
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
    // Filter out from current node
    remainders.removeAll(deltaInstances)
    remainders.removeAll(snapshotInstances)

    // Filter out from children
    if (classifier != null && remainders.isNotEmpty()) {
      for (child in classifier!!.allClassifierSets) {
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
  fun immediateInstancesOverlapWith(targetSet: Set<InstanceObject>): Boolean =
    overlaps(deltaInstances, targetSet) || overlaps(snapshotInstances, targetSet)

  /**
   * @return Whether the node and its descendants' instances overlap with `targetSet`
   */
  fun overlapsWith(targetSet: Set<InstanceObject>): Boolean =
    immediateInstancesOverlapWith(targetSet) ||
    classifier != null && classifier!!.allClassifierSets.stream().anyMatch { c: ClassifierSet -> c.overlapsWith(targetSet) }

  /**
   * Force the instances of this node to be partitioned.
   */
  private fun ensurePartition() {
    if (classifier == null) {
      classifier = createSubClassifier()
      classifier!!.partition(snapshotInstances, deltaInstances)
    }
  }

  /**
   * Gets the classifier this class will use to classify its instances.
   */
  protected abstract fun createSubClassifier(): Classifier

  // Apply filter and update allocation information
  // Filter children classifierSets that neither match the pattern nor have any matched ancestors
  // Update information base on unfiltered children classifierSets
  open fun applyFilter(filter: Filter, hasMatchedAncestor: Boolean, filterChanged: Boolean) {
    if (!filterChanged && !needsRefiltering) {
      return
    }
    myIsFiltered = true
    ensurePartition()
    snapshotObjectCount = 0
    deltaAllocationCount = 0
    deltaDeallocationCount = 0
    totalShallowSize = 0
    totalNativeSize = 0
    totalRetainedSize = 0
    instancesWithStackInfoCount = 0
    totalObjectSetCount = classifier!!.allClassifierSets.size
    filteredObjectSetCount = 0
    isMatched = matches(filter)
    filterMatchCount = if (isMatched) 1 else 0
    for (classifierSet in classifier!!.allClassifierSets) {
      classifierSet.applyFilter(filter, hasMatchedAncestor || isMatched, filterChanged)
      totalObjectSetCount += classifierSet.totalObjectSetCount
      if (!classifierSet.isFiltered) {
        myIsFiltered = false
        snapshotObjectCount += classifierSet.snapshotObjectCount
        deltaAllocationCount += classifierSet.deltaAllocationCount
        deltaDeallocationCount += classifierSet.deltaDeallocationCount
        totalShallowSize += classifierSet.totalShallowSize
        totalNativeSize += classifierSet.totalNativeSize
        totalRetainedSize += classifierSet.totalRetainedSize
        deltaShallowSize += classifierSet.deltaShallowSize
        instancesWithStackInfoCount += classifierSet.instancesWithStackInfoCount
        filterMatchCount += classifierSet.filterMatchCount
        filteredObjectSetCount++
      }
    }
    needsRefiltering = false
  }

  protected open fun matches(filter: Filter): Boolean = filter.matches(name)

  private fun countInstanceFilterMatch(filter: CaptureObjectInstanceFilter): Int = when {
    classifier != null && !classifier!!.isTerminalClassifier ->
      classifier!!.allClassifierSets.sumBy { it.getInstanceFilterMatchCount(filter) }
    else -> deltaInstances.count(filter.instanceTest) +
            snapshotInstances.count { it !in deltaInstances && filter.instanceTest(it) }
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