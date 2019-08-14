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
import com.intellij.psi.xml.XmlTag
import org.jetbrains.android.facet.AndroidFacet

/**
 * TODO: Remove unused arguments after removing [StudioFlags.RESOURCE_EXPLORER_PICKER] flag.
 */
fun createResourcePickerDialog(dialogTitle: String,
                               currentValue: String?,
                               facet: AndroidFacet,
                               resourceTypes: Set<ResourceType>,
                               defaultResourceType: ResourceType?,
                               showColorStateLists: Boolean,
                               showSampleData: Boolean,
                               file: VirtualFile?,
                               tag: XmlTag?)
  : ResourcePickerDialog {
  val dialog: ResourcePickerDialog =
    if (StudioFlags.RESOURCE_EXPLORER_PICKER.get())
      ResourceExplorerDialog(facet,
                             currentValue,
                             resourceTypes,
                             showSampleData,
                             file)
    else ChooseResourceDialog.builder()
      .setModule(facet.module)
      .setTypes(resourceTypes)
      .setCurrentValue(currentValue)
      .setTag(tag)
      .setDefaultType(defaultResourceType)
      .setFilterColorStateLists(!showColorStateLists)
      .setShowSampleDataPicker(showSampleData)
      .build()
  dialog.title = dialogTitle
  return dialog
}
