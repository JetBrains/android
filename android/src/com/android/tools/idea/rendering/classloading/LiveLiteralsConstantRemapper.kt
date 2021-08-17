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
package com.android.tools.idea.rendering.classloading

import com.android.tools.idea.editors.literals.LiteralUsageReference
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.SimpleModificationTracker
import org.jetbrains.android.uipreview.ModuleProvider
import org.jetbrains.annotations.TestOnly
import java.util.WeakHashMap

/**
 * Interface to be implemented by providers that can remap a constant into a different one.
 * The [remapConstant] method will be called at every use of a certain constant.
 */
interface ConstantRemapper : ModificationTracker {
  /**
   * Adds a new constant to be replaced. Returns true, if the constant value has been updated.
   *
   * @param classLoader [ClassLoader] associated to the class where the constants are being replaced. This allows to have the same class
   * loaded by multiple class loaders and have different constant replacements.
   * @param reference the full path, expressed as an [LiteralUsageReference] to the constant use.
   * @param initialValue the initial value constant.
   * @param newValue the new value to replace the [initialValue] with.
   */
  fun addConstant(classLoader: ClassLoader?, reference: LiteralUsageReference, initialValue: Any, newValue: Any): Boolean

  /**
   * Removes all the existing constants that are remapped.
   */
  fun clearConstants(classLoader: ClassLoader?)

  /**
   * Method called by the transformed class to obtain the new constant value.
   *
   * @param source the class instance that is obtaining the new constant value.
   * @param fileName name of the file containing the constant.
   * @param offset start offset within the file where the constant begins.
   * @param initialValue the initial value of the constant. Used to lookup the new value.
   */
  fun remapConstant(source: Any?, fileName: String, offset: Int, initialValue: Any?): Any?

  /**
   * Returns true if this [ConstantRemapper] has any constants defined.
   */
  fun hasConstants(): Boolean
}

/**
 * [ConstantRemapper] attached to the current [Project].
 */
@Service
class ProjectConstantRemapper : ConstantRemapper {
  private val log = Logger.getInstance(ConstantRemapper::class.java)
  /**
   * Replaced constants indexed by [ClassLoader] and the initial value.
   */
  private val perClassLoaderConstantMap: WeakHashMap<ClassLoader, MutableMap<String, Any>> = WeakHashMap()

  /** Used as a "bloom filter" to decide if we need to instrument a given class/method and for debugging. */
  private val allKeys: WeakHashMap<String, Boolean> = WeakHashMap()

  /** Cache of all the initial values we've seen. This allows avoiding checking the cached if the constant was never there. */
  private val initialValueCache: MutableSet<String> = mutableSetOf()

  /** Modification tracker that is updated everytime a constant is added/removed. */
  private val modificationTracker = SimpleModificationTracker()

  override fun addConstant(classLoader: ClassLoader?, reference: LiteralUsageReference, initialValue: Any, newValue: Any): Boolean {
    val classLoaderMap = perClassLoaderConstantMap.computeIfAbsent(classLoader) { mutableMapOf() }
    val serializedValue = initialValue.toString()
    initialValueCache.add(serializedValue)
    val lookupKey = "${reference.fileName}:${reference.range.startOffset}:$serializedValue"
    if (allKeys.put(lookupKey, true) == null) {
      // This is a new key, update modification count.
      modificationTracker.incModificationCount()
    }
    return classLoaderMap.put(lookupKey, newValue) != newValue
  }

  override fun clearConstants(classLoader: ClassLoader?) {
    perClassLoaderConstantMap[classLoader]?.let {
      if (it.isNotEmpty()) {
        modificationTracker.incModificationCount()
        it.clear()
      }
    }
  }

  @TestOnly
  fun allKeysToText(): String =
    allKeys.keys.joinToString("\n")

  override fun remapConstant(source: Any?, fileName: String, offset: Int, initialValue: Any?): Any? {
    val serializedValue = initialValue?.toString()
    if (serializedValue == null || !initialValueCache.contains(serializedValue)) return initialValue
    // For non static, the instance is passed, get the Class first and then the class loader.
    val classLoader = source?.javaClass?.classLoader
    val classLoaderMap = perClassLoaderConstantMap[classLoader]
                         ?: perClassLoaderConstantMap[null] // fallback to the global constants
                         ?: return initialValue

    // Construct the lookupKey to find the constant in the constant map.
    // For lambdas, we ignore the invoke() method name in Kotlin.
    val lookupKey = "$fileName:$offset:$serializedValue"
    log.debug { "Constant lookup $lookupKey (present=${classLoaderMap.containsKey(lookupKey)})" }
    return classLoaderMap.getOrDefault(lookupKey, initialValue)
  }

  override fun hasConstants(): Boolean = perClassLoaderConstantMap.values.any { it.isNotEmpty() }

  override fun getModificationCount(): Long = modificationTracker.modificationCount

  companion object {
    @JvmStatic
    fun getInstance(project: Project): ConstantRemapper = project.getService(ConstantRemapper::class.java)
  }
}

/**
 * Constant remapper used by the Compose preview..
 */
object ComposePreviewConstantRemapper {
  /**
   * Method used by the transformed classes to retrieve the value of a constant. This method
   * does not have direct uses in Studio but it will be used by the modified classes.
   */
  @JvmStatic
  fun remapAny(source: Any?, fileName: String, offset: Int, initialValue: Any?): Any? {
    if (source == null) return initialValue
    val project = (source::class.java.classLoader as? ModuleProvider)?.module?.project
                  ?: (source as? ModuleProvider)?.module?.project // For testing, we might pass the ModuleProvider direclty.
                  ?: return initialValue
    return ProjectConstantRemapper.getInstance(project).remapConstant(source, fileName, offset, initialValue)
  }
}