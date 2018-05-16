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
package com.android.tools.idea.gradle.structure.configurables.ui.properties

import com.android.tools.idea.gradle.structure.model.VariablesProvider
import com.android.tools.idea.gradle.structure.model.meta.ModelPropertyContext
import com.android.tools.idea.gradle.structure.model.meta.ModelPropertyCore
import com.intellij.ui.components.JBLabel
import javax.swing.JComponent

abstract class PropertyEditorBase<out ModelPropertyT : ModelPropertyCore<*>, ValueT : Any>(
  val property: ModelPropertyT,
  val propertyContext: ModelPropertyContext<ValueT>,
  protected val variablesProvider: VariablesProvider?,
  protected val extensions: List<EditorExtensionAction>
) {

  abstract val component: JComponent

  val labelComponent: JBLabel = JBLabel(property.description).also {
    it.labelFor = component
  }
}