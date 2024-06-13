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

import com.android.tools.idea.common.command.NlWriteCommandActionUtil
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.uibuilder.api.ViewEditor
import com.android.tools.idea.uibuilder.api.ViewHandler
import com.android.tools.idea.uibuilder.api.actions.DirectViewAction
import com.android.tools.idea.uibuilder.api.actions.ViewActionMenu
import icons.StudioIcons
import javax.swing.Icon

enum class ScaleType(val icon: Icon?, val displayName: String, val attributeValue: String) {
  CENTER(null, "Center", "center"),
  CENTER_CROP(null, "Center Crop", "centerCrop"),
  CENTER_INSIDE(null, "Center Inside", "centerInside"),
  FIT_CENTER(null, "Fit Center", "fitCenter"),
  FIT_END(null, "Fit End", "fitEnd"),
  FIT_START(null, "Fit Start", "fitStart"),
  FIT_XY(null, "Fit XY", "fitXY"),
  MATRIX(null, "Matrix", "matrix"),
}

class ScaleTypeViewAction(
  private val namespace: String?,
  private val attribute: String,
  private val type: ScaleType,
) : DirectViewAction(type.icon, type.displayName) {
  override fun perform(
    editor: ViewEditor,
    handler: ViewHandler,
    component: NlComponent,
    selectedChildren: MutableList<NlComponent>,
    modifiers: Int,
  ) {
    val attr = component.startAttributeTransaction()
    attr.setAttribute(namespace, attribute, type.attributeValue)
    NlWriteCommandActionUtil.run(component, "Change Scale Type") { attr.commit() }
  }
}

class ScaleTypesViewActionMenu(namespace: String?, attribute: String) :
  ViewActionMenu(
    "",
    StudioIcons.LayoutEditor.Motion.MAX_SCALE,
    ScaleType.values().map { ScaleTypeViewAction(namespace, attribute, it) },
  )
