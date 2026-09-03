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
import com.android.tools.profilers.memory.adapters.InstanceObject
import com.android.tools.profilers.memory.adapters.MemoryObject
import com.android.tools.profilers.memory.adapters.TraceProcessorHeapDumpInstance
import com.android.tools.profilers.memory.adapters.instancefilters.CaptureObjectInstanceFilter
import com.android.tools.profilers.memory.adapters.instancefilters.NoneFilter
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Stream
import kotlin.math.min
import kotlin.streams.asSequence
import kotlin.streams.asStream

/**
 * A general base class for classifying/filtering objects into categories.
 *
 * Supports lazy-loading the ClassifierSet's name in case it is expensive.
 *
 * THREADING & LOCKING MODEL: This class uses `synchronized(this)` to protect state consistency during updates (e.g. adding instances) and
 * complex aggregations (e.g. calculating totalRetainedSize).
 *
 * Example of State Consistency: When adding an instance via [addSnapshotInstanceObject], we update multiple fields (count, size, etc.).
 * Synchronization ensures that a concurrent reader doesn't see an inconsistent state (e.g., count incremented but size not yet updated),
 * and that concurrent writers don't race on the read-modify-write of these counters.
 *
 * LOCK ORDERING & DEADLOCKS: Aggregation methods (like [totalRetainedSize]) traverse the tree downwards, establishing a PARENT -> CHILD
 * locking order. The UI (MemoryClassifierView) often renders nodes by comparing a Child to the Root (Child -> Root access pattern). If the
 * UI were to call synchronized methods, a Deadlock would occur (Background: Parent->Child vs UI: Child->Parent). To prevent this, the UI
 * must ONLY access non-blocking @Volatile properties (e.g. [retainedSizeCache]) and NEVER call synchronized aggregation methods directly
 * during rendering.
 */
abstract class ClassifierSet(supplyName: () -> String) : MemoryObject {
  private sealed class State {
    @Volatile var retainedSize: Long = -1
    @Volatile var retainedNativeSize: Long = -1

    sealed class Coalesced(
      // The set of instances that make up our baseline snapshot (e.g. live objects at the left of a selection range).
      val snapshotInstances: MutableSet<InstanceObject>,
      // The set of instances that have delta events (e.g. delta allocations/deallocations within a selection range).
      // Note that instances here can also appear in the set of snapshot instances (e.g. when a instance is allocated before the selection
      // and deallocation within the selection).
      val deltaInstances: MutableSet<InstanceObject>,
    ) : State() {
      class Leaf(snapshotInstances: MutableSet<InstanceObject>, deltaInstances: MutableSet<InstanceObject>) :
        Coalesced(snapshotInstances, deltaInstances)

      class Delayed(
        val makeClassifier: () -> Classifier,
        snapshotInstances: MutableSet<InstanceObject>,
        deltaInstances: MutableSet<InstanceObject>,
      ) : Coalesced(snapshotInstances, deltaInstances)
    }

    class Partitioned(val classifier: Classifier) : State()

    fun retracted(makeClassifier: () -> Classifier): Coalesced =
      when (this) {
        is Coalesced -> this
        is Partitioned ->
          classifier.classifierSetSequence.let { subs ->
            fun instances(extract: (ClassifierSet) -> Stream<InstanceObject>) = subs.flatMapTo(mutableSetOf()) { extract(it).asSequence() }
            Coalesced.Delayed(makeClassifier, instances { it.snapshotInstanceStream }, instances { it.deltaInstanceStream })
          }
      }

    fun forced(): State /* Leaf | Partitioned */ =
      when (this) {
        is Partitioned,
        is Coalesced.Leaf -> this
        is Coalesced.Delayed ->
          when (val c = makeClassifier()) {
            is Classifier.Id -> Coalesced.Leaf(snapshotInstances, deltaInstances)
            is Classifier.Join<*> -> Partitioned(c.also { it.partition(snapshotInstances, deltaInstances) })
          }
      }
  }

