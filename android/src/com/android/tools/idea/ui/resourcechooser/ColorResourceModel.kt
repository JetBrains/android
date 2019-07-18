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
package com.android.tools.idea.ui.resourcechooser

import com.android.ide.common.rendering.api.ResourceValue
import com.android.ide.common.resources.ResourceItem
import com.android.ide.common.resources.ResourceVisitor
import com.android.resources.ResourceType
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.res.ResourceRepositoryManager
import com.android.tools.idea.res.resolveColor
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableList
import org.intellij.lang.annotations.MagicConstant

/**
 * Provide classified color resources for [ColorResourcePicker].
 */
class ColorResourceModel(configuration: Configuration) {

  @VisibleForTesting
  object Category {
    const val PROJECT = "Project"
    const val LIBRARY = "Library"
    const val FRAMEWORK = "Android Framework"
  }

  val categories = listOf(Category.PROJECT, Category.LIBRARY, Category.FRAMEWORK)
  private val resourceMaps = mutableMapOf<String, List<ResourceValue>>()

  private val resourceResolver = configuration.resourceResolver
  private val project = configuration.module.project

  init {
    val repoManager = ResourceRepositoryManager.getInstance(configuration.module)

    val projectResourceBuilder = ImmutableList.Builder<ResourceValue>()
    val libraryResourceBuilder = ImmutableList.Builder<ResourceValue>()
    repoManager?.appResources?.accept(object : ResourceVisitor {
      override fun visit(resourceItem: ResourceItem): ResourceVisitor.VisitResult {
        resourceItem.resourceValue?.let {
          when {
            it.libraryName == null -> projectResourceBuilder.add(it)
            else -> libraryResourceBuilder.add(it)
          }
        }
        return ResourceVisitor.VisitResult.CONTINUE
      }

      override fun shouldVisitResourceType(resourceType: ResourceType) = resourceType == ResourceType.COLOR
    })

    val frameworkResourceBuilder = ImmutableList.Builder<ResourceValue>()
    repoManager?.getFrameworkResources(emptySet())?.accept(object : ResourceVisitor {
      override fun visit(resourceItem: ResourceItem): ResourceVisitor.VisitResult {
        resourceItem.resourceValue?.let { frameworkResourceBuilder.add(it) }
        return ResourceVisitor.VisitResult.CONTINUE
      }

      override fun shouldVisitResourceType(resourceType: ResourceType) = resourceType == ResourceType.COLOR
    })

    resourceMaps[Category.PROJECT] = projectResourceBuilder.build()
    resourceMaps[Category.LIBRARY] = libraryResourceBuilder.build()
    resourceMaps[Category.FRAMEWORK] = frameworkResourceBuilder.build()
  }

  fun getResourceValues(@MagicConstant(valuesFromClass = Category::class) category: String, filter: String?) =
    resourceMaps[category]?.filter { it.resourceUrl.name.contains(filter ?: "") }?.toList() ?: emptyList()

  fun resolveColor(resourceValue: ResourceValue) = resourceResolver.resolveColor(resourceValue, project)
}
