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
package com.android.tools.idea.stats

import com.intellij.util.ref.DebugReflectionUtil
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import java.lang.reflect.Modifier

// An object has overhead for header and v-table entry
private const val ESTIMATED_OBJECT_OVERHEAD = 16

// An array has additional overhead for the array size
private const val ESTIMATED_ARRAY_OVERHEAD = 8

// Each reference will take 8 bytes in a 64 bit JVM
private const val REFERENCE_SIZE = 8

/**
 * Utility for computing the approximate memory size of the data referenced by a single root or multiple roots.
 *
 * Specify any classes that the utility should include with [includedClasses] and the [includedPackagePrefixes],
 * but ignore all classes in [excludedClasses]. All arrays are included by default.
 */
open class MemoryProbe(
  private val includedPackagePrefixes: List<String> = emptyList(),
  private val includedClasses: List<Class<*>> = emptyList(),
  private val excludedClasses: List<Class<*>> = emptyList(),
  private val excludeStaticFields: Boolean = false,
  private val cancelled: () -> Boolean = { false }
) {
  private val fieldCountMap = mutableMapOf<Class<*>, Int>()
  private val visited = IntOpenHashSet()
  private var size = 0L

  /**
   * Set to true if the probe was cancelled at any time.
   */
  var wasCancelled = false

  /**
   * Return the memory referenced by a single [root].
   */
  fun check(root: Any): Long =
    check(mapOf(root to "Root"))

  /**
   * Return the memory referenced by multiple [startRoots]. Give each root a name for debugging purposes.
   */
  fun check(startRoots: Map<Any, String>): Long {
    reset()
    DebugReflectionUtil.walkObjects(
      10000, startRoots, Object::class.java, { include(it) }, { obj, ref -> add(obj, ref) })
    return size
  }

  private fun include(obj: Any): Boolean =
    (obj.javaClass.isPrimitive ||
     obj.javaClass.isArray ||
     includedClasses.contains(obj.javaClass) ||
     includedPackagePrefixes.any { obj.javaClass.name.startsWith(it) }) &&
    excludedClasses.none { it.isInstance(obj) } &&
    visited.add(System.identityHashCode(obj)) &&
    !isCancelled()

  private fun isCancelled(): Boolean {
    if (wasCancelled) {
      return true
    }
    if (!cancelled()) {
      return false
    }
    wasCancelled = true
    return true
  }

  private fun reset() {
    size = 0L
    wasCancelled = false
    visited.clear()
  }

  @Suppress("UNUSED_PARAMETER") // Keep ref for debugging
  private fun add(obj: Any, ref: DebugReflectionUtil.BackLink<*>): Boolean {
    val fields = fields(obj.javaClass)
    val arraySize = arraySize(obj)
    size += ESTIMATED_OBJECT_OVERHEAD + fields * REFERENCE_SIZE + arraySize
    if (excludeStaticFields) {
      excludeStaticFields(obj, obj.javaClass)
    }
    return true
  }

  private fun fields(javaClass: Class<*>): Int {
    fieldCountMap[javaClass]?.let { return it }
    val size = javaClass.declaredFields.count { !Modifier.isStatic(it.modifiers) } + (javaClass.superclass?.let { fields(it) } ?: 0)
    fieldCountMap[javaClass] = size
    return size
  }

  private fun excludeStaticFields(obj: Any, javaClass: Class<*>) {
    visited.addAll(javaClass.declaredFields
                     .filter { Modifier.isStatic(it.modifiers) }
                     .mapNotNull {
                       try {
                         System.identityHashCode(it.apply { it.isAccessible = true }.get(obj))
                       }
                       catch (ex: Exception) {
                         null
                       }
                     }
    )
    javaClass.superclass?.let { excludeStaticFields(obj, it) }
  }

  private fun arraySize(obj: Any): Int {
    if (!obj.javaClass.isArray) {
      return 0
    }
    val componentCount = java.lang.reflect.Array.getLength(obj)
    if (componentCount == 0) {
      return 0
    }
    val componentSize = when (obj.javaClass.componentType) {
      java.lang.Byte.TYPE -> 1
      java.lang.Boolean.TYPE -> 1
      java.lang.Short.TYPE -> 2
      Character.TYPE -> 2
      Integer.TYPE -> 4
      java.lang.Float.TYPE -> 4
      else -> 8 // Assume 64 bit JVM
    }
    val size = componentCount * componentSize
    // The expression: (size + 7) and 7.inv() adds bytes to fill a full word (8 bytes)
    return ESTIMATED_ARRAY_OVERHEAD + (size + 7) and 7.inv()
  }
}
