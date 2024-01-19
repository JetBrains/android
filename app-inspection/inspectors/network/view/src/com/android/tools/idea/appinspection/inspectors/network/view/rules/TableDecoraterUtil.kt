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
package com.android.tools.idea.appinspection.inspectors.network.view.rules

import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.JTable
import javax.swing.KeyStroke

fun JTable.registerEnterKeyAction(action: (ActionEvent) -> Unit) {
  registerKeyboardAction(
    object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent) = action(e)
    },
    "Press enter",
    KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
    JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT,
  )
}

fun JTable.registerTabKeyAction(action: (ActionEvent) -> Unit) {
  registerKeyboardAction(
    object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent) = action(e)
    },
    "Press tab",
    KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0),
    JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT,
  )
}
