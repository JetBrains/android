/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.diagnostics.hprof.classstore

import com.android.tools.idea.diagnostics.hprof.navigator.RootReason
import com.android.tools.idea.diagnostics.hprof.parser.HProfEventBasedParser
import com.android.tools.idea.diagnostics.hprof.visitors.CollectRootReasonsVisitor
import com.android.tools.idea.diagnostics.hprof.visitors.CollectStringValuesVisitor
import com.android.tools.idea.diagnostics.hprof.visitors.CollectThreadInfoVisitor
import com.android.tools.idea.diagnostics.hprof.visitors.CompositeVisitor
import com.android.tools.idea.diagnostics.hprof.visitors.CreateClassStoreVisitor
import it.unimi.dsi.fastutil.longs.Long2ObjectMap
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import java.util.function.LongUnaryOperator

class HProfMetadata(var classStore: ClassStore, // TODO: private-set, public-get
                    val threads: Long2ObjectMap<ThreadInfo>,
                    var roots: Long2ObjectMap<RootReason>) {

  class RemapException : Exception()

  fun remapIds(remappingFunction: LongUnaryOperator) {
    // Remap ids in class store
    classStore = classStore.createStoreWithRemappedIDs(remappingFunction)

    // Remap root objects' ids
    val newRoots = Long2ObjectOpenHashMap<RootReason>()
    for (entry in roots.long2ObjectEntrySet()) {
      val key = entry.longKey
      val value = entry.value
      try {
        val newKey = remappingFunction.applyAsLong(key)
        assert(!newRoots.containsKey(newKey))
        newRoots.put(newKey, value)
      }
      catch (e: RemapException) {
        // Ignore root entry if there is no associated object
      }
    }
    roots = newRoots
  }

  companion object {
    fun create(parser: HProfEventBasedParser): HProfMetadata {
      val stringIdMap = Long2ObjectOpenHashMap<String>()
      val threadsMap = Long2ObjectOpenHashMap<ThreadInfo>()

      val classStoreVisitor = CreateClassStoreVisitor(stringIdMap)
      val threadInfoVisitor = CollectThreadInfoVisitor(threadsMap, stringIdMap)
      val rootReasonsVisitor = CollectRootReasonsVisitor(threadsMap)

      val visitor = CompositeVisitor(
        CollectStringValuesVisitor(stringIdMap),
        classStoreVisitor,
        threadInfoVisitor,
        rootReasonsVisitor
      )
      parser.accept(visitor, "create hprof metadata")
      return HProfMetadata(classStoreVisitor.getClassStore(),
                           threadsMap,
                           rootReasonsVisitor.roots)
    }
  }
}