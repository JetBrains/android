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

import com.android.SdkConstants.*
import com.android.ide.common.resources.ResourceResolver
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.naveditor.property.NavDeeplinkProperty
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.intellij.openapi.command.WriteCommandAction
import icons.StudioIcons.NavEditor.Surface.DEEPLINK

class NavDeeplinkInspectorProvider :
  NavListInspectorProvider<NavDeeplinkProperty>(NavDeeplinkProperty::class.java, DEEPLINK, "Add Deep Link") {

  override fun doAddItem(existing: NlComponent?, parents: List<NlComponent>, resourceResolver: ResourceResolver?) {
    assert(parents.size == 1)

    val deeplinkDialog = AddDeeplinkDialog(existing)

    if (deeplinkDialog.showAndGet()) {
      WriteCommandAction.runWriteCommandAction(null, {
        val realComponent = existing ?: run {
          val tag = parents[0].tag.createChildTag(TAG_DEEPLINK, null, null, false)
          parents[0].model.createComponent(tag, parents[0], null)
        }
        realComponent.setAttribute(
            AUTO_URI, ATTR_URI, deeplinkDialog.uri)
        if (deeplinkDialog.autoVerify) {
          realComponent.setAttribute(AUTO_URI, ATTR_AUTO_VERIFY, "true")
        }
      })
    }
  }

  override fun getTitle(components: List<NlComponent>, surface: NavDesignSurface?) = "Deep Links"
}