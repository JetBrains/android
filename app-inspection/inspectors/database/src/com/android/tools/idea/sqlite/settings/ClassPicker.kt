/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.sqlite.settings

import com.android.tools.idea.run.activity.ActivityLocatorUtils
import com.android.tools.idea.sqlite.localization.DatabaseInspectorBundle.message
import com.intellij.ide.util.TreeClassChooserFactory
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComponentWithBrowseButton
import com.intellij.openapi.util.Key
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.search.ProjectScope
import com.intellij.ui.EditorTextField
import com.intellij.ui.JBColor
import com.intellij.ui.LanguageTextField
import com.intellij.ui.TextAccessor
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * A component for picking a class
 *
 * Based on `SpecificActivityConfigurable`
 */
class ClassPicker(private val project: Project, private val base: String) :
  JPanel(null), TextAccessor {

  private val editorTextField =
    ClassTextField(project, base).apply { addDocumentListener(Validator()) }

  private val textComponent =
    ComponentWithBrowseButton<EditorTextField>(editorTextField, ClassPickerActionListener()).apply {
      alignmentX = LEFT_ALIGNMENT
      name = "textComponent"
    }
  private val errorLabel =
    JLabel().apply {
      isVisible = false
      foreground = JBColor.red
      alignmentX = LEFT_ALIGNMENT
      name = "errorLabel"
    }

  private val facade = JavaPsiFacade.getInstance(project)
  private val scope = ProjectScope.getAllScope(project)

  init {
    layout = BoxLayout(this, BoxLayout.PAGE_AXIS)
    add(textComponent)
    add(errorLabel)
  }

  override fun setText(value: String) {
    editorTextField.text = value
  }

  override fun getText(): String = editorTextField.text

  override fun setEnabled(enabled: Boolean) {
    super.setEnabled(enabled)
    textComponent.isEnabled = enabled
  }

  fun addDocumentListener(listener: DocumentListener) {
    editorTextField.addDocumentListener(listener)
  }

  private inner class Validator : DocumentListener {
    override fun documentChanged(event: DocumentEvent) {
      errorLabel.isVisible = false
      if (ActionUtil.isDumbMode(project)) {
        return
      }
      val name = event.document.text
      if (name.isEmpty()) {
        return
      }
      val cls = findClass(name)
      if (cls == null) {
        errorLabel.text = message("class.selector.not.found.error")
        errorLabel.isVisible = true
        return
      }
      val baseClass = findClass(base) ?: return
      if (!cls.isInheritor(baseClass, true)) {
        errorLabel.text = message("class.selector.type.error", base.substringAfterLast('.'))
        errorLabel.isVisible = true
      }
    }
  }

  private class ClassTextField(project: Project, private val base: String) :
    LanguageTextField(PlainTextLanguage.INSTANCE, project, "") {
    override fun createEditor(): EditorEx {
      val editor = super.createEditor()
      editor.putUserData(BASE_CLASS_KEY, base)
      return editor
    }
  }

  private fun findClass(name: String): PsiClass? {
    return facade.findClass(name, scope)
  }

  private inner class ClassPickerActionListener : ActionListener {
    override fun actionPerformed(e: ActionEvent?) {
      if (!project.isInitialized) {
        return
      }
      val baseClass = findClass(base) ?: return

      val initialClass = findClass(editorTextField.getText())

      // TODO(aalbert): Filter classes we should not track like BundledSQLiteDriver etc
      val chooser =
        project
          .service<TreeClassChooserFactory>()
          .createInheritanceClassChooser(
            message("class.selector.dialog.title", base.substringAfterLast('.')),
            scope,
            baseClass,
            initialClass,
          )
      chooser.showDialog()
      val selectedClass = chooser.selected ?: return
      editorTextField.setText(ActivityLocatorUtils.getQualifiedActivityName(selectedClass))
    }
  }

  companion object {
    // Used to pass the base class to ClassPickerCompletionContributor
    val BASE_CLASS_KEY: Key<String?> = Key.create("ClassPickerBaseClass")
  }
}
