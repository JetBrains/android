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
package com.android.tools.idea.tests.gui.framework.heapassertions.bleak

import java.io.File
import java.io.PrintStream
import java.util.IdentityHashMap

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
private fun Signature.isWhitelisted(): Boolean =
  first() == "com.intellij.testGuiFramework.remote.client.JUnitClientImpl\$ClientSendThread#objectOutputStream" ||
  first() == "com.android.layoutlib.bridge.impl.DelegateManager#sJavaReferences" ||
  first() == "android.graphics.NinePatch_Delegate#sChunkCache" ||
  anyTypeContains("org.fest.swing") ||
  entry(-3) == "com.intellij.util.ref.DebugReflectionUtil#allFields" ||
  entry(-2) == "java.util.concurrent.ForkJoinPool#workQueues" ||

  // don't report that the total number of loaded classes has been increasing - this is generally expected.
  size == 4 && first() == "java.lang.Thread#contextClassLoader" && entry(1) == "com.intellij.util.lang.UrlClassLoader#classes" && label(2) == "elementData" ||
  size == 3 && first() == "com.intellij.util.lang.UrlClassLoader#classes" && label(1) == "elementData" ||
  // don't report growing weak maps. Nodes whose weak referents have been GC'd will be removed from the map during some future map operation.
  entry(-3) == "com.intellij.util.containers.ConcurrentWeakHashMap#myMap" && lastType() == "[Ljava.util.concurrent.ConcurrentHashMap\$Node;" ||
  entry(-3) == "com.intellij.util.containers.WeakHashMap#myMap" && lastType() == "[Ljava.lang.Object;" ||

  entry(-4) == "com.maddyhome.idea.copyright.util.NewFileTracker#newFiles" || // b/126417715
  entry(-3) == "com.intellij.openapi.vfs.newvfs.impl.VfsData\$Segment#myObjectArray" ||
  entry(-4) == "com.intellij.openapi.vcs.impl.FileStatusManagerImpl#myCachedStatuses" ||
  entry(-4) == "com.intellij.util.indexing.VfsAwareMapIndexStorage#myCache" ||
  first() == "sun.java2d.marlin.OffHeapArray#REF_LIST" ||
  first() == "sun.awt.X11.XInputMethod#lastXICFocussedComponent" // b/126447315

// "Troublesome" signatures are whitelisted as well, but are removed from the set of leak roots before leakShare is determined, rather
// than after.
fun Signature.isTroublesome(): Boolean =
  size == 4 && lastLabel() in listOf("_set", "_values") && first() == "com.intellij.openapi.util.Disposer#ourTree" ||  // this accounts for both myObject2NodeMap and myRootObjects
  entry(-3) == "com.intellij.openapi.application.impl.ReadMostlyRWLock#readers"

private var currentLogPrinter: PrintStream? = null
var currentLogFile: File? = null
  set(f) {
    currentLogPrinter = if (f != null) PrintStream(f) else null
  }

private fun log(s: String) {
  println(s)
  currentLogPrinter?.println(s)
}

fun runWithBleak(scenario: Runnable) {
  runWithBleak { scenario.run() }
}

private val USE_INCREMENTAL_PROPAGATION = System.getProperty("bleak.incremental.propagation") == "true"

private fun runWithBleak(runs: Int = 3, scenario: () -> Unit) {
  scenario()  // warm up
  if (System.getProperty("enable.bleak") != "true") return  // if BLeak isn't enabled, the test will run normally.

  currentLogPrinter?.use {
    var g1 = HeapGraph { obj.javaClass.isArray }.expandWholeGraph()
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

    log("Found ${finalGraph.leakRoots.size} leak roots")
    val errorMessage = buildString {
      for ((leakRoot, leakShare) in finalGraph.rankLeakRoots().filterNot { it.first.getPath().signature().isWhitelisted() }) {
        appendln("Root with leakShare $leakShare:")
        appendln(leakRoot.getPath().verboseSignature().joinToString(separator = "\n  ", prefix = "  "))
        val prevLeakRoot = g2.getNodeForPath(leakRoot.getPath()) ?: g2.leakRoots.find { it.obj === leakRoot.obj }
        if (prevLeakRoot != null) {
          val prevChildrenObjects = IdentityHashMap<Any, Any>(prevLeakRoot.degree)
          prevChildrenObjects.putAll(prevLeakRoot.children.map { it.obj to it.obj })  // use map as a set
          leakRoot.children.filterNot { prevChildrenObjects.containsKey(it.obj) }.take(20).forEach {
            try {
              appendln("    Added object: ${it.type.name}: ${it.obj.toString().take(80)}")
            }
            catch (e: NullPointerException) {
              appendln("    Added object: ${it.type.name} [NPE in toString]")
            }
          }
        }
        else {
          appendln("Warning: path and object have both changed between penultimate and final snapshots for this root")
        }
        appendln("--------------------------------")
      }
    }
    if (errorMessage.isNotEmpty()) {
      throw MemoryLeakDetectedError(errorMessage)
    }
  }
}