  constructor(name: String) : this({ name })

  private val _name by lazy(supplyName)

  @Volatile private var state: State = initState()

  @Volatile
  var totalObjectSetCount = 0
    private set

  @Volatile
  var filteredObjectSetCount = 0
    private set

  @Volatile private var snapshotObjectCount = 0
  @Volatile
  var deltaAllocationCount = 0
    private set

  @Volatile
  var deltaDeallocationCount = 0
    private set

  @Volatile
  var allocationSize = 0L
    private set

  @Volatile
  var deallocationSize = 0L
    private set

  @Volatile
  var totalNativeSize = 0L
    private set

  @Volatile
  var totalShallowSize = 0L
    private set

  open val totalRetainedSize: Long
    get() =
      synchronized(this) {
        when (val s = state) {
          is State.Coalesced ->
            when (s.retainedSize) {
              -1L -> {
                // b(234174316) for an arbitrary classifier, neither summing over the classes
                // nor the instances is guaranteed to give the tighter estimation, so we take their min.
                // In practice, this problem shows up when we support classstacks in the heap dump,
                // where a callstack may only have some of the instances of the classes.
                val maxRetainedSizeByClass =
                  (s.snapshotInstances.asSequence() + s.deltaInstances.asSequence())
                    .map { it.classEntry }
                    .distinct()
                    .fold(0L) { sum, entry ->
                      when {
                        entry.retainedSize == -1L || sum == Long.MAX_VALUE -> Long.MAX_VALUE
                        else -> sum + entry.retainedSize
                      }
                    }
                val maxRetainedSizeByInstances =
                  (s.snapshotInstances.asSequence() + s.deltaInstances.asSequence()).sumOf { it.retainedSize.validOrZero() }
                val maxRetainedSize = min(maxRetainedSizeByClass, maxRetainedSizeByInstances)
                s.retainedSize = maxRetainedSize
                maxRetainedSize
              }
              else -> s.retainedSize
            }
          is State.Partitioned -> {
            if (s.retainedSize == -1L) {
              s.retainedSize = s.classifier.classifierSetSequence.filter { !it.isFiltered }.sumOf { it.totalRetainedSize }
            }
            s.retainedSize
          }
        }
      }

  open val totalRetainedNativeSize: Long
    get() =
      synchronized(this) {
        when (val s = state) {
          is State.Coalesced ->
            when (s.retainedNativeSize) {
              -1L -> {
                val maxRetainedNativeSizeByClass =
                  (s.snapshotInstances.asSequence() + s.deltaInstances.asSequence())
                    .mapNotNull { it.classEntry }
                    .distinct()
                    .fold(0L) { sum, entry ->
                      when {
                        entry.retainedNativeSize == -1L || sum == Long.MAX_VALUE -> Long.MAX_VALUE
                        else -> sum + entry.retainedNativeSize
                      }
                    }
                val maxRetainedNativeSizeByInstances =
                  (s.snapshotInstances.asSequence() + s.deltaInstances.asSequence()).sumOf { it.retainedNativeSize.validOrZero() }
                val maxRetainedNativeSize = min(maxRetainedNativeSizeByClass, maxRetainedNativeSizeByInstances)
                s.retainedNativeSize = maxRetainedNativeSize
                maxRetainedNativeSize
              }
              else -> s.retainedNativeSize
            }
          is State.Partitioned -> {
            if (s.retainedNativeSize == -1L) {
              s.retainedNativeSize = s.classifier.classifierSetSequence.filter { !it.isFiltered }.sumOf { it.totalRetainedNativeSize }
            }
            s.retainedNativeSize
          }
        }
      }

  @Volatile
  var deltaShallowSize = 0L
    private set

  @Volatile private var instancesWithStackInfoCount = 0

  // Number of ClassifierSet that match the filter.
  @Volatile
  var filterMatchCount = 0
    protected set

