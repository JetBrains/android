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
package com.android.tools.idea.bleak

import com.android.tools.idea.bleak.DisposerInfo.Companion.ROOT_PLACEHOLDER
import com.android.tools.idea.testing.DisposerExplorer
import com.intellij.openapi.Disposable

/**
 * [DisposerInfo] tracks child counts by class for each [Disposable]. This class-level granularity is not
 * exposed by the normal Bleak check, and is more robust in the face of removal of unrelated children (that
 * is, if a Disposable loses a child of type A and gains one of type B over the course of one iteration, the
 * growth in the number of B children will be discovered by this check, but Bleak would discard the parent
 * as not growing).
 */
class DisposerInfo private constructor (val growingCounts: Map<Key, Int> = mapOf()) {

  class Key(val disposable: Disposable, val klass: Class<*>) {
    override fun equals(other: Any?): Boolean {
      if (other is Key) {
        return disposable === other.disposable && klass === other.klass
      }
      return false
    }

    override fun hashCode() = System.identityHashCode(disposable) xor System.identityHashCode(klass)
  }

  companion object {
    val ROOT_PLACEHOLDER = Disposable { }

    fun createBaseline() = DisposerInfo(getClassCountsMap())

    fun propagateFrom(prevDisposerInfo: DisposerInfo): DisposerInfo {
      val currentClassCounts = getClassCountsMap()
      val growingCounts = mutableMapOf<Key, Int>()
      prevDisposerInfo.growingCounts.forEach { key, prevCount ->
        val newCount = currentClassCounts[key]
        if (newCount != null && newCount > prevCount) {
          growingCounts[key] = newCount
        }
      }
      return DisposerInfo(growingCounts)
    }

    private fun getClassCountsMap(): MutableMap<Key, Int> {
      val counts = mutableMapOf<Key, Int>()
      DisposerExplorer.visitTree { disposable ->
        DisposerExplorer.getChildren(disposable).forEach { child ->
          counts.compute(Key(disposable, child.javaClass)) { k, v -> if (v == null) 1 else v + 1 }
        }
        DisposerExplorer.VisitResult.CONTINUE
      }
      DisposerExplorer.getTreeRoots().forEach { child ->
        counts.compute(Key(ROOT_PLACEHOLDER, child.javaClass)) { k, v -> if (v == null) 1 else v + 1 }
      }
      return counts
    }
  }

}

class DisposerLeakInfo(val k: DisposerInfo.Key, val count: Int) {
  override fun toString(): String {
    return if (k.disposable === ROOT_PLACEHOLDER) {
      "There are an increasing number ($count) of Disposer roots of type ${k.klass.name}"
    } else {
      "Disposable of type ${k.disposable.javaClass.name} has an increasing number ($count) of children of type ${k.klass.name}"
    }
  }
}

class DisposerCheck(w: IgnoreList<DisposerLeakInfo> = IgnoreList(), ki: IgnoreList<DisposerLeakInfo> = IgnoreList()): BleakCheck<Nothing?, DisposerLeakInfo>(null, w, ki) {
  private var disposerInfo: DisposerInfo? = null

  override fun firstIterationFinished() {
    disposerInfo = DisposerInfo.createBaseline()
  }

  override fun middleIterationFinished() {
    disposerInfo = DisposerInfo.propagateFrom(disposerInfo!!);
  }

  override fun lastIterationFinished() = middleIterationFinished()

  override fun getResults() = disposerInfo?.growingCounts?.map { (key, count) -> DisposerLeakInfo(key, count) } ?: listOf()

}
