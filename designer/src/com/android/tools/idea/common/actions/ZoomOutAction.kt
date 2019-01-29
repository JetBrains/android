/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.common.actions

import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.ZoomType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomShortcutSet
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

class ZoomOutAction(surface: DesignSurface) : SetZoomAction(surface, ZoomType.OUT) {
  // TODO: register shortcuts
  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabled = mySurface.canZoomOut()
  }
}
