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

import com.android.tools.idea.rendering.StudioModuleRenderContext
import com.android.tools.rendering.classloading.ClassTransform
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.TestOnly
import java.util.LinkedList
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

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
 * Contains all the information that was used to create a [StudioModuleClassLoader].
 */
data class StudioModuleClassLoaderCreationContext(
  val parent: ClassLoader?,
  val moduleRenderContext: StudioModuleRenderContext,
  val classesToPreload: Set<String>,
  val projectTransform: ClassTransform,
  val nonProjectTransformation: ClassTransform,
) {

  /**
   * Creates a new [StudioModuleClassLoader] from this [StudioModuleClassLoaderCreationContext].
   */
  fun createClassLoader(): StudioModuleClassLoader =
    StudioModuleClassLoader(
      parent,
      moduleRenderContext,
      projectTransform,
      nonProjectTransformation,
      StudioModuleClassLoaderManager.createDiagnostics()
    )

  companion object {
    /** Obtains a the [StudioModuleClassLoaderCreationContext] used to create the [classLoader]. */
    fun fromClassLoader(classLoader: StudioModuleClassLoader): StudioModuleClassLoaderCreationContext? =
      classLoader.moduleContext?.let { moduleRenderContext ->
        StudioModuleClassLoaderCreationContext(
          parent = classLoader.parentAtConstruction,
          moduleRenderContext = moduleRenderContext,
          classesToPreload = classLoader.nonProjectLoadedClasses,
          projectTransform = classLoader.projectClassesTransform,
          nonProjectTransformation = classLoader.nonProjectClassesTransform
        )
      }

    @TestOnly
    fun fromClassLoaderOrThrow(classLoader: StudioModuleClassLoader): StudioModuleClassLoaderCreationContext =
      fromClassLoader(classLoader)!!
  }
}

/**
 * A storage of [StudioModuleClassLoader]s of the same type responsible for their preloading and
 * replenishment. [StudioModuleClassLoader]s managed by this storage do not get stale after updating
 * only the user code since module dependencies should change for these [StudioModuleClassLoader]s
 * to become invalid.
 */
private class Clutch(
  private val cloner: (StudioModuleClassLoaderCreationContext) -> StudioModuleClassLoader?,
  private val donor: StudioModuleClassLoaderCreationContext,
  copies: Int = COPIES
) {
  private val eggs = ConcurrentLinkedQueue<StudioPreloader>()
  init {
    repeat(copies) { cloner(donor)?.let { eggs.add(StudioPreloader(it, donor.classesToPreload)) } }
  }

  /**
   * Checks if the clutch maintains the [StudioModuleClassLoader]s of this type.
   */
  fun isCompatible(
    parent: ClassLoader?,
    projectTransformations: ClassTransform,
    nonProjectTransformations: ClassTransform) =
    eggs.peek()?.isForCompatible(parent, projectTransformations, nonProjectTransformations) ?: false

  /**
   * If possible, returns a [StudioModuleClassLoader] from the clutch and transfers full ownership to the caller, otherwise returns null.
   */
  fun retrieve(): StudioModuleClassLoader? {
    return generateSequence { eggs.poll()?.getClassLoader() }
      .firstOrNull {
        if (!it.isUserCodeUpToDate) {
          // This class loader can not be used, it's not up-to-date
          it.dispose()
          false
        } else true
      }
      ?.let { compatibleClassLoader ->
        // Incubate the next one
        cloner(donor)?.let { newClassLoader ->
          eggs.add(StudioPreloader(newClassLoader, donor.classesToPreload))
        }
        compatibleClassLoader
      }
  }

  /**
   * Should be called when the clutch is no longer needed to free all the resources.
   */
  fun destroy() {
    generateSequence { eggs.poll() }.forEach { it.dispose() }
  }

  fun getStats(): Stats {
    return Stats("Clutch ${this.hashCode()}", eggs.map { ReadyState(it.getLoadedCount(), donor.classesToPreload.size) })
  }
}

/**
 * Data representing the identification of the [StudioModuleClassLoader] type.
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
 * A data structure responsible for replenishing and providing on demand [StudioModuleClassLoader]s ready to use
 */
class ModuleClassLoaderHatchery(private val capacity: Int = CAPACITY, private val copies: Int = COPIES, parentDisposable: Disposable) {
  // Requests for ModuleClassLoaders type that hatchery does not know how to create
  private val requests = mutableSetOf<Request>()
  // Clutches of different ModuleClassLoader types
  private val storage = LinkedList<Clutch>()

  // If this class is disposed, it will stop accepting requests
  private val isDisposed = AtomicBoolean(false)

  init {
    Disposer.register(parentDisposable) {
      isDisposed.set(true)
      destroy()
    }
  }

  /**
   * Request a ModuleClassLoader compatible with the input from this hatchery if such exists.
   */
  @Synchronized
  fun requestClassLoader(parent: ClassLoader?, projectTransformations: ClassTransform, nonProjectTransformations: ClassTransform): StudioModuleClassLoader? {
    if (isDisposed.get()) return null

    storage
      .find { it.isCompatible(parent, projectTransformations, nonProjectTransformations) }
      ?.let { clutch ->
        return clutch.retrieve()
      }
    // If there is no compatible clutch we remember the request and will create one when we have an appropriate donor
    requests.add(Request(parent, projectTransformations, nonProjectTransformations))
    return null
  }

  /**
   * Create a clutch from the [donor] [StudioModuleClassLoader] if a clutch of this type does not exist and such type was requested. The [donor]
   * should only be used for cloning. Returns true if donor was used for cloning and false otherwise.
   */
  @Synchronized
  fun incubateIfNeeded(donor: StudioModuleClassLoaderCreationContext, cloner: (StudioModuleClassLoaderCreationContext) -> StudioModuleClassLoader?): Boolean {
    if (isDisposed.get()) return false

    val hasCompatibleDonor = storage.find {
      it.isCompatible(donor.parent, donor.projectTransform, donor.nonProjectTransformation)
    } != null
    if (hasCompatibleDonor) return false
    val request = Request(donor.parent, donor.projectTransform, donor.nonProjectTransformation)
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