  private val instanceFilterMatchCounts = ConcurrentHashMap<CaptureObjectInstanceFilter, Int>()

  // TODO: `myIsMatched` should be `true` initially, as "no filter" means "trivially matched".
  //        But at the moment that would break one test in `HeapSetNodeHRendererTest`
  @Volatile
  var isMatched = false
    protected set

  // We need to apply filter to ClassifierSet again after any updates (insertion, deletion etc.)
  @JvmField @Volatile protected var needsRefiltering = false

  fun forceRefiltering() {
    needsRefiltering = true
  }

  open val isEmpty: Boolean
    get() = snapshotObjectCount == 0 && deltaAllocationCount == 0 && deltaDeallocationCount == 0

  val totalObjectCount: Int
    get() = snapshotObjectCount + deltaAllocationCount - deltaDeallocationCount

  /** Returns the count of unfiltered [ClassSet] descendants in this classifier subtree. */
  open val classSetCount: Long
    get() {
      val s = state
      // For partitioned classifier trees (the common case for heap dumps), the child structure is
      // immutable, allowing lock-free sequence traversal without thread pool monitor contention.
      if (s is State.Partitioned) {
        return s.classifier.classifierSetSequence.filter { !it.isFiltered }.sumOf { it.classSetCount }
      }
      // If unpartitioned or coalesced (e.g. after changing class grouping), synchronize on `this`
      // to prevent race conditions while ensurePartitioned() mutates state to build the child tree.
      return synchronized(this) {
        when (val forcedState = ensurePartitioned()) {
          is State.Partitioned -> forcedState.classifier.classifierSetSequence.filter { !it.isFiltered }.sumOf { it.classSetCount }
          is State.Coalesced -> 0L
        }
      }
    }

  val totalRemainingSize: Long
    get() = allocationSize - deallocationSize

  open val isClassFilterMatch: Boolean
    get() = true

  open val instancesCount: Int
    get() = instancesStream.count().toInt()

  open fun getInstances(offset: Int, limit: Int): List<InstanceObject> {
    return instancesStream.skip(offset.toLong()).limit(limit.toLong()).toList()
  }

  /** Gets a stream of all instances (including all descendants) in this ClassifierSet. */
  val instancesStream: Stream<InstanceObject>
    get() = getStreamOf({ true }) { Stream.concat(it.snapshotInstances.stream(), it.deltaInstances.stream()).distinct() }

  /**
   * Return the stream of instance objects that contribute to the delta. Note that there can be duplicated entries as
   * [.getSnapshotInstanceStream].
   */
  protected val deltaInstanceStream: Stream<InstanceObject>
    get() = getStreamOf({ true }) { it.deltaInstances.stream() }

  /**
   * Return the stream of instance objects that contribute to the baseline snapshot. Note that there can duplicated entries as
   * [.getDeltaInstanceStream].
   */
  protected val snapshotInstanceStream: Stream<InstanceObject>
    get() = getStreamOf({ true }) { it.snapshotInstances.stream() }

  val childrenClassifierSets: List<ClassifierSet>
    get() {
      val s = state
      if (s is State.Partitioned) {
        return s.classifier.filteredClassifierSets
      }
      return synchronized(this) {
        when (val s = ensurePartitioned()) {
          is State.Coalesced -> listOf()
          is State.Partitioned -> s.classifier.filteredClassifierSets
        }
      }
    }

  @JvmField @Volatile protected var myIsFiltered = false

  val isFiltered: Boolean
    get() = isEmpty || myIsFiltered

  open val stringForMatching: String
    get() = _name

  override fun getName() = _name

  open val isRetainedSizeCached: Boolean
    get() = state.retainedSize != -1L

  open val isRetainedNativeSizeCached: Boolean
    get() = state.retainedNativeSize != -1L

  open val retainedSizeCache: Long
    get() = state.retainedSize

  open val retainedNativeSizeCache: Long
    get() = state.retainedNativeSize

