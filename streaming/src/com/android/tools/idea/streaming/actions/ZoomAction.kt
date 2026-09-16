/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.streaming.actions

import com.android.tools.idea.streaming.core.ZOOMABLE_KEY
import com.android.tools.idea.streaming.core.ZoomType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware

internal sealed class ZoomAction(val zoomType: ZoomType) : AnAction(), DumbAware {

  override fun update(event: AnActionEvent) {
    val zoomable = event.getData(ZOOMABLE_KEY)
    event.presentation.isEnabled = zoomable?.canZoom(zoomType) ?: false
  }

  override fun actionPerformed(event: AnActionEvent) {
    val zoomable = event.getData(ZOOMABLE_KEY)
    zoomable?.zoom(zoomType)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  class In : ZoomAction(ZoomType.IN)

  class Out : ZoomAction(ZoomType.OUT)

  class Fit : ZoomAction(ZoomType.FIT)

  class Actual : ZoomAction(ZoomType.ACTUAL)
}
