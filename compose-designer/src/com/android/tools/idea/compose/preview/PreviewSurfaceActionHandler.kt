/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.compose.preview

import com.android.tools.idea.common.model.ItemTransferable.DESIGNER_FLAVOR
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.DesignSurfaceActionHandler
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.DataContext
import java.awt.datatransfer.DataFlavor

/** A [DesignSurfaceActionHandler] that disables the copy/paste and delete functionality. */
class PreviewSurfaceActionHandler(surface: DesignSurface<*>) : DesignSurfaceActionHandler(surface) {
  override fun getPasteTarget(): NlComponent? = null
  override fun canHandleChildren(
    component: NlComponent,
    pasted: MutableList<NlComponent>
  ): Boolean = false
  override fun getFlavor(): DataFlavor = DESIGNER_FLAVOR
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
  override fun canDeleteElement(dataContext: DataContext): Boolean = false
  override fun isPasteEnabled(dataContext: DataContext): Boolean = false
  override fun isCopyEnabled(dataContext: DataContext): Boolean = false
  override fun isCopyVisible(dataContext: DataContext): Boolean = false
  override fun isCutVisible(dataContext: DataContext): Boolean = false
  override fun isPastePossible(dataContext: DataContext): Boolean = false
}
