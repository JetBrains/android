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
package com.android.tools.property.panel.impl.model.util

import com.intellij.ui.components.JBList
import java.awt.event.KeyAdapter
import java.awt.event.KeyListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseListener
import java.awt.event.MouseMotionListener
import javax.swing.JComboBox
import javax.swing.JList
import javax.swing.plaf.basic.ComboPopup

class FakeComboPopup(private val comboBox: JComboBox<Any>) : ComboPopup {
  private var visible = false
  private val list = JBList(comboBox.model)
  private val mouseListener = object : MouseAdapter() {}
  private val keyListener = object : KeyAdapter() {}

  override fun getMouseListener(): MouseListener = mouseListener

  override fun getMouseMotionListener(): MouseMotionListener = mouseListener

  override fun getKeyListener(): KeyListener = keyListener

  override fun hide() {
    comboBox.firePopupMenuWillBecomeInvisible()
    visible = false
  }

  override fun show() {
    comboBox.firePopupMenuWillBecomeVisible()
    setListSelection(comboBox.selectedIndex)
    visible = true
  }

  private fun setListSelection(selectedIndex: Int) {
    if (selectedIndex == -1) {
      list.clearSelection()
    }
    else {
      list.selectedIndex = selectedIndex
      list.ensureIndexIsVisible(selectedIndex)
    }
  }

  override fun isVisible(): Boolean = visible

  override fun getList(): JList<Any> = list

  override fun uninstallingUI() {}
}