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
import com.android.ide.common.resources.AbstractResourceRepository
import com.android.ide.common.resources.ResourceItem
import com.android.ide.common.resources.ResourceItemWithVisibility
import com.android.ide.common.resources.ResourceVisitor
import com.android.ide.common.resources.SingleNamespaceResourceRepository
import com.android.resources.ResourceType
import com.android.resources.ResourceVisibility
import com.android.resources.aar.FrameworkResourceRepository
import com.android.tools.res.PerConfigResourceMap.ResourceItemComparator
import com.google.common.collect.ImmutableListMultimap
import com.google.common.collect.ListMultimap
import java.util.EnumMap

/**
 * This repository contains all the base framework resources plus the resources coming from overlays.
 * It ensures that resources from overlays are prioritised over the ones from the base framework.
 * The overlays are applied with decreasing order of priority.
 */
class FrameworkWithOverlaysResourceRepository(base: FrameworkResourceRepository, overlays: List<FrameworkResourceRepository>) :
  AbstractResourceRepository(), SingleNamespaceResourceRepository {
  private val resources: MutableMap<ResourceType, ListMultimap<String, ResourceItem>> = EnumMap(ResourceType::class.java)
  private val publicResources: MutableMap<ResourceType, Set<ResourceItem>> = EnumMap(ResourceType::class.java)

  init {
    loadResources(base, overlays)
    populatePublicResourcesMap()
  }

  private fun loadResources(base: FrameworkResourceRepository, overlays: List<FrameworkResourceRepository>) {
    val allRepos = overlays.toMutableList()
    allRepos.add(base)
    val comparator = ResourceItemComparator(allRepos as Collection<SingleNamespaceResourceRepository>)
    for (type in ResourceType.entries) {
      val map = PerConfigResourceMap(comparator)
      allRepos.forEach {
        val resources = it.getResources(ResourceNamespace.ANDROID, type)
        if (!resources.isEmpty) {
          map.putAll(resources)
        }
      }
      resources[type] = ImmutableListMultimap.copyOf(map)
    }
  }

  override fun getResourcesInternal(
    namespace: ResourceNamespace, resourceType: ResourceType
  ): ListMultimap<String, ResourceItem> {
    if (namespace != ResourceNamespace.ANDROID) {
      return ImmutableListMultimap.of()
    }
    return resources.getOrDefault(resourceType, ImmutableListMultimap.of())
  }

  private fun populatePublicResourcesMap() {
    resources.forEach { (type, items) ->
      val visibleItems = items.values().filterIsInstance<ResourceItemWithVisibility>().filter { it.visibility == ResourceVisibility.PUBLIC }.toSet()
      publicResources[type] = visibleItems
    }
  }

  override fun accept(visitor: ResourceVisitor): ResourceVisitor.VisitResult {
    if (visitor.shouldVisitNamespace(ResourceNamespace.ANDROID)
        && acceptByResources(resources, visitor) == ResourceVisitor.VisitResult.ABORT) {
      return ResourceVisitor.VisitResult.ABORT
    }
    return ResourceVisitor.VisitResult.CONTINUE
  }

  override fun getResources(namespace: ResourceNamespace, resourceType: ResourceType, resourceName: String): List<ResourceItem> {
    val map = getResourcesInternal(namespace, resourceType)
    return map[resourceName]
  }

  override fun getResources(namespace: ResourceNamespace, resourceType: ResourceType): ListMultimap<String, ResourceItem> {
    return getResourcesInternal(namespace, resourceType)
  }

  override fun getPublicResources(namespace: ResourceNamespace, type: ResourceType): Collection<ResourceItem> {
    if (namespace != ResourceNamespace.ANDROID) {
      return emptySet()
    }
    return publicResources[type] ?: emptySet()
  }

  override fun getNamespace(): ResourceNamespace = ResourceNamespace.ANDROID

  override fun getPackageName() = ResourceNamespace.ANDROID.packageName
}