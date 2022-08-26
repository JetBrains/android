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
package com.android.tools.idea.diagnostics.hprof.analysis

import com.android.tools.idea.diagnostics.hprof.histogram.Histogram
import com.android.tools.idea.diagnostics.hprof.navigator.ObjectNavigator
import com.android.tools.idea.diagnostics.hprof.util.IntList
import com.android.tools.idea.diagnostics.hprof.util.UByteList
import gnu.trove.TIntHashSet
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntArrayList

class AnalysisContext(
  val navigator: ObjectNavigator,
  val config: AnalysisConfig,
  val parentList: IntList,
  val sizesList: IntList,
  val visitedList: IntList,
  val refIndexList: UByteList,
  var histogram: Histogram
) {
  val classStore = navigator.classStore
  val disposedObjectsIDs = TIntHashSet()
  val disposerParentToChildren = Int2ObjectOpenHashMap<IntArrayList>()
  var diposerTreeObjectId = 0
}