/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.common.property.editors

import com.android.tools.adtui.common.AdtSecondaryPanel
import com.android.tools.idea.common.property.NlProperty
import com.android.tools.idea.uibuilder.property.editors.NlEditingListener
import com.intellij.ide.ui.laf.darcula.ui.DarculaEditorTextFieldBorder
import com.intellij.openapi.command.undo.UndoConstants
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorTextField
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Insets
import javax.swing.JComponent
import javax.swing.border.EmptyBorder
import javax.swing.plaf.InsetsUIResource

class NonEditableEditor(listener: NlEditingListener, project: Project) : BaseComponentEditor(listener) {
  val component = AdtSecondaryPanel(BorderLayout())

  // TODO: factor this behavior out of here and TextEditorWithAutoCompletion
  val textField = object: EditorTextField("", project, FileTypes.PLAIN_TEXT) {
    override fun addNotify() {
      super.addNotify()
      editor?.document?.putUserData(UndoConstants.DONT_RECORD_UNDO, true)
      editor?.setBorder(object : DarculaEditorTextFieldBorder() {
        override fun getBorderInsets(component: Component): Insets {
          val myEditorInsets = JBUI.insets(VERTICAL_SPACING + VERTICAL_PADDING,
              HORIZONTAL_PADDING,
              VERTICAL_SPACING + VERTICAL_PADDING,
              HORIZONTAL_PADDING)
          return InsetsUIResource(myEditorInsets.top, myEditorInsets.left, myEditorInsets.bottom, myEditorInsets.right)
        }
      })
    }

    override fun removeNotify() {
      super.removeNotify()

      // Remove the editor from the component tree.
      // The editor component is added in EditorTextField.addNotify but never removed by EditorTextField.
      // This is causing paint problems when this component is reused in a different panel.
      removeAll()
    }
  }
  private lateinit var _property: NlProperty

  constructor(project: Project) : this(NlEditingListener.DEFAULT_LISTENER, project)

  init {
    component.border = EmptyBorder(VERTICAL_PADDING, HORIZONTAL_SPACING, VERTICAL_PADDING, HORIZONTAL_SPACING)
    component.add(textField, BorderLayout.CENTER)
    setEnabled(false)
    textField.isEnabled = false
  }

  override fun getComponent(): JComponent = component

  override fun getProperty() = _property

  override fun setProperty(property: NlProperty) {
    _property = property
    textField.text = value
  }

  override fun getValue(): String? {
    return _property.value
  }
}
