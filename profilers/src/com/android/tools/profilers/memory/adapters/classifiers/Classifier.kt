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

import com.android.tools.profilers.memory.adapters.InstanceObject

/**
 * The base index for holding child [ClassifierSet]s.
 */
sealed class Classifier {
  object Id: Classifier() {
    override val classifierSetSequence get(): Sequence<ClassifierSet> = sequenceOf()
    override fun getClassifierSet(instance: InstanceObject, createIfAbsent: Boolean) = throw UnsupportedOperationException()
  }
  class Join<T: Any>(private val classify: (InstanceObject) -> T?,
                     private val makeSet: (T) -> ClassifierSet,
                     private val rest: Classifier): Classifier() {
    private val cache = LinkedHashMap<T, ClassifierSet>()
    override fun getClassifierSet(instance: InstanceObject, createIfAbsent: Boolean): ClassifierSet? =
      when (val k = classify(instance)) {
        null -> rest.getClassifierSet(instance, createIfAbsent)
        else -> cache[k].let { classifierSet -> when {
          classifierSet == null && createIfAbsent -> makeSet(k).also { cache[k] = it }
          else -> classifierSet
        } }
      }
    override val classifierSetSequence get() = cache.values.asSequence() + rest.classifierSetSequence
  }

  /**
   * If this Classifier is a terminal classifier, and instances will not be further classified.
   */
  val isTerminalClassifier: Boolean get() = this is Id
  val filteredClassifierSets get() = classifierSetSequence.filter { !it.isFiltered }.toList()
  internal abstract val classifierSetSequence: Sequence<ClassifierSet>

  /**
   * Retrieve the next-level ClassifierSet that the given `instance` belongs to. If none exists and `createIfAbsent` is true,
   * then create and return the new ClassiferSet.
   */
  abstract fun getClassifierSet(instance: InstanceObject, createIfAbsent: Boolean): ClassifierSet?

  /**
   * Partitions [InstanceObject]s in `snapshotInstances` and `myDeltaInstances` according to the current
   * [ClassifierSet]'s strategy. This will consume the instances from the input.
   */
  fun partition(snapshotInstances: Collection<InstanceObject>, deltaInstances: Collection<InstanceObject>) {
    if (isTerminalClassifier) {
      return
    }
    snapshotInstances.forEach { getClassifierSet(it, true)!!.addSnapshotInstanceObject(it) }
    deltaInstances.forEach {
      if (it.hasTimeData()) {
        // Note - we only add the instance allocation to our delta set if it is not already accounted for in the baseline snapshot.
        // Otherwise we would be double counting allocations.
        if (it.hasAllocTime() && it !in snapshotInstances) getClassifierSet(it, true)!!.addDeltaInstanceObject(it)
        if (it.hasDeallocTime()) getClassifierSet(it, true)!!.freeDeltaInstanceObject(it)
      }
      else {
        getClassifierSet(it, true)!!.addDeltaInstanceObject(it)
      }
    }
  }

  companion object {
    @JvmStatic
    fun<T: Any> of(key: (InstanceObject) -> T?, classify: (T) -> ClassifierSet) = Join(key, classify, Id)
  }
}