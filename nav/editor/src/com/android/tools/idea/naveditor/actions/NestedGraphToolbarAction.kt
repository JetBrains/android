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

import com.android.tools.idea.naveditor.analytics.NavUsageTracker
import com.android.tools.idea.naveditor.model.createNestedGraph
import com.android.tools.idea.naveditor.model.isDestination
import com.android.tools.idea.naveditor.model.moveIntoNestedGraph
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.google.wireless.android.sdk.stats.NavEditorEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import icons.StudioIcons

class NestedGraphToolbarAction(surface: NavDesignSurface) :
  ToolbarAction(surface, "Group into nested graph", StudioIcons.NavEditor.Toolbar.NESTED_GRAPH) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun isEnabled() = surface.selectionModel.selection.any {
    it.isDestination && it != surface.currentNavigation
  }

  override fun actionPerformed(e: AnActionEvent) {
    if (moveIntoNestedGraph(surface) { surface.currentNavigation.createNestedGraph() }) {
      NavUsageTracker.getInstance(surface.model).createEvent(NavEditorEvent.NavEditorEventType.CREATE_NESTED_GRAPH)
        .withSource(NavEditorEvent.Source.TOOLBAR)
        .log()
    }
  }
}