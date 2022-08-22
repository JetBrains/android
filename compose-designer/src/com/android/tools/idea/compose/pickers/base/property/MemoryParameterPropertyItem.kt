/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.compose.pickers.base.property

import com.android.tools.adtui.model.stdui.EDITOR_NO_ERROR
import com.android.tools.adtui.model.stdui.EditingSupport
import com.android.tools.adtui.model.stdui.EditingValidation
import com.android.tools.idea.compose.pickers.base.editingsupport.PsiEditingSupport

/**
 * A [PsiPropertyItem] that only exists on memory. Changes on this property will not immediately
 * reflect on a file.
 */
open class MemoryParameterPropertyItem(
  override var name: String,
  override val defaultValue: String?,
  inputValidation: EditingValidation = { EDITOR_NO_ERROR }
) : PsiPropertyItem {
  override var value: String? = null

  override val editingSupport: EditingSupport = PsiEditingSupport(inputValidation)
}
