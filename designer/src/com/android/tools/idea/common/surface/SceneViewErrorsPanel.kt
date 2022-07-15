/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.common.surface

import com.android.tools.adtui.TabularLayout
import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.idea.common.actions.IssueNotificationAction
import com.android.tools.idea.editors.shortcuts.asString
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.Gray
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import javax.swing.JPanel
import javax.swing.border.LineBorder
import javax.swing.event.HyperlinkEvent

/**
 * [JPanel] to be displayed when a given [SceneView] has render errors.
 *
 * The panel uses a [TabularLayout] with the following [TabularLayout.Constraint]s:
 *        ______________________________
 *  10px |                             |
 *       |_____________________________|
 *       |     |                 |     |
 *       |     |                 |     |
 *       |10 px|  panelContent   |10 px|
 *       |     |                 |     |
 *       |     |                 |     |
 *        ______________________________
 *  10px |                             |
 *       |_____________________________|
 */
class SceneViewErrorsPanel(private val isPanelVisible: () -> Boolean = { true }) : JPanel(TabularLayout("10px,*,10px", "10px,*,10px")) {

  private val size = JBUI.size(200)

  private val shortcutText =
    if (ApplicationManager.getApplication().isUnitTestMode) " (I)" else IssueNotificationAction.getInstance().shortcutSet.asString()

  private val fakeActionEvent by lazy {
    AnActionEvent(null, DataManager.getInstance().getDataContext(this), "", Presentation(), ActionManager.getInstance(), 0)
  }

  init {
    // panelContent also uses a TabularLayout with the following constraints:
    //            Fit |  use all available space
    //           ______________________________________
    //          |      |                              |
    //          |      |                              | use all remaining space
    //          |      |                              |
    //          | Icon |   "There are issues" label   | Fit
    //          |      |                              | 5px
    //          |      |   "Open issues panel" link   | Fit
    //          |      |                              |
    //          |      |                              | use all remaining space
    //          |_____ |______________________________|
    val panelContent = JPanel(TabularLayout("Fit,*", "*,Fit,5px,Fit,*"))

    val errorIconPanel = JPanel(TabularLayout("Fit", "Fit,*"))
    errorIconPanel.add(JBLabel(StudioIcons.Common.WARNING).apply { border = JBUI.Borders.empty(2, 5) }, TabularLayout.Constraint(0, 0))
    panelContent.add(errorIconPanel, TabularLayout.Constraint(1, 0))

    val label = JBLabel("<html>Some issues were found when trying to render this preview.</html>").apply { foreground = Gray._119 }
    panelContent.add(label, TabularLayout.Constraint(1, 1))

    val linkLabel = HyperlinkLabel().apply {
      setHtmlText("<html><a href=\"#\">Open Issues Panel${shortcutText}</a></href>")
      addHyperlinkListener {
        if (it.eventType == HyperlinkEvent.EventType.ACTIVATED) {
          // TODO(b/203426144): open the shared problems panel instead of the issues panel.
          IssueNotificationAction.getInstance().setSelected(fakeActionEvent, true)
        }
      }
    }
    panelContent.add(linkLabel, TabularLayout.Constraint(3, 1))
    add(panelContent, TabularLayout.Constraint(1, 1))

    border = LineBorder(AdtUiUtils.DEFAULT_BORDER_COLOR, 1)
  }

  override fun getPreferredSize() = size

  override fun getMinimumSize() = size

  override fun isVisible() = isPanelVisible()
}