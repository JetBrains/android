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
package com.android.tools.idea.naveditor.actions

import com.android.tools.idea.assistant.OpenAssistSidePanelAction
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.uibuilder.actions.HelpPanelBundle
import com.android.tools.idea.uibuilder.actions.LayoutEditorHelpPanelAssistantBundleCreatorBase
import com.intellij.openapi.actionSystem.AnActionEvent

const val BUNDLE_ID = "LayoutEditor.HelpAssistant.NavEditor"
val navEditorHelpPanelBundle =
  HelpPanelBundle(BUNDLE_ID, "/naveditor_help_assistance_bundle.xml")

class NavEditorHelperAssistanceAction : OpenAssistSidePanelAction() {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = StudioFlags.NELE_NAV_EDITOR_ASSISTANT.get()
  }

  override fun actionPerformed(event: AnActionEvent) {
    openWindow(navEditorHelpPanelBundle.bundleId, event.project!!)
  }
}

class NavEditorPanelAssistantBundleCreator : LayoutEditorHelpPanelAssistantBundleCreatorBase(navEditorHelpPanelBundle)
