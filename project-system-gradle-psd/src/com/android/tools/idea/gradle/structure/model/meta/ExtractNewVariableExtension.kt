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
package com.android.tools.idea.gradle.structure.model.meta

import com.android.tools.idea.gradle.structure.configurables.ui.properties.EditorExtensionAction
import com.android.tools.idea.gradle.structure.configurables.ui.properties.ModelPropertyEditor
import com.android.tools.idea.gradle.structure.configurables.ui.properties.ModelPropertyEditorFactory
import com.android.tools.idea.gradle.structure.configurables.ui.properties.manipulation.ExtractVariableDialog
import com.android.tools.idea.gradle.structure.model.PsModule
import com.android.tools.idea.gradle.structure.model.PsProject
import com.intellij.icons.AllIcons
import javax.swing.Icon

class ExtractNewVariableExtension<T : Any, PropertyCoreT : ModelPropertyCore<T>>(
  private val project: PsProject,
  private val module: PsModule?
) : EditorExtensionAction<T, PropertyCoreT> {
  override val title: String = "Extract Variable"
  override val tooltip: String = "Extract Variable"
  override val icon: Icon = AllIcons.Nodes.Variable
  override val isMainAction: Boolean = true

  override fun isAvailableFor(property: PropertyCoreT, isPropertyContext: Boolean): Boolean =
      property is GradleModelCoreProperty<*, *> && isPropertyContext

  override fun invoke(
    property: PropertyCoreT,
    editor: ModelPropertyEditor<T>,
    editorFactory: ModelPropertyEditorFactory<T, PropertyCoreT>
  ) {
    editor.updateProperty()
    val dialog =
      ExtractVariableDialog(
        project,
        property.variableScope?.let { it() } ?: module?.variables ?: project.variables,
        property,
        editorFactory)
    if (dialog.showAndGet()) {
      editor.reload()
    }
  }
}
