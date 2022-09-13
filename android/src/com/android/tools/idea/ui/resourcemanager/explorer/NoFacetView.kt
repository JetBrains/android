/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.ui.resourcemanager.explorer

import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncReason.Companion.PROJECT_LOADED
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncReason.Companion.PROJECT_MODIFIED
import com.android.tools.idea.projectsystem.getProjectSystem
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.ColorUtil
import com.intellij.util.ui.*
import org.intellij.lang.annotations.Language
import java.awt.Cursor
import javax.swing.JEditorPane
import javax.swing.JPanel
import javax.swing.event.HyperlinkEvent
import javax.swing.text.DefaultCaret


private const val SYNC_LINK = "#sync"

private const val NEW_MODULE_LINK = "#newmodule"

@Language("HTML")
private val NO_FACET_TEXT = """
  |<p>
  |    No Android module has been found.<br/>
  |    <a href="$NEW_MODULE_LINK">Sync project</a>,<br/>
  |    <a href="$SYNC_LINK">Add Android module</a>
  |</p>
  |""".trimMargin()

private const val EMPTY_TEXT_LINE_HEIGHT = 1.2

/**
 * Placeholder view shown when no Android facet has been found on the project.
 */
class NoFacetView(val project: Project)
  : JPanel(VerticalFlowLayout(VerticalFlowLayout.MIDDLE, true, false)) {

  private val androidNewModuleAction = ActionManager.getInstance().getAction("NewModule")

  init {
    add(createInnerText())
  }

  private fun createInnerText(): JEditorPane {
    val linkColor = ColorUtil.toHex(JBUI.CurrentTheme.Link.Foreground.ENABLED)
    return JEditorPane().apply {
      contentType = UIUtil.HTML_MIME
      background = UIUtil.getPanelBackground()
      foreground = NamedColorUtil.getInactiveTextColor()
      editorKit = HTMLEditorKitBuilder().withGapsBetweenParagraphs().build().also {
        it.styleSheet.addRule(" a { color: #$linkColor; } p { line-height: $EMPTY_TEXT_LINE_HEIGHT; }")
      }
      border = JBUI.Borders.empty(32)
      font = StartupUiUtil.getLabelFont()
      cursor = Cursor.getDefaultCursor()
      text = "<html><center>$NO_FACET_TEXT<center></html>"
      isEditable = false
      isFocusable = true
      isOpaque = false
      caret = object : DefaultCaret() {
        override fun isVisible() = false
      }
      addHyperlinkListener { e ->
        if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
          when (e.description) {
            NEW_MODULE_LINK -> syncProject(project)
            SYNC_LINK -> newModule(e, project)
          }
        }
      }
    }
  }

  private fun newModule(e: HyperlinkEvent, project: Project) {
    val anActionEvent = AnActionEvent.createFromInputEvent(e.inputEvent, "", null, SimpleDataContext.getProjectContext(project))
    androidNewModuleAction.update(anActionEvent)
    if (anActionEvent.presentation.isEnabled) {
      androidNewModuleAction.actionPerformed(anActionEvent)
    }
  }

  private fun syncProject(project: Project) {
    val reason = if (project.isInitialized) PROJECT_MODIFIED else PROJECT_LOADED
    val syncManager = project.getProjectSystem().getSyncManager()
    if (!syncManager.isSyncInProgress()) {
      syncManager.syncProject(reason)
    }
  }
}

