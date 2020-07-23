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
package com.android.tools.idea.ui.resourcechooser.util

import com.android.resources.ResourceType
import com.android.tools.idea.ui.resourcemanager.ResourcePickerDialog
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.facet.AndroidFacet

/**
 * Returns a [ResourcePickerDialog], may list sample data, project, library, android and theme attributes resources for the given
 * [resourceTypes].
 *
 * Selecting a resource in the ResourcePicker will populate [ResourcePickerDialog.resourceName] in the following format:
 * '@string/my_string' or '?attr/my_attribute' for theme attributes, the resource name will include the appropriate namespace.
 *
 * @param dialogTitle For the DialogWrapper
 * @param currentValue The current/initial resource reference value E.g: '@string/my_string'
 * @param facet The current [AndroidFacet]
 * @param resourceTypes Supported or expected [ResourceType]s in the ResourcePicker
 * @param defaultResourceType Preferred [ResourceType] when there are multiple [ResourceType]s supported
 * @param showColorStateLists If true, include state lists in Color resources
 * @param showSampleData If true, include SampleData
 * @param file The context file with a Configuration, used for theme attributes
 */
fun createResourcePickerDialog(
  @NlsContexts.DialogTitle dialogTitle: String,
  currentValue: String?,
  facet: AndroidFacet,
  resourceTypes: Set<ResourceType>,
  defaultResourceType: ResourceType?,
  showColorStateLists: Boolean,
  showSampleData: Boolean,
  file: VirtualFile?
)
  : ResourcePickerDialog {
  // TODO: Implement showColorStateLists
  return  ResourcePickerDialog(facet, currentValue, resourceTypes, defaultResourceType, showSampleData, file).apply { title = dialogTitle }
}
