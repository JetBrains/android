/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.model

import com.intellij.openapi.Disposable
import java.util.Spliterator
import java.util.function.Consumer
import java.util.stream.Stream

interface PsModelCollection<T> : Collection<T> {
  val items: Collection<T>
  fun forEach(consumer: (T) -> Unit) = forEach(Consumer { consumer(it) })
  fun onChange(disposable: Disposable, listener: () -> Unit)

  override val size: Int get() = items.size
  override fun contains(element: T): Boolean = items.contains(element)
  override fun containsAll(elements: Collection<T>): Boolean = items.containsAll(elements)
  override fun isEmpty(): Boolean = items.isEmpty()
  override fun iterator(): Iterator<T> = items.iterator()
  override fun parallelStream(): Stream<T> = items.parallelStream()
  override fun spliterator(): Spliterator<T> = items.spliterator()
  override fun stream(): Stream<T> = items.stream()
}

interface PsKeyedModelCollection<KeyT, T> : PsModelCollection<T> {
  val entries: Map<KeyT, T>
  fun findElement(key: KeyT): T?
}
