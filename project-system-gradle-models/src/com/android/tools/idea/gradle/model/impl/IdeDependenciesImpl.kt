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
package com.android.tools.idea.gradle.model.impl

import com.android.tools.idea.gradle.model.IdeDependencies
import com.android.tools.idea.gradle.model.IdeDependenciesCore
import com.android.tools.idea.gradle.model.IdeDependencyCore
import com.android.tools.idea.gradle.model.IdeLibraryModelResolver
import java.io.Serializable

/**
 * We need a sealed interface so that the model classes pass validation, we can't use the [IdeDependenciesCore] interface
 * as this is in a different package. We still need other references to this interface within com.android.tools.idea.gradle.model
 */
sealed interface IdeDependenciesCoreImpl: IdeDependenciesCore, Serializable

data class IdeDependenciesCoreDirect(
  override val dependencies: List<IdeDependencyCore>,
) : IdeDependenciesCoreImpl, Serializable {
  override fun lookup(ref: Int): IdeDependencyCore = dependencies[ref]
}

fun IdeDependenciesCore.lookupAll(indexes: List<Int>?): List<IdeDependencyCore>? {
  return indexes?.map { this.lookup(it) }
}

data class IdeDependenciesCoreRef(
  val referee: IdeDependenciesCoreDirect,
  val index: Int,
  override val dependencies: List<IdeDependencyCore> = transitiveClosure(referee.lookup(index), referee),
) : IdeDependenciesCoreImpl, Serializable {
  override fun lookup(ref: Int): IdeDependencyCore = referee.lookup(ref)
}

fun transitiveClosure(rootDependency: IdeDependencyCore, classpath: IdeDependenciesCoreDirect): List<IdeDependencyCore> {
  val result = LinkedHashSet<IdeDependencyCore>()
  val queue = ArrayDeque(classpath.lookupAll(rootDependency.dependencies).orEmpty())
  while (queue.isNotEmpty()) {
    val value = queue.removeFirst()
    if (result.contains(value)) continue
    result.add(value)
    queue.addAll(classpath.lookupAll(value.dependencies).orEmpty())
  }

  return result.toList()
}

data class IdeDependenciesImpl(
  private val classpath: IdeDependenciesCore,
  override val resolver: IdeLibraryModelResolver
) : IdeDependencies {
  override val libraries by lazy { classpath.dependencies.flatMap { resolver.resolve(it) } }
  override val unresolvedDependencies = classpath.dependencies
  override val lookup: (Int) -> IdeDependencyCore = { classpath.lookup(it) }
}

fun throwingIdeDependencies(): IdeDependenciesCoreImpl {
  return IdeDependenciesCoreDirect(object : List<IdeDependencyCoreImpl> {
    override val size: Int get() = unexpected()
    override fun get(index: Int): IdeDependencyCoreImpl = unexpected()
    override fun indexOf(element: IdeDependencyCoreImpl): Int = unexpected()
    override fun isEmpty(): Boolean = unexpected()
    override fun iterator(): Iterator<IdeDependencyCoreImpl> = unexpected()
    override fun listIterator(): ListIterator<IdeDependencyCoreImpl> = unexpected()
    override fun listIterator(index: Int): ListIterator<IdeDependencyCoreImpl> = unexpected()
    override fun subList(fromIndex: Int, toIndex: Int): List<IdeDependencyCoreImpl> = unexpected()
    override fun lastIndexOf(element: IdeDependencyCoreImpl): Int = unexpected()
    override fun containsAll(elements: Collection<IdeDependencyCoreImpl>): Boolean = unexpected()
    override fun contains(element: IdeDependencyCoreImpl): Boolean = unexpected()

    private fun unexpected(): Nothing {
      error("Should not be called")
    }

  })
}
