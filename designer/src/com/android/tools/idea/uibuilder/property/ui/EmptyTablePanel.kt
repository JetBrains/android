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
package com.android.tools.idea.uibuilder.property.ui

import com.android.tools.adtui.common.secondaryPanelBackground
import com.android.tools.property.panel.api.TableLineModel
import com.android.tools.property.ptable.PTableItem
import com.android.tools.property.ptable.PTableModelUpdateListener
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.util.ui.HTMLEditorKitBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.util.Locale
import javax.swing.JEditorPane
import javax.swing.JPanel
import javax.swing.event.HyperlinkEvent

private const val BORDER_SIZE = 4

/**
 * A panel with text to inform the user what to do when a table is empty.
 */
class EmptyTablePanel(private val addAction: AnAction, model: TableLineModel) : JPanel(BorderLayout()) {
  private var textPanel: JEditorPane? = null

  init {
    val text = JEditorPane()
    text.editorKit = HTMLEditorKitBuilder().build()
    text.isEditable = false
    text.isFocusable = false
    text.text = createText()
    text.isOpaque = false
    text.border = JBUI.Borders.empty(BORDER_SIZE)
    text.addHyperlinkListener { event ->
      if (event.eventType == HyperlinkEvent.EventType.ACTIVATED) {
        val context = DataManager.getInstance().getDataContext(this@EmptyTablePanel)
        val actionEvent = AnActionEvent.createFromAnAction(addAction, null, "", context)
        addAction.actionPerformed(actionEvent)
      }
    }
    textPanel = text
    add(text, BorderLayout.CENTER)
    background = secondaryPanelBackground
    isVisible = model.itemCount == 0
    model.tableModel.addListener(object: PTableModelUpdateListener {
      override fun itemsUpdated(modelChanged: Boolean, nextEditedItem: PTableItem?) {
        isVisible = model.tableModel.items.isEmpty()
      }
    })
  }

  override fun updateUI() {
    super.updateUI()
    textPanel?.text = createText()
  }

  private fun createText(): String {
    val actionText = addAction.templatePresentation.description.toLowerCase(Locale.getDefault())
    val font = StartupUiUtil.labelFont
    val color = UIUtil.getLabelForeground()
    val disabled = JBUI.CurrentTheme.Label.disabledForeground()
    val style = "<head><style>" +
                "body { " +
                "text-align: center; " +
                "font-family: \"${font.family}\"; " +
                "font-size: 100%;" +
                "font-style: normal; " +
                "color: rgb(${disabled.red},${disabled.green},${disabled.blue}); " +
                "} " +
                "a {" +
                "color: rgb(${color.red},${color.green},${color.blue}); " +
                "font-style: bold; " +
                "text-decoration: none; " +
                "} " +
                "</style></head>"
    return "<html>$style<body>Use <a href=\"1\">+</a> to $actionText for easy access</body></html>"
  }
}
