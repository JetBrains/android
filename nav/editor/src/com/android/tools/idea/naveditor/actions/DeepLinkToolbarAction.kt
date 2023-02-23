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

import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.actions.DesignerActions
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.naveditor.analytics.NavUsageTracker
import com.android.tools.idea.naveditor.dialogs.AddDeeplinkDialog
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.google.wireless.android.sdk.stats.NavEditorEvent
import com.google.wireless.android.sdk.stats.NavEditorEvent.NavEditorEventType.*
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.jetbrains.android.dom.AndroidDomElement
import org.jetbrains.android.dom.navigation.DeeplinkElement
import org.jetbrains.android.dom.navigation.NavigationSchema

class DeepLinkToolbarAction private constructor(): AnAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val surface = e.getData(DESIGN_SURFACE) as? NavDesignSurface
    val selection = surface?.selectionModel?.selection

    val enabled = if (selection != null && selection.size == 1) {
      supportsSubtag(selection[0], DeeplinkElement::class.java)
    }
    else {
      false
    }

    e.presentation.isEnabled = enabled
  }

  private fun supportsSubtag(component: NlComponent, subtag: Class<out AndroidDomElement>): Boolean {
    val model = component.model ?: return false
    val schema = NavigationSchema.get(model.module)
    return schema.getDestinationSubtags(component.tagName).containsKey(subtag)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val surface = e.getRequiredData(DESIGN_SURFACE) as NavDesignSurface
    surface.selectionModel.selection.firstOrNull()?.let {
      val dialog = AddDeeplinkDialog(null, it)
      if (dialog.showAndGet()) {
        dialog.save()
        NavUsageTracker.getInstance(surface.model).createEvent(CREATE_DEEP_LINK)
          .withSource(NavEditorEvent.Source.TOOLBAR).log()
      }
    }
  }

  companion object {
    @JvmStatic
    val instance: DeepLinkToolbarAction
      get() = ActionManager.getInstance().getAction(DesignerActions.ACTION_ADD_DEEP_LINK) as DeepLinkToolbarAction
  }
}
