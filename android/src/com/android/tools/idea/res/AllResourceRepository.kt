/*
 * Copyright (C) 2019 The Android Open Source Project
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
import com.android.ide.common.resources.ResourceItem
import com.android.ide.common.resources.ResourceRepository
import com.android.ide.common.resources.ResourceVisitor
import com.android.ide.common.resources.SingleNamespaceResourceRepository
import com.android.resources.ResourceType
import com.google.common.collect.ListMultimap
import java.util.function.Predicate

/**
 * Repository that returns results from both the app and framework.
 */
class AllResourceRepository(
  private val appResourceRepository: ResourceRepository,
  private val frameworkResourceRepository: ResourceRepository
) : ResourceRepository {
  override fun getPublicResources(namespace: ResourceNamespace, type: ResourceType): Collection<ResourceItem> {
    return pickRepository(namespace).getPublicResources(namespace, type)
  }

  override fun hasResources(namespace: ResourceNamespace, resourceType: ResourceType): Boolean {
    return pickRepository(namespace).hasResources(namespace, resourceType)
  }

  override fun getResourceTypes(namespace: ResourceNamespace): Set<ResourceType> {
    return pickRepository(namespace).getResourceTypes(namespace)
  }

  override fun getLeafResourceRepositories(): Collection<SingleNamespaceResourceRepository> {
    return appResourceRepository.leafResourceRepositories + frameworkResourceRepository.leafResourceRepositories
  }

  override fun getResources(namespace: ResourceNamespace, resourceType: ResourceType, resourceName: String): List<ResourceItem> {
    return pickRepository(namespace).getResources(namespace, resourceType, resourceName)
  }

  override fun getResources(namespace: ResourceNamespace,
                            resourceType: ResourceType,
                            filter: Predicate<ResourceItem>): List<ResourceItem> {
    return pickRepository(namespace).getResources(namespace, resourceType, filter)
  }

  override fun getResources(namespace: ResourceNamespace, resourceType: ResourceType): ListMultimap<String, ResourceItem> {
    return pickRepository(namespace).getResources(namespace, resourceType)
  }

  override fun accept(visitor: ResourceVisitor): ResourceVisitor.VisitResult {
    return if (appResourceRepository.accept(visitor) == ResourceVisitor.VisitResult.CONTINUE) {
      frameworkResourceRepository.accept(visitor)
    }
    else {
      ResourceVisitor.VisitResult.ABORT
    }
  }

  override fun hasResources(namespace: ResourceNamespace, resourceType: ResourceType, resourceName: String): Boolean {
    return pickRepository(namespace).hasResources(namespace, resourceType, resourceName)
  }

  override fun getNamespaces(): Set<ResourceNamespace> = appResourceRepository.namespaces + frameworkResourceRepository.namespaces

  private fun pickRepository(namespace: ResourceNamespace): ResourceRepository {
    return if (namespace == ResourceNamespace.ANDROID) frameworkResourceRepository else appResourceRepository
  }
}