  private fun invalidateRetainedSizeCache() {
    state.retainedSize = -1L
    state.retainedNativeSize = -1L
  }

  private fun ensurePartitioned() = synchronized(this) { state.forced().also { state = it } }

  protected fun coalesce() = synchronized(this) { state = state.retracted(::createSubClassifier) }

  /** Returns the cached or computed match count of instances in this set that satisfy [filter]. */
  fun getInstanceFilterMatchCount(filter: CaptureObjectInstanceFilter): Int =
    instanceFilterMatchCounts.computeIfAbsent(filter) { countInstanceFilterMatch(it) }

  fun getCachedInstanceFilterMatchCount(filter: CaptureObjectInstanceFilter): Int = instanceFilterMatchCounts[filter] ?: -1

  /**
   * Add an instance to the baseline snapshot and update the accounting of the "total" values. Note that instances at the baseline must be
   * an allocation event.
   */
  fun addSnapshotInstanceObject(instanceObject: InstanceObject) = changeSnapshotInstanceObject(instanceObject, SetOperation.ADD)

  /** Aggregates precomputed class overview metrics from Perfetto Trace Processor lazily into classifier tree nodes. */
  fun addLazyClassOverview(
    instanceObject: InstanceObject,
    count: Int,
    shallowSize: Long,
    nativeSize: Long,
    retainedNativeSize: Long,
    retainedSize: Long,
  ) {
    synchronized(this) {
      when (val s = ensurePartitioned()) {
        is State.Partitioned -> {
          val classifierSet = s.classifier.getClassifierSet(instanceObject, true)
          classifierSet?.addLazyClassOverview(instanceObject, count, shallowSize, nativeSize, retainedNativeSize, retainedSize)
        }
        is State.Coalesced -> {
          s.snapshotInstances.add(instanceObject)
          if (s.retainedSize == -1L) {
            s.retainedSize = 0L
          }
          if (retainedSize > 0) {
            s.retainedSize += retainedSize
          }
          if (s.retainedNativeSize == -1L) {
            s.retainedNativeSize = 0L
          }
          if (retainedNativeSize > 0) {
            s.retainedNativeSize += retainedNativeSize
          }
        }
      }
      snapshotObjectCount += count
      totalObjectSetCount += count
      totalNativeSize += nativeSize
      totalShallowSize += shallowSize
    }
  }

  /** Remove an instance from the baseline snapshot and update the accounting of the "total" values. */
  fun removeSnapshotInstanceObject(instanceObject: InstanceObject) = changeSnapshotInstanceObject(instanceObject, SetOperation.REMOVE)

  private fun changeSnapshotInstanceObject(instanceObject: InstanceObject, op: SetOperation): Boolean =
    synchronized(this) {
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
        val instanceCount = instanceObject.instanceCount
        snapshotObjectCount += op.countChange * instanceCount
        val nativeSize = instanceObject.nativeSize.validOrZero()
        if (nativeSize > 0) {
          val sizeToAdd = if (instanceObject is TraceProcessorHeapDumpInstance) nativeSize else nativeSize * instanceCount
          totalNativeSize += op.countChange * sizeToAdd
        }

        val shallowSize = instanceObject.shallowSize.toLong().validOrZero()
        if (shallowSize > 0) {
          val sizeToAdd = if (instanceObject is TraceProcessorHeapDumpInstance) shallowSize else shallowSize * instanceCount
          totalShallowSize += op.countChange * sizeToAdd
        }
        invalidateRetainedSizeCache()
        if (!instanceObject.isCallStackEmpty) {
          instancesWithStackInfoCount += op.countChange
        }
        instanceFilterMatchCounts.clear()
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
    INSTANCE_ADDED_OR_REMOVED(true, true),
  }

