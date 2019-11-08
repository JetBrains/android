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
package com.android.tools.idea.tests.gui.framework.heapassertions.bleak

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
  result.knownIssues.takeUnless { it.isEmpty() }?.let {
    println("${it.size} known issue(s) found when running the test with BLeak.")
  }
}

private fun iterativeLeakCheck(options: BleakOptions, scenario: () -> Unit): BleakResult {
  scenario()  // warm up
  var g1 = HeapGraph(options.getExpanderChooser()).expandWholeGraph(initialRun = true)
  scenario()
  var g2 = HeapGraph(options.getExpanderChooser()).expandWholeGraph()
  g1.propagateGrowing(g2)
  for (i in 0 until options.iterations - 1) {
    scenario()
    if (options.useIncrementalPropagation) {
      g1 = HeapGraph(options.getExpanderChooser())
      g2.propagateGrowingIncremental(g1)
    }
    else {
      g1 = HeapGraph(options.getExpanderChooser()).expandWholeGraph()
      g2.propagateGrowing(g1)
    }
    g2 = g1
  }
  scenario()
  val finalGraph = HeapGraph(options.getExpanderChooser()).expandWholeGraph()
  g2.propagateGrowing(finalGraph)
  return BleakResult(finalGraph.getLeaks(g2, options), finalGraph.disposerInfo.growingCounts)
}
