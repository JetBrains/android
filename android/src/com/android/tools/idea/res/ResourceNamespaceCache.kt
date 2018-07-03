/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.res

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.utils.concurrency.CacheUtils
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.JdkOrderEntry
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.impl.DirectoryIndex
import com.intellij.openapi.vfs.VirtualFile

private val NULL_NAMESPACE = ResourceNamespace.fromPackageName("_null_")

class ResourceNamespaceCache(private val directoryIndex: DirectoryIndex) {

  companion object {
    @JvmStatic fun getInstance(project: Project) = ServiceManager.getService(project, ResourceNamespaceCache::class.java)!!
  }

  private val virtualFileCache: Cache<VirtualFile, ResourceNamespace> = CacheBuilder.newBuilder().weakKeys().weakValues().build()

  /**
   * Determines the [ResourceNamespace] used by a file that doesn't belong to any known android module.
   */
  fun getNamespaceForUnknownResourceFile(file: VirtualFile): ResourceNamespace? {
    val fromCache = CacheUtils.getAndUnwrap(virtualFileCache, file) {
      val orderEntries = directoryIndex.getOrderEntries(directoryIndex.getInfoForFile(file))
      when {
        orderEntries.any { it is JdkOrderEntry } -> ResourceNamespace.ANDROID

        // TODO(namespaces): detect if it's the apk:/// URL
        orderEntries.any { it is LibraryOrderEntry } -> ResourceNamespace.RES_AUTO

        else -> NULL_NAMESPACE
      }
    }

    return if (fromCache === NULL_NAMESPACE) null else fromCache
  }
}
