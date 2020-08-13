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
package com.android.tools.idea.compose.preview

import com.android.annotations.concurrency.Slow
import com.android.tools.idea.compose.preview.PreviewGroup.Companion.ALL_PREVIEW_GROUP
import com.android.tools.idea.compose.preview.util.PreviewElement
import com.android.tools.idea.compose.preview.util.PreviewElementInstance
import com.android.tools.idea.compose.preview.util.PreviewElementTemplateInstanceProvider
import com.intellij.openapi.diagnostic.Logger
import kotlin.properties.Delegates

class PreviewFilters(previewProvider: PreviewElementProvider) : PreviewElementProvider {
  private val LOG = Logger.getInstance(PreviewFilters::class.java)

  /**
   * Filter to be applied for the group filtering. This allows multiple [PreviewElement]s belonging to the same group
   */
  private val groupNameFilteredProvider = GroupNameFilteredPreviewProvider(previewProvider)

  /**
   * [PreviewElementProvider] that instantiates the templates and creates specific instances.
   */
  private val instantiatedElementProvider = PreviewElementTemplateInstanceProvider(groupNameFilteredProvider)

  /**
   * Filter to be applied for the preview to display a single [PreviewElement]. Used in interactive mode to focus on a
   * single element.
   */
  private val singleElementFilteredProvider = SinglePreviewElementInstanceFilteredPreviewProvider(instantiatedElementProvider)

  /**
   * Name of the group to filter elements.
   */
  var groupNameFilter: PreviewGroup by Delegates.observable(ALL_PREVIEW_GROUP) { _, oldValue, newValue ->
    if (oldValue != newValue) {
      LOG.debug("New group preview element selection: $newValue")
      this.groupNameFilteredProvider.groupName = newValue.name
    }
  }

  /**
   * [Set] of all the available [PreviewGroup]s in this preview.
   */
  @get:Slow
  val allAvailableGroups: Set<PreviewGroup>
    get() = groupNameFilteredProvider.allAvailableGroups.map {
      PreviewGroup.namedGroup(it)
    }.toSet()

  /**
   * [PreviewElementInstance] to select or null if no instance should be selected. If the instance does not exist all instances are
   * returned.
   */
  var instanceFilter: PreviewElementInstance?
    set(value) {
      singleElementFilteredProvider.instance = value
    }
    get() = singleElementFilteredProvider.instance

  /**
   * Clears the instance id filter.
   */
  fun clearInstanceIdFilter() {
    singleElementFilteredProvider.instance = null
  }

  @get:Slow
  override val previewElements: Sequence<PreviewElementInstance>
    get() = singleElementFilteredProvider.previewElements.filterIsInstance<PreviewElementInstance>()

}