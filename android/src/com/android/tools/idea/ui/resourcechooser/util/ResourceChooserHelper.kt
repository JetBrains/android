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
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.ui.resourcechooser.ChooseResourceDialog
import com.android.tools.idea.ui.resourcecommon.ResourcePickerDialog
import com.android.tools.idea.ui.resourcemanager.ResourceExplorerDialog
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import org.jetbrains.android.facet.AndroidFacet

/**
 * TODO: Remove unused arguments after removing [StudioFlags.RESOURCE_EXPLORER_PICKER] flag.
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
 * @param file The context file with a Configuration, used for theme attributes, should be present if [tag] or [xmlFile] are present
 * @param xmlFile The context [XmlFile] with a Configuration, used for theme attributes, can use [tag] as a fallback
 * @param tag The context [XmlTag] in a file with a Configuration, used for theme attributes, can use [xmlFile] as a fallback
 */
fun createResourcePickerDialog(dialogTitle: String,
                               currentValue: String?,
                               facet: AndroidFacet,
                               resourceTypes: Set<ResourceType>,
                               defaultResourceType: ResourceType?,
                               showColorStateLists: Boolean,
                               showSampleData: Boolean,
                               file: VirtualFile?,
                               xmlFile: XmlFile?,
                               tag: XmlTag?)
  : ResourcePickerDialog {
  val dialog: ResourcePickerDialog =
    if (StudioFlags.RESOURCE_EXPLORER_PICKER.get())
      ResourceExplorerDialog(facet,
                             currentValue,
                             resourceTypes,
                             defaultResourceType,
                             showSampleData,
                             file)
    else ChooseResourceDialog.builder()
      .setModule(facet.module)
      .setTypes(resourceTypes)
      .setCurrentValue(currentValue)
      .setFile(xmlFile)
      .setTag(tag)
      .setDefaultType(defaultResourceType)
      .setFilterColorStateLists(!showColorStateLists)
      .setShowSampleDataPicker(showSampleData)
      .build()
  dialog.title = dialogTitle
  return dialog
}
