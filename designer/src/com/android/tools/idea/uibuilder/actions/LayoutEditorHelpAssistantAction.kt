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
package com.android.tools.idea.uibuilder.actions

import com.android.tools.idea.assistant.AssistantBundleCreator
import com.android.tools.idea.assistant.OpenAssistSidePanelAction
import com.android.tools.idea.assistant.datamodel.TutorialBundleData
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import java.net.URL

val BUNDLE_ID = "LayoutEditor.HelpAssistant"

class LayoutEditorHelpAssistantAction : OpenAssistSidePanelAction() {

  override fun update(e: AnActionEvent) {
    e.presentation.isVisible = StudioFlags.NELE_LAYOUT_EDITOR_ASSISTANT_ENABLED.get()
  }

  override fun actionPerformed(event: AnActionEvent) {
    openWindow(BUNDLE_ID, event.project!!)

    // TODO: Tracker - Followup - How do I add an EventKind?
    // How do we usually track these in Design Surface?
  }
}

private class LayoutEditorHelpPanelAssistantBundleCreator : AssistantBundleCreator {

  private val TUTORIAL_CONFIG_FILENAME = "/help_assistant_bundle.xml"

  override fun getBundleId(): String {
    return BUNDLE_ID
  }

  override fun getBundle(project: Project): TutorialBundleData? {
    return null
  }

  override fun getConfig(): URL? {
    return javaClass.getResource(TUTORIAL_CONFIG_FILENAME)
  }
}