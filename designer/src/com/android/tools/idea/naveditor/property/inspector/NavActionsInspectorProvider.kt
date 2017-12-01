/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.naveditor.property.inspector

import com.android.SdkConstants
import com.android.ide.common.resources.ResourceResolver
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.naveditor.property.NavActionsProperty
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.intellij.openapi.command.WriteCommandAction
import icons.StudioIcons
import org.jetbrains.android.dom.navigation.NavigationSchema

class NavActionsInspectorProvider : NavListInspectorProvider<NavActionsProperty>(NavActionsProperty::class.java,
    StudioIcons.NavEditor.Properties.ACTION) {

  override fun addItem(existing: NlComponent?, parents: List<NlComponent>, resourceResolver: ResourceResolver?) {
    assert(parents.size == 1)

    val addActionDialog = AddActionDialog(existing, parents[0], resourceResolver)

    if (addActionDialog.showAndGet()) {
      WriteCommandAction.runWriteCommandAction(null, {
        val realComponent = existing ?: run {
          val source = addActionDialog.source
          val tag = source.tag.createChildTag(NavigationSchema.TAG_ACTION, null, null, false)
          source.model.createComponent(tag, source, null)
        }
        realComponent.ensureId()
        realComponent.setAttribute(
            SdkConstants.AUTO_URI, NavigationSchema.ATTR_DESTINATION, SdkConstants.ID_PREFIX + addActionDialog.destination.id!!)
        addActionDialog.popTo?.let { realComponent.setAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_POP_UP_TO, it) }
            ?: realComponent.removeAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_POP_UP_TO)
        if (addActionDialog.isInclusive) {
          realComponent.setAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_POP_UP_TO_INCLUSIVE, "true")
        }
        if (addActionDialog.isSingleTop) {
          realComponent.setAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_SINGLE_TOP, "true")
        }
        if (addActionDialog.isDocument) {
          realComponent.setAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_DOCUMENT, "true")
        }
        if (addActionDialog.isClearTask) {
          realComponent.setAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_CLEAR_TASK, "true")
        }
      })
    }
  }

  override fun getTitle(components: List<NlComponent>, surface: NavDesignSurface?) =
    if (components.size == 1 && components[0] == surface?.currentNavigation) { "Global Actions" } else { "Actions" }
}
