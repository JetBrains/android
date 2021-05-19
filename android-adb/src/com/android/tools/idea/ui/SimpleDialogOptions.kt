/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import javax.swing.JComponent

/**
 * Creation options for [SimpleDialog]
 */
data class SimpleDialogOptions(
  val project: Project,
  val canBeParent: Boolean,
  val ideModalityType: DialogWrapper.IdeModalityType,
  val title: String,
  val isModal: Boolean,
  /** Lambda that creates the center panel of the dialog */
  val centerPanelProvider: () -> JComponent,
  val preferredFocusProvider: () -> JComponent? = { null },
  val hasOkButton: Boolean = true,
  val okButtonText: String? = null,
  val okActionHandler: () -> Boolean = { false },
  val cancelButtonText: String? = null,
  val validationHandler: () -> ValidationInfo? = { null }
)
