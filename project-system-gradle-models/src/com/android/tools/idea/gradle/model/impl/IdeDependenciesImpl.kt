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
import com.android.tools.idea.gradle.model.IdeLibrary
import java.io.Serializable

/**
 * We need a sealed interface so that the model classes pass validation, we can't use the [IdeDependenciesCore] interface
 * as this is in a different package. We still need other references to this interface within com.android.tools.idea.gradle.model
 */
sealed interface IdeDependenciesCoreImpl: IdeDependenciesCore, Serializable

data object ThrowingIdeDependencies : IdeDependenciesCoreImpl {
  override fun lookup(ref: Int): IdeDependencyCore { error("Should not be called") }
  override val dependencies: List<IdeDependencyCoreImpl> get() { error("Should not be called") }

  // Make sure the serialization always returns this singleton
  private fun readResolve(): Any = ThrowingIdeDependencies
}


data class IdeDependenciesCoreDirect(
  override val dependencies: List<IdeDependencyCoreImpl>,
) : IdeDependenciesCoreImpl, Serializable {
  override fun lookup(ref: Int): IdeDependencyCore = dependencies[ref]
}

fun IdeDependenciesCore.lookupAll(indexes: List<Int>?): List<IdeDependencyCoreImpl>? {
  return indexes?.map { this.lookup(it) as IdeDependencyCoreImpl}
}

data class IdeDependenciesCoreRef(
  val referee: IdeDependenciesCoreDirect,
  val index: Int,
  override val dependencies: List<IdeDependencyCoreImpl> = transitiveClosure(referee.lookup(index), referee),
) : IdeDependenciesCoreImpl, Serializable {
  override fun lookup(ref: Int): IdeDependencyCore = referee.lookup(ref)
}

fun transitiveClosure(rootDependency: IdeDependencyCore, classpath: IdeDependenciesCoreDirect): List<IdeDependencyCoreImpl> {
  val result = LinkedHashSet<IdeDependencyCoreImpl>()
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
  private val classpath: IdeDependenciesCoreImpl,
  override val resolver: IdeLibraryModelResolverImpl
) : IdeDependencies {
  override val libraries: List<IdeLibrary> by lazy { classpath.dependencies.flatMap { resolver.resolve(it) } }
  override val unresolvedDependencies = classpath.dependencies
  override val lookup: (Int) -> IdeDependencyCore = { classpath.lookup(it) }
}

fun throwingIdeDependencies(): IdeDependenciesCoreImpl {
  return ThrowingIdeDependencies
}
