/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.res

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.ResourceItem
import com.android.ide.common.resources.ResourceVisitor
import com.android.ide.common.util.PathString
import com.android.resources.ResourceType
import com.android.resources.base.BasicResourceItem
import com.android.resources.base.LoadableResourceRepository
import com.android.resources.base.RepositoryLoader
import com.google.common.collect.LinkedListMultimap
import com.google.common.collect.ListMultimap
import java.io.File
import java.nio.file.Path
import java.util.EnumMap

/**
 * [LocalResourceRepository] based on a single [resFolder] [File] that contains resources structured as res folder in Android project. This
 * resource repository does not track changes in the resource files in the [resFolder], they are loaded once at the time of creation.
 */
class FolderResourceRepository(private val resFolder: File) : LocalResourceRepository<File>(""), LoadableResourceRepository {
  private val resourcePathPrefix = RepositoryLoader.portableFileName(resFolder.path) + '/';
  private val resourcePathBase = PathString(resourcePathPrefix)
  private val resourceTable: MutableMap<ResourceType, ListMultimap<String, ResourceItem>> = EnumMap(ResourceType::class.java)
  init {
    val loader = object : RepositoryLoader<FolderResourceRepository>(resFolder.toPath(), null, this.namespace) {
      override fun addResourceItem(item: BasicResourceItem, repository: FolderResourceRepository) {
        resourceTable.computeIfAbsent(item.resourceType) { LinkedListMultimap.create() }.put(item.name, item)
      }
    }
    loader.loadRepositoryContents(this)
  }
  override fun accept(visitor: ResourceVisitor): ResourceVisitor.VisitResult {
    if (visitor.shouldVisitNamespace(namespace)) {
      return acceptByResources(resourceTable, visitor)
    }
    return ResourceVisitor.VisitResult.CONTINUE
  }

  override fun getNamespace(): ResourceNamespace = ResourceNamespace.RES_AUTO

  override fun getPackageName(): String? = namespace.packageName

  override fun getLibraryName(): String? = null
  override fun getOrigin(): Path = resFolder.toPath()

  override fun getResourceUrl(relativeResourcePath: String): String = resourcePathPrefix + relativeResourcePath

  override fun getSourceFile(relativeResourcePath: String, forFileResource: Boolean): PathString =
    resourcePathBase.resolve(relativeResourcePath)

  override fun containsUserDefinedResources(): Boolean = true

  override fun getMap(namespace: ResourceNamespace, resourceType: ResourceType): ListMultimap<String, ResourceItem>? {
    return if (namespace == this.namespace) resourceTable[resourceType] else null
  }

  override fun computeResourceDirs(): MutableSet<File> {
    return mutableSetOf(resFolder)
  }
}