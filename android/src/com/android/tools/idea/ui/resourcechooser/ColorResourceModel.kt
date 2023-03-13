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

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.resources.ResourceItem
import com.android.ide.common.resources.ResourceItemWithVisibility
import com.android.ide.common.resources.ResourceVisitor
import com.android.resources.ResourceType
import com.android.resources.ResourceVisibility
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.res.resolveColor
import com.google.common.annotations.VisibleForTesting
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
  private val resourceMaps = mutableMapOf<String, List<ResourceReference>>()

  private val resourceResolver = configuration.resourceResolver
  private val project = configuration.configModule.project

  init {
    val repoManager = configuration.configModule.resourceRepositoryManager

    val projectResources = ArrayList<ResourceReference>()
    val libraryResources = ArrayList<ResourceReference>()
    repoManager?.appResources?.accept(object : ResourceVisitor {
      override fun visit(resourceItem: ResourceItem): ResourceVisitor.VisitResult {
        resourceItem.referenceToSelf.let {
          when {
            resourceItem.libraryName == null -> projectResources.add(it)
            resourceItem !is ResourceItemWithVisibility || resourceItem.visibility == ResourceVisibility.PUBLIC -> libraryResources.add(it)
            else -> Unit
          }
        }
        return ResourceVisitor.VisitResult.CONTINUE
      }

      override fun shouldVisitResourceType(resourceType: ResourceType) = resourceType == ResourceType.COLOR
    })

    val frameworkResources = ArrayList<ResourceReference>()
    repoManager?.getFrameworkResources(emptySet())
      ?.getPublicResources(ResourceNamespace.ANDROID, ResourceType.COLOR)
      ?.mapTo(frameworkResources) { it.referenceToSelf }

    resourceMaps[Category.PROJECT] = projectResources.apply { sort() }
    resourceMaps[Category.LIBRARY] = libraryResources.apply { sort() }
    resourceMaps[Category.FRAMEWORK] = frameworkResources.apply { sort() }
  }

  fun getResourceReference(@MagicConstant(valuesFromClass = Category::class) category: String, filter: String?) =
    resourceMaps[category]?.let {
      if (filter == null) it
      else it.filter { ref -> ref.resourceUrl.name.contains(filter) }
    } ?: emptyList()

  fun resolveColor(resourceReference: ResourceReference) =
    resourceResolver.resolveColor(resourceResolver.getResolvedResource(resourceReference), project)

  @MagicConstant(valuesFromClass = Category::class)
  fun findResourceCategory(resourceReference: ResourceReference): String? {
    for (valueMap in resourceMaps) {
      if (valueMap.value.contains(resourceReference)) {
        return valueMap.key
      }
    }
    return null
  }
}
