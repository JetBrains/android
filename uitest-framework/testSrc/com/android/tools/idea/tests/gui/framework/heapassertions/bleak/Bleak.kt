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
  anyTypeContains("com.intellij.testGuiFramework") ||
  entry(2) == "com.android.layoutlib.bridge.impl.DelegateManager#sJavaReferences" ||
  anyTypeContains("org.fest.swing") ||
  entry(-3) == "com.intellij.util.ref.DebugReflectionUtil#allFields" ||
  entry(-2) == "java.util.concurrent.ForkJoinPool#workQueues" ||

  // don't report growing weak maps. Nodes whose weak referents have been GC'd will be removed from the map during some future map operation.
  entry(-3) == "com.intellij.util.containers.ConcurrentWeakHashMap#myMap" && lastType() == "[Ljava.util.concurrent.ConcurrentHashMap\$Node;" ||
  entry(-3) == "com.intellij.util.containers.WeakHashMap#myMap" && lastType() == "[Ljava.lang.Object;" ||

  entry(-4) == "com.maddyhome.idea.copyright.util.NewFileTracker#newFiles" || // b/126417715
  entry(-3) == "com.intellij.openapi.vfs.newvfs.impl.VfsData\$Segment#myObjectArray" ||
  entry(-4) == "com.intellij.openapi.vcs.impl.FileStatusManagerImpl#myCachedStatuses" ||
  entry(-4) == "com.intellij.util.indexing.VfsAwareMapIndexStorage#myCache" ||
  entry(-3) == "com.intellij.openapi.fileEditor.impl.EditorWindow#myRemovedTabs" ||
  entry(-3) == "com.intellij.notification.EventLog\$ProjectTracker#myInitial" ||
  entry(2) == "sun.java2d.Disposer#records" ||
  entry(2) == "sun.java2d.marlin.OffHeapArray#REF_LIST" ||
  entry(2) == "sun.awt.X11.XInputMethod#lastXICFocussedComponent" // b/126447315

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

private fun mostCommonClassesOf(objects: Collection<Any?>, maxResults: Int): List<Pair<Class<*>, Int>> {
  val classCounts = mutableMapOf<Class<*>, Int>()
  objects.forEach { if (it != null) classCounts.merge(it.javaClass, 1) { currentCount, _ -> currentCount + 1 } }
  return classCounts.toList().sortedByDescending { it.second }.take(maxResults)
}

private fun StringBuilder.appendRetainedObjectSummary(g: HeapGraph, nodes: Set<HeapGraph.Node>) {
  val retainedNodes = g.dominatedNodes(nodes)
  appendln("${retainedNodes.size} (${retainedNodes.fold(0) { acc, node -> acc + node.getApproximateSize() }} bytes)")
  mostCommonClassesOf(retainedNodes.map{it.obj}, 50).forEach {
    appendln("    ${it.second} ${it.first.name}")
  }
}

fun runWithBleak(runs: Int = 3, scenario: () -> Unit) {
  scenario()  // warm up
  if (System.getProperty("enable.bleak") != "true") return  // if BLeak isn't enabled, the test will run normally.

  currentLogPrinter?.use {
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

    log("Found ${finalGraph.leakRoots.size} leak roots")
    val errorMessage = buildString {
      for ((leakRoot, leakShare) in finalGraph.rankLeakRoots().filterNot { it.first.getPath().signature().isWhitelisted() }) {
        appendln("Root with leakShare $leakShare:")
        appendln(leakRoot.getPath().verboseSignature().joinToString(separator = "\n  ", prefix = "  "))
        val prevLeakRoot = g2.getNodeForPath(leakRoot.getPath()) ?: g2.leakRoots.find { it.obj === leakRoot.obj }
        if (prevLeakRoot != null) {
          val prevChildrenObjects = IdentityHashMap<Any, Any>(prevLeakRoot.degree)
          val newChildrenObjects = IdentityHashMap<Any, Any>(leakRoot.degree)
          prevChildrenObjects.putAll(prevLeakRoot.children.map { it.obj to it.obj })  // use map as a set
          newChildrenObjects.putAll(leakRoot.children.map { it.obj to it.obj })  // use map as a set
          val addedChildren = leakRoot.children.filterNot { prevChildrenObjects.containsKey(it.obj) }

          // print information about the newly added objects
          appendln(" ${leakRoot.degree} children (+${leakRoot.degree - prevLeakRoot.degree}) [${newChildrenObjects.size} distinct (+${newChildrenObjects.size- prevChildrenObjects.size})]. New children: ${addedChildren.size}")
          addedChildren.take(20).forEach {
            try {
              appendln("    Added object: ${it.type.name}: ${it.obj.toString().take(80)}")
            }
            catch (e: NullPointerException) {
              appendln("    Added object: ${it.type.name} [NPE in toString]")
            }
          }
          // if many objects are added, print summary information about their most common classes
          if (addedChildren.size > 20) {
            appendln("    ...")
            appendln("  Most common classes of added objects: ")
            mostCommonClassesOf(addedChildren.map{it.obj}, 5).forEach {
              appendln("    ${it.second} ${it.first.name}")
            }
          }

          // print information about objects retained by the added children:
          append("\nRetained by new children: ")
          appendRetainedObjectSummary(finalGraph, addedChildren.toSet())

          // print information about objects retained by all of the children. Sometimes severity is considerably underestimated by
          // just looking at the children added in the last iteration, since often the same heavy data structures (Projects, etc.) are held
          // by all of the leaked objects (and so are not retained by the last-iteration children alone, but are retained by all of the children
          // in aggregate). However, this may also overestimate the severity, since there can be many other objects in the array unrelated to the
          // actual leak in question.
          append("\nRetained by all children: ")
          appendRetainedObjectSummary(finalGraph, leakRoot.children.toSet())

        }
        else {
          appendln("Warning: path and object have both changed between penultimate and final snapshots for this root")
        }
        appendln("--------------------------------")
      }
    }
    if (errorMessage.isNotEmpty()) {
      throw MemoryLeakDetectedError(mangleSunReflect(errorMessage))
    }
  }
}

// Ant filters out lines in exception messages that contain 'sun.reflect', among other things. I have so far been unable
// to turn this off, though in principle it's configurable. For now, intentionally misspell 'sun.reflect' to avoid this.
private fun mangleSunReflect(s: String) = s.replace("sun.reflect", "sun.relfect")
