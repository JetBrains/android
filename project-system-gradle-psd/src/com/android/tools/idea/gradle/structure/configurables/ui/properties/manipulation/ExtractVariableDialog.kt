/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.ui.properties.manipulation

import com.android.tools.idea.gradle.structure.configurables.ui.properties.ModelPropertyEditorFactory
import com.android.tools.idea.gradle.structure.model.PsProject
import com.android.tools.idea.gradle.structure.model.PsVariablesScope
import com.android.tools.idea.gradle.structure.model.helpers.ExtractVariableWorker
import com.android.tools.idea.gradle.structure.model.meta.ModelPropertyCore
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.JBColor
import javax.swing.JComponent

class ExtractVariableDialog<PropertyT : Any, ModelPropertyCoreT : ModelPropertyCore<PropertyT>>(
  project: PsProject,
  scope: PsVariablesScope,
  refactoredProperty: ModelPropertyCoreT,
  private var editorFactory: ModelPropertyEditorFactory<PropertyT, ModelPropertyCoreT>
) : DialogWrapper(project.ideProject) {

  private var form: ExtractVariableForm? = null
  private val name: String get() = form?.myNameField?.text ?: ""
  private val scopes = scope.getVariableScopes().associateBy { it.title }

  private val worker = ExtractVariableWorker(refactoredProperty)

  init {
    title = "Extract Variable"
    init()
  }

  override fun createCenterPanel(): JComponent? {
    val form = ExtractVariableForm()

    scopes.forEach { scope, _ -> form.myScopeField!!.addItem(scope) }

    // Prefer Project scope. (The first scope in the list is expected to be the build script scope.)
    val defaultScope = scopes.keys.drop(1).firstOrNull() ?: scopes.keys.first()
    configureFormFor(form, defaultScope)

    form.myScopeField.addActionListener {
      configureFormFor(form, form.myScopeField.selectedItem as String)
    }
    form.myScopeField.background = JBColor.background()

    this.form = form
    return form.myPanel
  }

  private fun configureFormFor(form: ExtractVariableForm, scope: String) {
    val (newName, property) = worker.changeScope(scopes[scope]!!, form.myNameField.text)
    val editor = editorFactory.createNew(property, isPropertyContext = false)
    form.myNameField.text = newName
    form.setValueEditor(editor.component)
    form.myScopeField.selectedItem = scope
  }

  override fun doValidate(): ValidationInfo? =
    worker.validate(name)?.let { ValidationInfo(it).withOKEnabled() }

  override fun doCancelAction() {
    super.doCancelAction()
    worker.cancel()
  }

  override fun doOKAction() {
    super.doOKAction()
    worker.commit(name)
  }

  override fun getPreferredFocusedComponent(): JComponent? {
    return form?.myNameField
  }
}