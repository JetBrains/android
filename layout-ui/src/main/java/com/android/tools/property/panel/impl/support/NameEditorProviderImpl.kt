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
package com.android.tools.property.panel.impl.support

import com.android.tools.property.panel.api.EditorContext
import com.android.tools.property.panel.api.EditorProvider
import com.android.tools.property.panel.api.NewPropertyItem
import com.android.tools.property.panel.api.PropertyEditorModel
import com.android.tools.property.panel.impl.model.PropertyNameEditorModel
import com.android.tools.property.panel.impl.ui.PropertyTextField
import javax.swing.JComponent

/**
 * A standard provider for a property name editor.
 *
 * For a given property this class will provide a model and a UI for an editor of that property
 * name. There is only one implementation at this time.
 */
class NameEditorProviderImpl<in P : NewPropertyItem> : EditorProvider<P> {

  override fun createEditor(
    property: P,
    context: EditorContext,
  ): Pair<PropertyEditorModel, JComponent> {
    val model = PropertyNameEditorModel(property)
    val editor = PropertyTextField(model)
    return Pair(model, editor)
  }
}
