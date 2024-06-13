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
@file:JvmName("HeapAssertions")
@file:TestOnly
package com.android.tools.idea.tests.gui.framework.heapassertions

import com.google.common.annotations.VisibleForTesting
import com.intellij.ide.ProhibitAWTEvents
import com.intellij.openapi.CompositeDisposable
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LeakHunter
import com.intellij.util.PairProcessor
import com.intellij.util.io.PersistentEnumeratorCache
import com.intellij.util.ref.DebugReflectionUtil
import com.intellij.util.ui.UIUtil
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet
import org.jetbrains.annotations.TestOnly
import java.io.Closeable
import java.util.*
import javax.swing.SwingUtilities

/**
 * Checks for weak/soft hash maps preventing their entries from being collected:
 * - for [java.util.WeakHashMap] and subclasses of [com.intellij.util.containers.RefHashMap] this means values referencing keys
 * - for subclasses of [com.intellij.util.containers.RefValueHashMap] this means keys referencing values.
 */
fun checkWeakSoftRefMaps(enforce: Boolean = false) {
  class PathFound(path: String) : Exception(path)

  val refHashMapClass = Class.forName("com.intellij.util.containers.RefHashMap").kotlin
  val refValueHashMapClass = Class.forName("com.intellij.util.containers.RefValueHashMap").kotlin

  val roots = LeakHunter.allRoots().get()
  roots[extraRoots] = "test-only extra roots"
  roots.remove(Disposer.getTree()) // for now, remove the disposer tree as it might lead to confusion.

  LeakCollector("Weak/Soft Map Checker", enforce).use {
    traverseObjectGraph(
      roots,
      Map::class.java,
      objectMatches = { it is WeakHashMap<*, *> || refHashMapClass.isInstance(it) || refValueHashMapClass.isInstance(it) }
    ) { map, backlink ->
      val (sources, sinks, description) = when {
        map is WeakHashMap<*, *> || refHashMapClass.isInstance(map) ->
          Triple(map.values, ReferenceOpenHashSet(map.keys), "Strong-referenced values: $backlink")
        else ->
          Triple(map.keys, ReferenceOpenHashSet(map.values), "Strong-referenced keys: $backlink")
      }
      try {
        traverseObjectGraph(
          mapOf(sources.filterNotNull() as Any to description),
          Any::class.java,
          objectMatches = { it in sinks },
          processor = { leak, path -> throw PathFound("Weak-referenced $leak; reached $path.toString()") })
      }
      catch (p: PathFound) {
        it.registerLeak(map, p.message.toString())
      }
    }
  }
}

/**
 * Checks for reachable but disposed objects in the heap.
 */
fun checkReachableDisposed(enforce: Boolean = false) {
  LeakCollector("Disposed instances", enforce).use {
    val roots = LeakHunter.allRoots().get()
    roots[extraRoots] = "test-only extra roots"
    roots.remove(Disposer.getTree()) // for now, remove the disposer tree as it might lead to confusion.

    traverseObjectGraph(
      roots,
      Any::class.java,
      objectMatches = { it is Disposable && Disposer.isDisposed(it) },
      // CompositeDisposable allows breaking this invariant: "this class improves on the memory usage by not creating temporary objects
      // inside Disposer.", trading registrations inside Disposer for possibly longer-lived, already disposed objects.
      // For example, it is used by RootModelImpl to store all content roots.
      shouldFollowValue = { it !is CompositeDisposable },
      processor = it::registerLeak
    )
  }
}

/**
 * Traverses the object graph starting at [roots], matching instances of class [suspectClass]. At each step, determine whether to continue
 * the traversal iff [shouldFollowValue] returns true: this allows pruning entire sub-graphs if their dominator is a known offender.
 * A leak is identified iff [objectMatches] returns, in which case the offending value and its back link (the reachable chain of object
 * references, all the way back to a GC root) are offered to [processor] for further action.
 */
inline fun <reified T> traverseObjectGraph(roots: Map<Any, String>,
                                           suspectClass: Class<T>,
                                           crossinline shouldFollowValue: (Any) -> Boolean = { true },
                                           crossinline objectMatches: (T) -> Boolean,
                                           crossinline processor: (T, Any) -> Unit) {
  val alreadyReported = ReferenceOpenHashSet<Any>()
  ApplicationManager.getApplication().runReadAction {
    ProhibitAWTEvents.start("checking for leaks").use {
      DebugReflectionUtil.walkObjects(
        10000, roots, suspectClass, { shouldFollowValue(it) },
        PairProcessor { value, backLink ->
          if (objectMatches(value as T) && alreadyReported.add(value)) processor(value, backLink)
          true // keep reporting, don't stop at the first occurrence
        })
    }
  }
}

@VisibleForTesting
internal val extraRoots = mutableListOf<Any>()

class HeapAssertionError(message: String) : AssertionError(message)

// TODO: Need a way to ignore known/existing issues
private class LeakCollector(val description: String, val enforce: Boolean = false) : Closeable {

  // We look for problems involving at least one class from the packages defined below, as a simple way to ignore platform oddities.
  private val packagesToMatch = listOf("com.android.", "com.google.", "org.jetbrains.android.", "org.jetbrains.kotlin.android.")
  private val issues = mutableListOf<String>()

  init {
    // Before searching for leaks it's a good idea to dispatch all postprocessing (might clear some disposables as well).
    when {
      SwingUtilities.isEventDispatchThread() -> UIUtil.dispatchAllInvocationEvents()
      else -> UIUtil.pump()
    }
    PersistentEnumeratorCache.clearCacheForTests()
  }

  fun registerLeak(leaked: Any, backlink: Any) {
    val path = backlink.toString()
    if (packagesToMatch.any { it in path }) {
      issues.add(buildString {
        appendLine("Found ${leaked.javaClass}: $leaked; hash: ${System.identityHashCode(leaked)})")
        appendLine(backlink)
      })
    }
  }

  override fun close() {
    if (issues.isNotEmpty()) {
      val description = buildString {
        appendLine(description)
        appendLine()
        issues.forEachIndexed { index, issue ->
          appendLine("[$index]: $issue").appendLine()
        }
      }
      if (enforce) throw HeapAssertionError(description) else println(description)
    }
  }
}