  private fun changeDeltaInstanceInformation(instanceObject: InstanceObject, isAllocation: Boolean, op: SetOperation): DeltaChange =
    synchronized(this) {
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
          instanceFilterMatchCounts.clear()
        }
      }
      return change
    }

  fun clearClassifierSets() =
    synchronized(this) {
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
      instanceFilterMatchCounts.clear()
    }

  /** Collect stream of instances from nodes satisfying |condition| */
  private fun getStreamOf(
    condition: (ClassifierSet) -> Boolean,
    extract: (State.Coalesced) -> Stream<InstanceObject>,
  ): Stream<InstanceObject> = state.let { s ->
    when {
      !condition(this) -> Stream.empty()
      s is State.Coalesced -> extract(s)
      else -> (s as State.Partitioned).classifier.classifierSetSequence.asStream().flatMap { it.getStreamOf(condition, extract) }
    }
  }

  fun hasStackInfo(): Boolean = instancesWithStackInfoCount > 0

  /**
   * O(N) search through all descendant ClassifierSet. Note - calling this method would cause the full ClassifierSet tree to be built if it
   * has not been done already, and all InstanceObjects would be partitioned into their corresponding leaf ClassifierSet.
   *
   * @return the set that contains the `target`, or null otherwise.
   */
  open fun findContainingClassifierSet(target: InstanceObject): ClassifierSet? =
    synchronized(this) {
      state.let { s ->
        when {
          s is State.Coalesced && (target in s.snapshotInstances || target in s.deltaInstances) ->
            when (val forcedState = ensurePartitioned()) {
              is State.Coalesced -> this
              is State.Partitioned ->
                forcedState.classifier.classifierSetSequence.firstNotNullOfOrNull { it.findContainingClassifierSet(target) }
            }
          s is State.Partitioned -> s.classifier.classifierSetSequence.firstNotNullOfOrNull { it.findContainingClassifierSet(target) }
          else -> null
        }
      }
    }

  /**
   * O(N) search through all descendant ClassifierSet. Note - calling this method would cause the full ClassifierSet tree to be built if it
   * has not been done already, and all InstanceObjects would be partitioned into their corresponding leaf ClassifierSet.
   *
   * @return the set that satisfies `pred`, or null otherwise.
   */
  fun findClassifierSet(test: (ClassifierSet) -> Boolean): ClassifierSet? =
    synchronized(this) {
      when {
        test(this) -> this
        else -> childrenClassifierSets.firstNotNullOfOrNull { it.findClassifierSet(test) }
      }
    }

  /**
   * Determines if `this` ClassifierSet's descendant children forms a superset (could be equivalent) of the given `targetSet`'s immediate
   * children.
   */
  fun isSupersetOf(targetSet: Set<InstanceObject>): Boolean =
    synchronized(this) {
      val clone = Collections.newSetFromMap(IdentityHashMap<InstanceObject, Boolean>()).apply { addAll(targetSet) }
      filterOutInstances(clone)
      return clone.isEmpty()
    }

  /** Remove this node's and its children's instances from the given set */
  private fun filterOutInstances(remainders: MutableSet<InstanceObject>) {
    val s = state
    when {
      remainders.isEmpty() -> return
      s is State.Coalesced -> {
        remainders.removeAllFast(s.deltaInstances)
        remainders.removeAllFast(s.snapshotInstances)
      }
      s is State.Partitioned ->
        s.classifier.classifierSetSequence.forEach { child ->
          child.filterOutInstances(remainders)
          if (remainders.isEmpty()) {
            return
          }
        }
    }
  }

  /**
   * Like [removeAll], but iterating over the smaller set. For our specific use of [IdentityHashMap], this restores the optimization from
   * [AbstractSet], which [IdentityHashMap] wouldn't be able to do in general when [those] uses structural instead of referential equality.
   */
  private fun <X> MutableSet<X>.removeAllFast(those: Set<X>) {
    when {
      size < those.size -> removeAll(those)
      else -> for (that in those) remove(that)
    }
  }

  /** @return Whether the node's immediate instances overlap with `targetSet` */
  fun immediateInstancesOverlapWith(targetSet: Set<InstanceObject>): Boolean =
    synchronized(this) {
      state.let { s -> s is State.Coalesced && (overlaps(s.deltaInstances, targetSet) || overlaps(s.snapshotInstances, targetSet)) }
    }

  /** @return Whether the node and its descendants' instances overlap with `targetSet` */
  fun overlapsWith(targetSet: Set<InstanceObject>): Boolean =
    synchronized(this) {
      when (val s = state) {
        is State.Coalesced -> immediateInstancesOverlapWith(targetSet)
        is State.Partitioned -> s.classifier.classifierSetSequence.any { it.overlapsWith(targetSet) }
      }
    }

  /** Gets the classifier this class will use to classify its instances. */
  protected abstract fun createSubClassifier(): Classifier

  fun applyFilter(filter: Filter, filterChanged: Boolean) {
    applyFilter(filter, false, true, filterChanged)
  }

  /**
   * Apply filter and update allocation information Filter children classifierSets that neither match the pattern nor have any matched
   * ancestors Update information base on unfiltered children classifierSets
   *
   * @param filter a pattern to search for in stringForMatching.
   * @param hasMatchedAncestor true if any ancestors matched the pattern.
   * @param isTopLevel true if this has no ancestors.
   * @param filterChanged true if the filter has changed from the last pass.
   */
  private fun applyFilter(filter: Filter, hasMatchedAncestor: Boolean, isTopLevel: Boolean, filterChanged: Boolean): Unit =
    synchronized(this) {
      if (!filterChanged && !needsRefiltering) {
        return@synchronized
      }
      val matchesClassFilter = isClassFilterMatch
      isMatched = !isTopLevel && filter.matches(stringForMatching) && matchesClassFilter
      filterMatchCount = if (isMatched) 1 else 0
      when (val s = ensurePartitioned()) {
        is State.Coalesced.Leaf -> {
          myIsFiltered = (!isMatched && !hasMatchedAncestor) || !matchesClassFilter
          instanceFilterMatchCounts.clear()
          needsRefiltering = false
        }
        is State.Partitioned -> {
          myIsFiltered = true
          snapshotObjectCount = 0
          invalidateRetainedSizeCache()
          instanceFilterMatchCounts.clear()
          deltaAllocationCount = 0
          deltaDeallocationCount = 0
          allocationSize = 0
          deallocationSize = 0
          totalShallowSize = 0
          totalNativeSize = 0
          instancesWithStackInfoCount = 0
          totalObjectSetCount = s.classifier.classifierSetSequence.count()
          filteredObjectSetCount = 0
          val childFilterChanged = filterChanged || needsRefiltering
          for (classifierSet in s.classifier.classifierSetSequence) {
            classifierSet.applyFilter(filter, hasMatchedAncestor || isMatched, false, childFilterChanged)
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

  protected open fun countInstanceFilterMatch(filter: CaptureObjectInstanceFilter): Int =
    when (val s = state) {
      is State.Partitioned -> s.classifier.classifierSetSequence.filter { !it.isFiltered }.sumOf { it.getInstanceFilterMatchCount(filter) }
      is State.Coalesced ->
        s.deltaInstances.count(filter.instanceTest) +
          s.snapshotInstances.sumOf { inst ->
            if (inst in s.deltaInstances) return@sumOf 0
            if (inst is TraceProcessorHeapDumpInstance) {
              if (!inst.isSyntheticClass && filter !is NoneFilter) {
                inst.captureObject.filterInstances.count { it.classEntry.classId == inst.classEntry.classId && filter.instanceTest(it) }
              } else {
                0
              }
            } else if (filter.instanceTest(inst)) {
              1
            } else {
              0
            }
          }
    }

  private enum class SetOperation(val invoke: (MutableSet<InstanceObject>, InstanceObject) -> Unit, val countChange: Int) {
    ADD(MutableSet<InstanceObject>::add, 1),
    REMOVE(MutableSet<InstanceObject>::remove, -1),
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
