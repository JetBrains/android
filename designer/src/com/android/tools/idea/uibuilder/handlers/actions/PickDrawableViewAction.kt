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
package com.android.tools.idea.uibuilder.handlers.actions

import com.android.SdkConstants
import com.android.resources.ResourceType
import com.android.tools.idea.common.command.NlWriteCommandActionUtil
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.ui.resourcechooser.util.createResourcePickerDialog
import com.android.tools.idea.uibuilder.api.ViewEditor
import com.android.tools.idea.uibuilder.api.ViewHandler
import com.android.tools.idea.uibuilder.api.actions.DirectViewAction
import icons.StudioIcons
import java.util.EnumSet

/**
 * Action for picking drawable resource. The reference of resource will set to
 * [namespace]:[attribute] attribute after action performed.
 */
class PickDrawableViewAction(private val namespace: String?, private val attribute: String) :
  DirectViewAction(StudioIcons.LayoutEditor.Extras.PIPETTE, "Drawable") {

  override fun perform(
    editor: ViewEditor,
    handler: ViewHandler,
    component: NlComponent,
    selectedChildren: MutableList<NlComponent>,
    modifiers: Int,
  ) {
    val tag = component.tag ?: return
    val types = EnumSet.of(ResourceType.DRAWABLE, ResourceType.MIPMAP)

    val dialog =
      createResourcePickerDialog(
        dialogTitle = "Choose an Image",
        currentValue = null,
        facet = component.model.facet,
        resourceTypes = types,
        defaultResourceType = null,
        showColorStateLists = true,
        showSampleData = false,
        showThemeAttributes = true,
        file = tag.containingFile.virtualFile,
      )

    if (dialog.showAndGet()) {
      if (dialog.resourceName != null) {
        val attr = component.startAttributeTransaction()
        attr.setAttribute(namespace, attribute, dialog.resourceName)
        // Remove sample data attribute.
        attr.removeAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_SRC_COMPAT)
        NlWriteCommandActionUtil.run(component, "Update Image") { attr.commit() }
      }
    }
  }
}
