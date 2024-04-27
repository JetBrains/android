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
package com.android.tools.idea.rendering.classloading.loaders

import com.android.tools.idea.projectsystem.ClassContent
import com.android.tools.rendering.classloading.loaders.CachingClassLoaderLoader
import com.intellij.openapi.module.Module
import com.intellij.util.SofterReference
import org.jetbrains.android.uipreview.INTERNAL_PACKAGE
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.ConcurrentHashMap

private fun String.isSystemPrefix(): Boolean = startsWith("java.") ||
                                               startsWith("javax.") ||
                                               startsWith("kotlin.") ||
                                               startsWith(INTERNAL_PACKAGE) ||
                                               startsWith("sun.")

/**
 * A [CachingClassLoaderLoader] that loads the classes from a given IntelliJ [Module].
 * It relies on the given [findClassContent] to find the [ClassContent] mapping to a given FQCN.
 */
class ProjectSystemClassLoader(
  private val findClassContent: (String) -> ClassContent?
) : CachingClassLoaderLoader {
  /**
   * Map that contains the mapping from the class FQCN to the [ClassContent] that contains the `.class` contents and metadata for the file
   * where the class was loaded from.
   */
  private val classCache = ConcurrentHashMap<String, SofterReference<ClassContent>>()

  @TestOnly
  fun getClassCache() = classCache
    .filter { entry -> entry.value.get() != null }
    .toMap()

  override fun isUpToDate(): Boolean = classCache.values
    .mapNotNull { it.get() }
    .all { it.isUpToDate() }

  /**
   * Finds the [ClassContent] for the `.class` associated to the given [fqcn].
   */
  private fun getClassContentForFqcn(fqcn: String): ClassContent? {
    // Avoid loading a few well known system prefixes for the project class loader and also classes that have failed before.
    if (fqcn.isSystemPrefix()) {
      return null
    }
    val cachedContent = classCache[fqcn]?.get()

    if (cachedContent?.isUpToDate() == true) return cachedContent
    val classContent = findClassContent(fqcn)?.also {
      val newRef = SofterReference(it)
      classCache[fqcn] = newRef
    }

    return classContent
  }

  /**
   * Clears all the internal caches. Next `find` call will reload the information directly from the VFS.
   */
  override fun invalidateCaches() {
    classCache.clear()
  }

  override fun loadClass(fqcn: String): ByteArray? = try {
    getClassContentForFqcn(fqcn)?.content
  }
  catch (_: Throwable) {
    null
  }

  /**
   * Injects the given [classContent] with the passed [fqcn] so it looks like loaded from the project. Only for testing.
   */
  @TestOnly
  fun injectClassFile(fqcn: String, classContent: ClassContent) {
    classCache[fqcn] = SofterReference(classContent)
  }
}