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
@file:JvmName("Bleak")
package com.android.tools.idea.bleak

import com.android.tools.idea.bleak.HeapGraph.Companion.jniHelper
import com.android.tools.idea.bleak.expander.ArrayObjectIdentityExpander
import com.android.tools.idea.bleak.expander.ClassLoaderExpander
import com.android.tools.idea.bleak.expander.ClassStaticsExpander
import com.android.tools.idea.bleak.expander.DefaultObjectExpander
import com.android.tools.idea.bleak.expander.ElidingExpander
import com.android.tools.idea.bleak.expander.Expander
import com.android.tools.idea.bleak.expander.ExpanderChooser
import com.android.tools.idea.bleak.expander.RootExpander
import java.time.Duration
import java.util.function.Supplier

/**
 * BLeak checks for memory leaks by repeatedly running a test that returns to its original state, taking
 * and analyzing memory snapshots between each run. It looks for paths from GC roots through the heap that
 * terminate in objects that are consistently growing (i.e., have an increasing number of outgoing
 * references) each iteration.
 *
 * Initially, all non-primitive arrays are marked as potentially growing, since these are the only objects
 * that can theoretically grow indefinitely. On subsequent iterations, the shortest path to each growing
 * object in the previous iteration is found, and the corresponding path is followed (if it exists) in the
 * new graph. If the degree of the node at the tip of this path has increased, it is marked as growing in
 * the new graph. This process whittles down the set of consistently growing nodes (called "leak roots"),
 * ideally discarding any growth unrelated to the operations under test.
 *
 * Based on "BLeak: Automatically Debugging Memory Leaks in Web Applications" by John Vilk and Emery D. Berger
 */

fun runWithBleak(options: BleakOptions, scenario: Runnable) {
  runWithBleak(options) { scenario.run() }
}

fun runWithBleak(options: BleakOptions, scenario: () -> Unit) {
  val result = iterativeLeakCheck(options, scenario)
  if (!result.success) {
    throw MemoryLeakDetectedError(result.errorMessage)
  }
}

class MainBleakCheck(ignoreList: IgnoreList<LeakInfo>,
                     knownIssues: IgnoreList<LeakInfo> = IgnoreList(),
                     customExpanderSupplier: Supplier<List<Expander>>,
                     private val forbiddenObjects: List<Any> = listOf(),
                     private val dominatorTimeout: Duration = Duration.ofSeconds(60)):
  BleakCheck<() -> ExpanderChooser, LeakInfo>({ getExpanderChooser(customExpanderSupplier) }, ignoreList, knownIssues) {
  lateinit var g1: HeapGraph
  lateinit var g2: HeapGraph
  var leaks: List<LeakInfo> = listOf()

  private fun buildGraph(firstRun: Boolean = false) = HeapGraph(options(), forbiddenObjects).expandWholeGraph(firstRun)

  override fun firstIterationFinished() {
    g1 = buildGraph(true)
  }

  override fun middleIterationFinished() {
    g2 = buildGraph()
    g1.propagateGrowing(g2)
    g1 = g2
  }

  override fun lastIterationFinished() {
    g2 = buildGraph()
    g1.propagateGrowing(g2)
    leaks = g2.getLeaks(g1, dominatorTimeout)
  }

  override fun getResults() = leaks;

}

// get a new ExpanderChooser instance each time, since some Expanders may hold references to Nodes from
// the graphs (notably the label to node maps in ArrayObjectIdentityExpander). Using a single instance
// of ArrayObjectIdentityExpander across all iterations would keep all of the graphs in memory at once.
private fun getExpanderChooser(customExpanderSupplier: Supplier<List<Expander>>) = ExpanderChooser(listOf(
  RootExpander(),
  ArrayObjectIdentityExpander(),
  ClassLoaderExpander(jniHelper),
  ClassStaticsExpander()) +
  ElidingExpander.getExpanders() +
  customExpanderSupplier.get() +
  listOf(DefaultObjectExpander()))


private fun iterativeLeakCheck(options: BleakOptions, scenario: () -> Unit): BleakResult {
  scenario()  // warm up
  options.checks.forEach { it.firstIterationFinished() }
  for (i in 0 until options.iterations - 1) {
    scenario()
    options.checks.forEach { it.middleIterationFinished() }
  }
  scenario()
  options.checks.forEach { it.lastIterationFinished() }
  return BleakResult(options.checks)
}
