/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.adtui.swing

import org.jetbrains.annotations.TestOnly
import java.awt.Component
import java.awt.Container

/**
 * Returns the first descendant satisfying the given [predicate] by doing breadth-first
 * search starting from `this` [Component], or `null` if no components satisfy the [predicate].
 */
@TestOnly
inline fun <reified T: Any> Component.findDescendant(crossinline predicate: (T) -> Boolean = { true }): T? =
  findDescendant(T::class.java) { predicate(it) }

/**
 * Returns the first descendant of type [type] satisfying the given [predicate] by doing breadth-first
 * search starting from `this` [Component], or `null` if no components satisfy the [predicate].
 */
@TestOnly
fun <T: Any> Component.findDescendant(type: Class<T>, predicate: (T) -> Boolean = { true }): T? =
  findAllDescendants(type, predicate).firstOrNull()

/**
 * Returns the first descendant satisfying the given [predicate] by doing breadth-first
 * search starting from `this` [Component].
 *
 * @throws NoSuchElementException if no components satisfy the [predicate].
 */
@TestOnly
inline fun <reified T: Any> Component.getDescendant(crossinline predicate: (T) -> Boolean = { true }): T =
  getDescendant(T::class.java) { predicate(it) }

/**
 * Returns the first descendant of type [type] satisfying the given [predicate] by doing breadth-first
 * search starting from `this` [Component].
 *
 * @throws NoSuchElementException if no components satisfy the [predicate].
 */
@TestOnly
fun <T: Any> Component.getDescendant(type: Class<T>, predicate: (T) -> Boolean = { true }): T =
  findDescendant(type, predicate) ?: throw NoSuchElementException("Unable to find ${type.javaClass.name} satisfying $predicate")

/**
 * Returns a [Sequence] of descendants satisfying the given [predicate] by doing breadth-first
 * search starting from `this` [Component].
 */
@TestOnly
inline fun <reified T: Any> Component.findAllDescendants(crossinline predicate: (T) -> Boolean = { true }): Sequence<T> =
  findAllDescendants(T::class.java) { predicate(it) }

/**
 * Returns a [Sequence] of descendants of type [type] satisfying the given [predicate] by doing breadth-first
 * search starting from `this` [Component].
 */
@TestOnly
@Suppress("UNCHECKED_CAST") // Checked, just not recognized.
fun <T: Any> Component.findAllDescendants(type: Class<T>, predicate: (T) -> Boolean = {true}) : Sequence<T> = sequence {
  val root = this@findAllDescendants
  if (type.isInstance(root) && predicate(root as T)) yield(root)
  if (root is Container) {
    val queue = ArrayDeque<Container>().also { it.add(root) }
    while (queue.isNotEmpty()) {
      for (child in queue.removeFirst().components) {
        if (type.isInstance(child) && predicate(child as T)) yield(child)
        if (child is Container) queue.add(child)
      }
    }
  }
}
