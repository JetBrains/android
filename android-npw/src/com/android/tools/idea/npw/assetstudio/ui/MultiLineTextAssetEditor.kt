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
package com.android.tools.idea.npw.assetstudio.ui

import com.android.tools.adtui.TabularLayout
import com.android.tools.idea.npw.assetstudio.assets.TextAsset
import com.android.tools.idea.observable.BindingsManager
import com.android.tools.idea.observable.InvalidationListener
import com.android.tools.idea.observable.core.ObjectProperty
import com.android.tools.idea.observable.ui.SelectedItemProperty
import com.android.tools.idea.observable.ui.TextProperty
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.JBColor
import com.intellij.ui.border.CustomLineBorder
import com.intellij.util.ArrayUtil
import java.awt.Component
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.DefaultListCellRenderer
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JTextPane

/** Panel which wraps a [TextAsset], allowing the user to enter multi-line text and choose a font from a pulldown. */
class MultiLineTextAssetEditor : JPanel(TabularLayout("4px,120px,*")), AssetComponent<TextAsset?> {
  private val textAsset = TextAsset()
  private val bindings = BindingsManager()
  private val listeners: MutableList<ActionListener> = ArrayList(1)

  init {
    val spacer = JPanel()
    val textPane =
      JTextPane().apply {
        setBackground(JBColor.WHITE)
        setBorder(CustomLineBorder(JBColor.border(), 1, 1, 1, 1))
      }
    val fontComboWrapper = JPanel(GridBagLayout()).apply { setOpaque(false) }
    val constraints =
      GridBagConstraints().apply {
        this.fill = GridBagConstraints.HORIZONTAL
        this.weightx = 1.0
      }

    val fontFamilies = TextAsset.getAllFontFamilies()
    val fontCombo = ComboBox(ArrayUtil.toStringArray(fontFamilies))
    fontCombo.setRenderer(
      object : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
          list: JList<*>?,
          value: Any?,
          index: Int,
          isSelected: Boolean,
          cellHasFocus: Boolean,
        ): Component {
          val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
          if (value is String) {
            component.font = Font(value, Font.PLAIN, component.font.size)
          }
          return component
        }
      }
    )

    fontComboWrapper.add(fontCombo, constraints)

    add(spacer, TabularLayout.Constraint(0, 0))
    add(textPane, TabularLayout.Constraint(0, 1))
    add(fontComboWrapper, TabularLayout.Constraint(0, 2))
    bindings.bindTwoWay(TextProperty(textPane), textAsset.text())

    val selectedFont = SelectedItemProperty<String?>(fontCombo)
    bindings.bindTwoWay(ObjectProperty.wrap<String?>(selectedFont), textAsset.fontFamily())

    val onTextChanged = InvalidationListener {
      val e = ActionEvent(this, ActionEvent.ACTION_PERFORMED, null)
      for (listener in listeners) {
        listener.actionPerformed(e)
      }
    }

    textAsset.text().addListener(onTextChanged)
    textAsset.fontFamily().addListener(onTextChanged)
  }

  override fun getAsset(): TextAsset {
    return textAsset
  }

  override fun addAssetListener(listener: ActionListener) {
    listeners.add(listener)
  }

  override fun dispose() {
    bindings.releaseAll()
    listeners.clear()
  }
}
