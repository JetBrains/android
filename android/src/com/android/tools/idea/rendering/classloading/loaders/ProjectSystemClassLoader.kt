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

import com.android.tools.idea.flags.StudioFlags
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.uipreview.ClassModificationTimestamp
import org.jetbrains.android.uipreview.INTERNAL_PACKAGE
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap

private fun String.isSystemPrefix(): Boolean = startsWith("java.") ||
                                               startsWith("javax.") ||
                                               startsWith("kotlin.") ||
                                               startsWith(INTERNAL_PACKAGE) ||
                                               startsWith("sun.")

/**
 * A [DelegatingClassLoader.Loader] that loads the classes from a given IntelliJ [Module].
 * It relies on the given [findClassVirtualFileImpl] to find the [VirtualFile] mapping to a given FQCN.
 */
class ProjectSystemClassLoader(
  jarLoaderCache: Cache<String, EntryCache>,
  private val findClassVirtualFileImpl: (String) -> VirtualFile?
) : DelegatingClassLoader.Loader {

  constructor(findClassVirtualFileImpl: (String) -> VirtualFile?)
    : this(CacheBuilder.newBuilder()
             .softValues()
             .weigher { _: String, value: EntryCache -> value.weight() }
             .maximumWeight(StudioFlags.PROJECT_SYSTEM_CLASS_LOADER_CACHE_LIMIT.get().toLong())
             .build<String, EntryCache>(), findClassVirtualFileImpl)

  @VisibleForTesting val jarManager = JarManager(prefetchAllFiles = true, jarFileCache = jarLoaderCache)

  /**
   * Map that contains the mapping from the class FQCN to the [VirtualFile] that contains the `.class` contents and the
   * [ClassModificationTimestamp] representing the loading timestamp.
   */
  private val virtualFileCache = ConcurrentHashMap<String, Pair<VirtualFile, ClassModificationTimestamp>>()

  /**
   * [Sequence] of all the [VirtualFile]s and their associated [ClassModificationTimestamp] that have been loaded
   * by this [ProjectSystemClassLoader].
   */
  val loadedVirtualFiles: Sequence<Triple<String, VirtualFile, ClassModificationTimestamp>>
    get() = virtualFileCache
      .asSequence()
      .filter { it.value.first.isValid }
      .map {
        Triple(it.key, it.value.first, it.value.second)
      }

  /**
   * Finds the [VirtualFile] for the `.class` associated to the given [fqcn].
   */
  fun findClassVirtualFile(fqcn: String): VirtualFile? {
    // Avoid loading a few well known system prefixes for the project class loader and also classes that have failed before.
    if (fqcn.isSystemPrefix()) {
      return null
    }
    val cachedVirtualFile = virtualFileCache[fqcn]

    if (cachedVirtualFile?.first?.isValid == true) return cachedVirtualFile.first
    val vFile = findClassVirtualFileImpl(fqcn)

    if (vFile != null) {
      virtualFileCache[fqcn] = Pair(vFile, ClassModificationTimestamp.fromVirtualFile(vFile))
    }

    return vFile
  }

  /**
   * Clears all the internal caches. Next `find` call will reload the information directly from the VFS.
   */
  fun invalidateCaches() {
    virtualFileCache.clear()
  }

  /**
   * Reads the contents of this [VirtualFile] via NIO avoiding the VFS cache. This is needed when reading files
   * that are generated outside of Studio, like `.class` files created by the build system so we read the freshest
   * content.
   */
  private fun VirtualFile.readBytesUsingNio(): ByteArray? {
    try {
      return Files.readAllBytes(toNioPath())
    }
    catch (_: UnsupportedOperationException) {
    }
    catch (_: IOException) {
    }

    return null
  }

  /**
   * Reads the contents of this [VirtualFile] using NIO from within a jar. If the [VirtualFile] is not
   * within a jar, this method will return null.
   */
  private fun VirtualFile.readFromJar(): ByteArray? =
    if (url.startsWith("jar:")) {
      // The URL needs to be encoded since it might contain spaces or other characters not valid in the URI.
      jarManager.loadFileFromJar(URI(URLEncoder.encode(url, Charsets.UTF_8)))
    }
    else null

  override fun loadClass(fqcn: String): ByteArray? = try {
    val vFile = findClassVirtualFile(fqcn)
    val contents = vFile?.readBytesUsingNio()
                   ?: vFile?.readFromJar()

    contents
  }
  catch (_: Throwable) {
    null
  }

  /**
   * Injects the given [virtualFile] with the passed [fqcn] so it looks like loaded from the project. Only for testing.
   */
  @TestOnly
  fun injectClassFile(fqcn: String, virtualFile: VirtualFile) {
    virtualFileCache[fqcn] = Pair(virtualFile, ClassModificationTimestamp.fromVirtualFile(virtualFile))
  }
}