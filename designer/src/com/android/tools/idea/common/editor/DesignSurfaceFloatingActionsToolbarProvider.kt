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
package com.android.tools.idea.common.editor

import com.android.annotations.concurrency.UiThread
import com.android.tools.editor.EditorActionsFloatingToolbarProvider
import com.android.tools.editor.EditorActionsToolbarActionGroups
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.DesignSurfaceListener
import com.android.tools.idea.uibuilder.editor.BasicDesignSurfaceActionGroups
import com.android.tools.idea.uibuilder.editor.EditableDesignSurfaceActionGroups
import com.intellij.openapi.Disposable
import javax.swing.JComponent

/** Creates the floating actions toolbar used on the [DesignSurface] */
class DesignSurfaceFloatingActionsToolbarProvider(
  private val designSurface: DesignSurface<*>,
  component: JComponent,
  parentDisposable: Disposable
) : EditorActionsFloatingToolbarProvider(component, parentDisposable, "Surface"), DesignSurfaceListener {


  init {
    designSurface.addListener(this)
    designSurface.addPanZoomListener(this)
    updateToolbar()
  }

  override fun dispose() {
    super.dispose()
    designSurface.removeListener(this)
    designSurface.removePanZoomListener(this)
  }

  @UiThread
  override fun modelChanged(surface: DesignSurface<*>, model: NlModel?) {
    updateToolbar()
  }

  override fun getActionGroups(): EditorActionsToolbarActionGroups {
    return if (designSurface.isEditable) {
      // Only editable file types support panning.
      EditableDesignSurfaceActionGroups()
    }
    else {
      BasicDesignSurfaceActionGroups()
    }
  }
}
