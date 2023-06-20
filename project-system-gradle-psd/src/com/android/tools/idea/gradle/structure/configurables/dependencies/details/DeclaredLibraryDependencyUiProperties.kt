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
package com.android.tools.idea.gradle.structure.configurables.dependencies.details

import com.android.tools.idea.gradle.structure.configurables.ui.PropertyEditorFactory
import com.android.tools.idea.gradle.structure.configurables.ui.PropertyUiModel
import com.android.tools.idea.gradle.structure.configurables.ui.PropertyUiModelImpl
import com.android.tools.idea.gradle.structure.configurables.ui.TextRenderer
import com.android.tools.idea.gradle.structure.configurables.ui.noExtractButtonPropertyEditor
import com.android.tools.idea.gradle.structure.configurables.ui.properties.SimplePropertyEditor
import com.android.tools.idea.gradle.structure.configurables.ui.simplePropertyEditor
import com.android.tools.idea.gradle.structure.model.PsDeclaredLibraryDependency
import com.android.tools.idea.gradle.structure.model.PsModule
import com.android.tools.idea.gradle.structure.model.PsProject
import com.android.tools.idea.gradle.structure.model.PsVariablesScope
import com.android.tools.idea.gradle.structure.model.meta.ModelProperty
import com.android.tools.idea.gradle.structure.model.meta.ModelPropertyCore
import javax.swing.table.TableCellEditor
import com.android.tools.idea.gradle.structure.configurables.ui.properties.renderEmptyTo

object DeclaredLibraryDependencyUiProperties {
  fun makeVersionUiProperty(dependency: PsDeclaredLibraryDependency): PropertyUiModel<Unit, *> =
    PropertyUiModelImpl(dependency.versionProperty,
                        getFactoryMethod(dependency),
                        psdUsageLogFieldId = null)

  /**
   * Wrapping together SimplePropertyEditor builder function with adding custom renderer in order
   * to show empty property with /*not specified*/ watermark
   */
  private fun <PropertyT : Any, ModelT : Any,
    ModelPropertyT : ModelProperty<ModelT, PropertyT, PropertyT, ModelPropertyCore<PropertyT>>> getFactoryMethod(
    dependency: PsDeclaredLibraryDependency
  ): PropertyEditorFactory<ModelT, ModelPropertyT, PropertyT> = {
    project: PsProject,
    module: PsModule?,
    model: ModelT,
    property: ModelPropertyT,
    variablesScope: PsVariablesScope?,
    cellEditor: TableCellEditor?,
    logValueEdited: () -> Unit ->
    val editor: SimplePropertyEditor<PropertyT, ModelPropertyCore<PropertyT>> =
      if (dependency.canExtractVariable()) {
        simplePropertyEditor(project, module, model, property, variablesScope, cellEditor, logValueEdited)
      }
      else {
        noExtractButtonPropertyEditor(project, module, model, property, variablesScope, cellEditor, logValueEdited)
      }
    editor.customRenderTo = { renderer: TextRenderer, _, _ -> renderEmptyTo(renderer) }
    editor
  }

}