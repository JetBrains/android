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

import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.uipreview.ClassModificationTimestamp
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.ConcurrentHashMap

/**
 * A [DelegatingClassLoader.Loader] that loads the classes from a given IntelliJ [Module].
 * It relies in the given [findClassVirtualFileImpl] to find the [VirtualFile] mapping to a given FQCN.
 */
class ProjectSystemClassLoader(private val findClassVirtualFileImpl: (String) -> VirtualFile?) : DelegatingClassLoader.Loader {
  private val virtualFileCache = ConcurrentHashMap<String, VirtualFile>()
  private val virtualFileModificationCache = ConcurrentHashMap<String, ClassModificationTimestamp>()

  /**
   * [Sequence] of all the [VirtualFile]s and their associated [ClassModificationTimestamp] that have been loaded
   * by this [ProjectSystemClassLoader].
   */
  val loadedVirtualFiles: Sequence<Triple<String, VirtualFile, ClassModificationTimestamp>>
    get() = virtualFileCache
      .asSequence()
      .filter { it.value.isValid }
      .mapNotNull {
        val modificationTimestamp = findClassVirtualFileModificationTimeStamp(it.key) ?: return@mapNotNull null
        Triple(it.key, it.value, modificationTimestamp)
      }

  /**
   * Finds the [VirtualFile] for the `.class` associated to the given [fqcn].
   */
  fun findClassVirtualFile(fqcn: String): VirtualFile? =
    virtualFileCache.compute(fqcn) { _, value ->
      if (value?.isValid == true)
        value
      else
        findClassVirtualFileImpl(fqcn)

    }

  /**
   * Finds the [ClassModificationTimestamp] for the `.class` associated to the given [fqcn].
   */
  private fun findClassVirtualFileModificationTimeStamp(fqcn: String): ClassModificationTimestamp? =
    virtualFileModificationCache.compute(fqcn) { key, value ->
      value ?: ClassModificationTimestamp.fromVirtualFile(findClassVirtualFile(key) ?: return@compute null)
    }

  /**
   * Clears all the internal caches. Next `find` call will reload the information directly from the VFS.
   */
  fun invalidateCaches() {
    virtualFileCache.clear()
    virtualFileModificationCache.clear()
  }

  override fun loadClass(fqcn: String): ByteArray? = findClassVirtualFile(fqcn)?.contentsToByteArray()

  @TestOnly
  fun injectClassFile(fqcn: String, virtualFile: VirtualFile) {
    virtualFileCache[fqcn] = virtualFile
  }
}