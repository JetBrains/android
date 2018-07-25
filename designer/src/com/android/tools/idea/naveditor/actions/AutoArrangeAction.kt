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
package com.android.tools.idea.naveditor.actions

import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.naveditor.scene.layout.SKIP_PERSISTED_LAYOUT
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.command.WriteCommandAction
import icons.StudioIcons

class AutoArrangeAction(private val surface: DesignSurface) : AnAction("Auto Arrange", null, StudioIcons.NavEditor.Toolbar.AUTO_ARRANGE) {

  override fun actionPerformed(e: AnActionEvent?) {
    WriteCommandAction.runWriteCommandAction(surface.project) {
      surface.scene?.root?.children?.map { it.nlComponent }?.forEach { it.putClientProperty(SKIP_PERSISTED_LAYOUT, true) }
      surface.sceneManager?.requestRender()
      surface.scene?.root?.children?.map { it.nlComponent }?.forEach { it.removeClientProperty(SKIP_PERSISTED_LAYOUT) }
    }
  }
}
