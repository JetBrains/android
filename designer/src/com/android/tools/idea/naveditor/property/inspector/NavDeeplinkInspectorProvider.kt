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

import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.naveditor.property.NavDeeplinkProperty
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import icons.StudioIcons.NavEditor.Surface.DEEPLINK
import org.jetbrains.annotations.TestOnly

class NavDeeplinkInspectorProvider(
  @TestOnly private val dialogProvider: (NlComponent?, NlComponent) -> AddDeeplinkDialog =
    { existing: NlComponent?, parent: NlComponent -> AddDeeplinkDialog(existing, parent) }) :
  NavListInspectorProvider<NavDeeplinkProperty>(NavDeeplinkProperty::class.java, DEEPLINK, "Deep Link") {

  override fun doAddItem(existing: NlComponent?, parents: List<NlComponent>, surface: DesignSurface?) {
    assert(parents.size == 1)

    val deeplinkDialog = dialogProvider(existing, parents[0])

    if (deeplinkDialog.showAndGet()) {
      deeplinkDialog.save()
    }
  }

  override fun getTitle(components: List<NlComponent>, surface: NavDesignSurface?) = "Deep Links"
}