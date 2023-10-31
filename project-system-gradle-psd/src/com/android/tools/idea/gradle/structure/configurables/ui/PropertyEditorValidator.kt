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
package com.android.tools.idea.gradle.structure.configurables.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.DocumentAdapter
import javax.swing.event.DocumentEvent
import javax.swing.text.JTextComponent

/**
 * Class installs validation function as component validator for a JTextComponent. Validation function will run each time text is changed
 * in the component.
 *
 * validationFunction takes sting to validate and returns null if input is correct or validation message otherwise
 */
class PropertyEditorValidator(
  private val project: Project,
  private val validationFunction: ((String) -> String?)
) {
  fun installValidation(component: JTextComponent) {
    ComponentValidator(project).withValidator { ->
      val message = validationFunction.invoke(component.text)
      message?.let { ValidationInfo(it, component) }
    }.installOn(component)

    component.document?.addDocumentListener(
      object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) {
          ComponentValidator.getInstance(component).ifPresent { v: ComponentValidator -> v.revalidate() }
        }
      })
  }

  /**
   * installValidation should be called before this method.
   * Validator must be installed to component before triggering manual validation
   */
  fun validate(component: JTextComponent) {
    ComponentValidator.getInstance(component).ifPresent { v: ComponentValidator -> v.revalidate() }
  }
}