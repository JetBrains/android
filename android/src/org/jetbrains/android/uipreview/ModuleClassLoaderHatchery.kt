/*
 * Copyright (C) 2021 The Android Open Source Project
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
package org.jetbrains.android.uipreview

import com.android.tools.idea.rendering.classloading.ClassTransform
import com.intellij.openapi.util.Disposer
import java.util.LinkedList
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * How many different classloader types the hatchery stores. The current default is 2 with the idea of having:
 * 1. ModuleClassLoader for the static preview
 * 2. ModuleClassLoader for the interactive preview
 */
private const val CAPACITY = 2
/**
 * How many copies of the same classloader the hatchery maintains
 */
private const val COPIES = 1

/**
 * A storage of [ModuleClassLoader]s of the same type responsible for their preloading and replenishment. [ModuleClassLoader]s managed by
 * this storage do not get stale after updating only the user code since module dependencies should change for these [ModuleClassLoader]s
 * to become invalid.
 */
private class Clutch(private val cloner: (ModuleClassLoader) -> ModuleClassLoader?, donor: ModuleClassLoader, copies: Int = COPIES) {
  private val classesToPreload = donor.nonProjectLoadedClasses
  private val eggs = ConcurrentLinkedQueue<Preloader>()
  init {
    repeat(copies) {
      cloner(donor)?.let {
        eggs.add(Preloader(it, classesToPreload))
      }
    }
  }

  /**
   * Checks if the clutch maintains the [ModuleClassLoader]s of this type.
   */
  fun isCompatible(
    parent: ClassLoader?,
    projectTransformations: ClassTransform,
    nonProjectTransformations: ClassTransform) =
    eggs.peek()?.isForCompatible(parent, projectTransformations, nonProjectTransformations) ?: false

  /**
   * If possible, returns a [ModuleClassLoader] from the clutch and transfers full ownership to the caller, otherwise returns null.
   */
  fun retrieve(): ModuleClassLoader? {
    return generateSequence { eggs.poll()?.getClassLoader() }
      .firstOrNull {
        if (!it.isUserCodeUpToDate) {
          // This class loader can not be used, it's not up-to-date
          Disposer.dispose(it)
          false
        }
        else true
      }
      ?.let { compatibleClassLoader ->
        // Incubate the next one
        cloner(compatibleClassLoader)?.let { newClassLoader ->
          eggs.add(Preloader(newClassLoader, classesToPreload))
        }
        compatibleClassLoader
      }
  }

  /**
   * Should be called when the clutch is no longer needed to free all the resources.
   */
  fun destroy() {
    generateSequence { eggs.poll() }.forEach { it.cancel() }
  }

  fun getStats(): Stats {
    return Stats("Clutch ${this.hashCode()}", eggs.map { ReadyState(it.getLoadedCount(), classesToPreload.size) })
  }
}

/**
 * Data representing the identification of the [ModuleClassLoader] type.
 */
private data class Request(
  val parent: ClassLoader?,
  val projectTransformations: ClassTransform,
  val nonProjectTransformations: ClassTransform) {
  override fun equals(other: Any?): Boolean {
    if (other !is Request) {
      return false
    }
    if (other.parent != null && this.parent != null && other.parent != this.parent) {
      return false
    }
    return projectTransformations.id == other.projectTransformations.id &&
           nonProjectTransformations.id == other.nonProjectTransformations.id
  }

  override fun hashCode(): Int {
    return projectTransformations.id.hashCode() xor nonProjectTransformations.id.hashCode()
  }
}

/**
 * A data structure responsible for replenishing and providing on demand [ModuleClassLoader]s ready to use
 */
class ModuleClassLoaderHatchery(private val capacity: Int = CAPACITY, private val copies: Int = COPIES) {
  // Requests for ModuleClassLoaders type that hatchery does not know how to create
  private val requests = mutableSetOf<Request>()
  // Clutches of different ModuleClassLoader types
  private val storage = LinkedList<Clutch>()

  /**
   * Request a ModuleClassLoader compatible with the input from this hatchery if such exists.
   */
  @Synchronized
  fun requestClassLoader(parent: ClassLoader?, projectTransformations: ClassTransform, nonProjectTransformations: ClassTransform):
    ModuleClassLoader? {
    storage.find { it.isCompatible(parent, projectTransformations, nonProjectTransformations) }?.let { clutch ->
      return clutch.retrieve()
    }
    // If there is no compatible clutch we remember the request and will create one when we have an appropriate donor
    requests.add(Request(parent, projectTransformations, nonProjectTransformations))
    return null
  }

  /**
   * Create a clutch from the [donor] [ModuleClassLoader] if a clutch of this type does not exist and such type was requested. The [donor]
   * should only be used for cloning. Returns true if donor was used for cloning and false otherwise.
   */
  @Synchronized
  fun incubateIfNeeded(donor: ModuleClassLoader, cloner: (ModuleClassLoader) -> ModuleClassLoader?): Boolean {
    if (storage.find { it.isCompatible(
        donor.parent, donor.projectClassesTransform, donor.nonProjectClassesTransform) } != null) {
      return false
    }
    val request = Request(donor.parentAtConstruction, donor.projectClassesTransform, donor.nonProjectClassesTransform)
    if (requests.contains(request)) {
      requests.remove(request)
      if (storage.size == capacity) {
        storage.poll().destroy()
      }
      storage.add(Clutch(cloner, donor, copies))
      return true
    }
    return false
  }

  @Synchronized
  fun getStats(): List<Stats> {
    return storage.map { it.getStats() }
  }

  @Synchronized
  fun destroy() {
    requests.clear()
    storage.forEach { it.destroy() }
    storage.clear()
  }
}

/**
 * Represents the current preloading [progress] (number of classes) out of full [toDo] number.
 */
data class ReadyState(val progress: Int, val toDo: Int)

/**
 * Represents [ReadyState] stats for all eggs in a Clutch identified by [label]
 */
data class Stats(val label: String, val states: List<ReadyState>)