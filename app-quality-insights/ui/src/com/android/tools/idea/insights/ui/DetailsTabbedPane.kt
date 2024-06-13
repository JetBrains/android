/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.insights.ui

import com.android.tools.adtui.common.primaryContentBackground
import com.android.tools.adtui.util.ActionToolbarUtil
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actions.AbstractToggleUseSoftWrapsAction
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.softwrap.SoftWrapAppliancePlaces
import com.intellij.ui.border.CustomLineBorder
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.util.preferredWidth
import java.awt.Component
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSeparator

/** Defines the name of a tab and the component to show for that tab. */
data class TabbedPaneDefinition(val name: String, val component: JComponent)

/**
 * A tabbed pane component for AQI's details panel with a floating soft wrap action in the top right
 * corner.
 *
 * It takes a list of [TabbedPaneDefinition] and creates tabs based on them. It requires a
 * [StackTraceConsole] to connect with the soft wrap action.
 */
class DetailsTabbedPane(
  name: String,
  definitions: List<TabbedPaneDefinition>,
  stackTraceConsole: StackTraceConsole,
) {
  val component: JComponent

  init {
    val tabbedPane =
      JBTabbedPane().apply {
        background = primaryContentBackground
        isOpaque = false
      }
    component =
      transparentPanel(GridBagLayout()).apply {
        add(
          createToolbar(stackTraceConsole.consoleView.editor, this, name),
          GridBagConstraints().apply {
            gridx = 1
            gridy = 0
            anchor = GridBagConstraints.FIRST_LINE_END
            weightx = 0.1
          },
        )
        add(
          tabbedPane,
          GridBagConstraints().apply {
            gridx = 0
            gridy = 0
            gridwidth = 2
            fill = GridBagConstraints.BOTH
            anchor = GridBagConstraints.FIRST_LINE_START
            weightx = 0.9
            weighty = 1.0
          },
        )
      }
    tabbedPane.tabComponentInsets = null
    definitions.forEachIndexed { index, definition ->
      tabbedPane.insertTab(
        definition.name,
        null,
        createPanelWithBlankSpaceAtTop(definition.component),
        null,
        index,
      )
    }

    // On fold/un-fold of code blocks, resize tab panel accordingly.
    (stackTraceConsole.consoleView.editor as EditorImpl)
      .contentComponent
      .addComponentListener(
        object : ComponentAdapter() {
          override fun componentResized(e: ComponentEvent) {
            tabbedPane.preferredSize =
              calculatePreferredTabbedPaneSize(tabbedPane, tabbedPane.selectedComponent)
            tabbedPane.revalidate()
          }
        }
      )

    if (definitions.size > 1) {
      // Tabbed panels normally prefer the size of the largest tab.
      // Here we set the size according to the currently selected tab.
      tabbedPane.addChangeListener {
        tabbedPane.preferredSize =
          calculatePreferredTabbedPaneSize(tabbedPane, tabbedPane.selectedComponent)
      }
    }
  }

  private fun createToolbar(
    editor: Editor,
    targetComponent: JComponent,
    place: String,
  ): JComponent {
    val wrapAction =
      object : AbstractToggleUseSoftWrapsAction(SoftWrapAppliancePlaces.CONSOLE, false) {
        init {
          ActionUtil.copyFrom(this, IdeActions.ACTION_EDITOR_USE_SOFT_WRAPS)
        }

        override fun getEditor(e: AnActionEvent) = editor

        override fun getActionUpdateThread() = ActionUpdateThread.EDT
      }
    val toolbar =
      ActionManager.getInstance().createActionToolbar(place, DefaultActionGroup(wrapAction), true)
    toolbar.targetComponent = targetComponent
    toolbar.layoutStrategy = ToolbarLayoutStrategy.NOWRAP_STRATEGY
    toolbar.setReservePlaceAutoPopupIcon(false)
    toolbar.component.isOpaque = false
    ActionToolbarUtil.makeToolbarNavigable(toolbar)
    return toolbar.component
  }

  private fun calculatePreferredTabbedPaneSize(tabbedPane: JBTabbedPane, component: Component) =
    Dimension(tabbedPane.preferredWidth, component.preferredSize.height + component.bounds.y)

  private fun createPanelWithBlankSpaceAtTop(comp: JComponent) =
    JPanel().apply {
      background = primaryContentBackground
      border = CustomLineBorder(JSeparator().foreground, 1, 0, 0, 0)
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      add(Box.createVerticalStrut(8))
      add(comp)
    }
}
