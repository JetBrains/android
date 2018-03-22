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
package com.android.tools.idea.uibuilder.property2.model

import com.android.annotations.VisibleForTesting
import com.android.tools.idea.common.property2.api.FormModel
import com.android.tools.idea.common.property2.api.PropertyEditorModel
import com.android.tools.idea.common.property2.api.PropertyItem
import com.android.tools.idea.common.property2.impl.model.BasePropertyEditorModel

/**
 * Model for an editor with one or more sub editors.
 *
 * This model forwards focus requests to the first sub editor, and provides
 * a way to move focus between the sub editors.
 */
class HorizontalEditorPanelModel(property: PropertyItem, formModel: FormModel) : BasePropertyEditorModel(property, formModel) {
  @VisibleForTesting
  val models = mutableListOf<PropertyEditorModel>()

  fun add(childModel: PropertyEditorModel) {
    models.add(childModel)
  }

  fun prior() {
    val index = focusIndex
    val model = if (index <= 0) models.last() else models[index - 1]
    model.requestFocus()
  }

  fun next() {
    val index = focusIndex
    val model = if (index < 0 || index == models.lastIndex) models.first() else models[index + 1]
    model.requestFocus()
  }

  private val focusIndex: Int
    get() = models.indexOfFirst { it.hasFocus }


  override fun requestFocus() {
    models.firstOrNull()?.requestFocus()
  }

  override fun refresh() {
    models.forEach { it.refresh() }
  }
}
