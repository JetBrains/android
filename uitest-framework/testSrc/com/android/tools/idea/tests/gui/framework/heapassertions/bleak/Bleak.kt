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
 * Finally, the leak roots are evaluated for severity. Currently two similar metrics are available:
 * retained size and "leak share". Leak share is like retained size, except objects that are reachable via
 * multiple leak roots (but are not reachable from non-leak roots) have their size contribution split
 * equally among the roots from which they're reachable.
 *
 * Based on "BLeak: Automatically Debugging Memory Leaks in Web Applications" by John Vilk and Emery D. Berger
 */

// Whitelist for known issues: don't report leak roots for which this method returns true
fun Signature.isWhitelisted(): Boolean =
  anyTypeContains("com.intellij.testGuiFramework") ||
  entry(2) == "com.android.layoutlib.bridge.impl.DelegateManager#sJavaReferences" ||
  anyTypeContains("org.fest.swing") ||
  entry(-3) == "com.intellij.util.ref.DebugReflectionUtil#allFields" ||
  entry(-2) == "java.util.concurrent.ForkJoinPool#workQueues" ||
  entry(-4) == "java.io.DeleteOnExitHook#files" ||

  // don't report growing weak maps. Nodes whose weak referents have been GC'd will be removed from the map during some future map operation.
  entry(-3) == "com.intellij.util.containers.ConcurrentWeakHashMap#myMap" && lastType() == "[Ljava.util.concurrent.ConcurrentHashMap\$Node;" ||
  entry(-3) == "com.intellij.util.containers.ConcurrentWeakKeyWeakValueHashMap#myMap" ||
  entry(-3) == "com.intellij.util.containers.WeakHashMap#myMap" && lastType() == "[Ljava.lang.Object;" ||

  entry(-4) == "com.android.tools.idea.configurations.ConfigurationManager#myCache" ||
  entry(-4) == "com.maddyhome.idea.copyright.util.NewFileTracker#newFiles" || // b/126417715
  entry(-3) == "com.intellij.openapi.vfs.newvfs.impl.VfsData\$Segment#myObjectArray" ||
  entry(-4) == "com.intellij.openapi.vcs.impl.FileStatusManagerImpl#myCachedStatuses" ||
  entry(-4) == "com.intellij.util.indexing.VfsAwareMapIndexStorage#myCache" ||
  entry(-3) == "com.intellij.util.indexing.IndexingStamp#myTimestampsCache" ||
  entry(-3) == "com.intellij.util.indexing.IndexingStamp#ourFinishedFiles" ||
  entry(-3) == "com.intellij.openapi.fileEditor.impl.EditorWindow#myRemovedTabs" ||
  entry(-3) == "com.intellij.notification.EventLog\$ProjectTracker#myInitial" ||
  entry(2) == "sun.java2d.Disposer#records" ||
  entry(2) == "sun.java2d.marlin.OffHeapArray#REF_LIST" ||
  entry(2) == "sun.awt.X11.XInputMethod#lastXICFocussedComponent" || // b/126447315
  entry(-3) == "sun.font.XRGlyphCache#cacheMap" ||
  lastLabel() in listOf("_set", "_values") && entry(-4) == "com.intellij.openapi.util.Disposer#ourTree" ||  // this accounts for both myObject2NodeMap and myRootObjects
  entry(-3) == "com.intellij.openapi.application.impl.ReadMostlyRWLock#readers"

fun runWithBleak(scenario: Runnable) {
  runWithBleak { scenario.run() }
}

fun runWithBleak(runs: Int = 3, scenario: () -> Unit) {
  val errorMessage = findLeaks(runs, scenario).filterNot { it.whitelisted }.joinToString(separator = "\n------------------------------\n")
  if (errorMessage.isNotEmpty()) {
    throw MemoryLeakDetectedError(mangleSunReflect(errorMessage))
  }
}

private val USE_INCREMENTAL_PROPAGATION = System.getProperty("bleak.incremental.propagation") == "true"

fun findLeaks(runs: Int = 3, scenario: () -> Unit): List<LeakInfo> {
  scenario()  // warm up
  if (System.getProperty("enable.bleak") != "true") return listOf() // if BLeak isn't enabled, the test will run normally.

  var g1 = HeapGraph { isRootNode || obj.javaClass.isArray }.expandWholeGraph()
  scenario()
  var g2 = HeapGraph().expandWholeGraph()
  g1.propagateGrowing(g2)
  for (i in 0 until runs - 1) {
    scenario()
    if (USE_INCREMENTAL_PROPAGATION) {
      g1 = HeapGraph()
      g2.propagateGrowingIncremental(g1)
    }
    else {
      g1 = HeapGraph().expandWholeGraph()
      g2.propagateGrowing(g1)
    }
    g2 = g1
  }
  scenario()
  val finalGraph = HeapGraph().expandWholeGraph()
  g2.propagateGrowing(finalGraph)

  return finalGraph.getLeaks(g2)
}

// Ant filters out lines in exception messages that contain 'sun.reflect', among other things. I have so far been unable
// to turn this off, though in principle it's configurable. For now, intentionally misspell 'sun.reflect' to avoid this.
private fun mangleSunReflect(s: String) = s.replace("sun.reflect", "sun.relfect")
