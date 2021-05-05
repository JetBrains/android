/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.devicemanager.virtualtab

import com.android.tools.adtui.stdui.CommonButton
import com.android.tools.idea.avdmanager.AvdActionPanel
import com.android.tools.idea.avdmanager.AvdUiAction
import com.android.tools.idea.avdmanager.CreateAvdAction
import com.android.tools.idea.flags.StudioFlags
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.layout.panel
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.border.Border
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * A toolbar which contains a [panel] which is an UI element.
 */
class VirtualToolbar(
  avdInfoProvider: AvdUiAction.AvdInfoProvider,
  private val avdRefreshProvider: AvdActionPanel.AvdRefreshProvider,
  searchDocumentListener: DocumentListener
) {
  private val createAvdAction = CreateAvdAction(avdInfoProvider)
  private val newButton = JButton("Create device").apply {
    addActionListener(createAvdAction)
  }

  private val refreshButton = CommonButton(AllIcons.Actions.Refresh).apply {
    addActionListener { avdRefreshProvider.refreshAvds() }
  }

  private val helpButton = CommonButton(AllIcons.Actions.Help).apply {
    addActionListener { BrowserUtil.browse("http://developer.android.com/r/studio-ui/virtualdeviceconfig.html") }
  }

  private val separator = Separator()

  private val searchField = createSearchField("Search virtual devices by name").apply {
    addDocumentListener(searchDocumentListener)
  }

  // TODO(qumeric): probably we should implement some interface with CommonToolbar instead of having a public field.
  // Consider using delegation? Or some pattern like MVVM.
  val panel = panel {
    row {
      newButton().withLeftGap()
      separator()
      refreshButton()
      if (StudioFlags.ENABLE_DEVICE_MANAGER_HALF_BAKED_FEATURES.get()) {
        helpButton()
        searchField(growX, pushX)
      }
      else {
        helpButton(pushX)
      }
    }
  }
}

// From the resource explorer toolbar. Move to adtui?
private fun createSearchField(name: String, gap: Int = JBUI.scale(10)) = SearchTextField(true).apply {
  isFocusable = true
  toolTipText = name
  accessibleContext.accessibleName = name
  textEditor.columns = gap
  textEditor.document.addDocumentListener(object : DocumentAdapter() {
    override fun textChanged(e: DocumentEvent) {
      // TODO(qumeric)
    }
  })
}

// TODO(qumeric): consider moving it to adtui?
class Separator(
  border: Border = JBUI.Borders.empty(0, 4),
  background: Color = JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()
) : JComponent() {

  private val lineWidth = 1

  init {
    this.background = background
    this.border = border
  }

  override fun paint(g: Graphics) = with(g) {
    color = background
    fillRect(insets.left, insets.top, JBUI.scale(lineWidth), height - insets.top - insets.bottom)
  }

  override fun getPreferredSize(): Dimension {
    val width = JBUI.scale(lineWidth) + insets.left + insets.right
    val height: Int = JBUI.scale(16)
    return Dimension(width, height)
  }
}
