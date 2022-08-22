/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.compose.pickers.common.enumsupport

import com.android.tools.idea.compose.pickers.base.enumsupport.EnumSupportValuesProvider
import com.android.tools.property.panel.api.EnumSupport
import com.android.tools.property.panel.api.EnumValue
import javax.swing.ListCellRenderer

/**
 * [EnumSupport] that provides its values lazily.
 *
 * Normally, if we are going to run long execution when acquiring values, we are expected to do it
 * every time the property is called, but if we don't expect different data after the first
 * invocation, we can use a lazy property.
 */
internal class EnumSupportWithConstantData(
  enumSupportValuesProvider: EnumSupportValuesProvider,
  key: String,
  private val customCreateValue: ((String) -> EnumValue)? = null
) : EnumSupport {
  override val values: List<EnumValue> by lazy {
    return@lazy enumSupportValuesProvider.getValuesProvider(key)?.invoke() ?: emptyList()
  }

  override fun createValue(stringValue: String): EnumValue {
    return customCreateValue?.invoke(stringValue) ?: super.createValue(stringValue)
  }

  override val renderer: ListCellRenderer<EnumValue> = PsiEnumValueCellRenderer()
}
