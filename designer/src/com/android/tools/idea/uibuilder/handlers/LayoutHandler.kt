/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers

import com.android.tools.idea.common.api.DragType
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.uibuilder.api.DragHandler
import com.android.tools.idea.uibuilder.api.ViewEditor
import com.android.tools.idea.uibuilder.handlers.frame.FrameDragHandler
import com.android.tools.idea.uibuilder.handlers.frame.FrameLayoutHandler

/**
 * Handler for the <layout> tag
 */
class LayoutHandler : FrameLayoutHandler() {
  override fun createDragHandler(editor: ViewEditor, layout: SceneComponent, components: List<NlComponent>, type: DragType): DragHandler {
    return FrameDragHandler(editor, this, layout, components, type)
  }

  override fun getTitle(tagName: String): String {
    return "<layout>"
  }

  override fun getTitle(component: NlComponent): String {
    return "<layout>"
  }
}
