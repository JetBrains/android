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
package com.android.tools.idea.common.property2.api

import com.android.tools.adtui.ptable2.PTableCellEditorProvider
import com.android.tools.adtui.ptable2.PTableCellRendererProvider
import com.android.tools.idea.common.property2.impl.support.SimpleControlTypeProvider
import com.android.tools.idea.common.property2.impl.table.TableUIProviderImpl

interface TableUIProvider {
  val tableCellRendererProvider: PTableCellRendererProvider
  val tableCellEditorProvider: PTableCellEditorProvider

  companion object {

    /**
     * A [TableUIProvider] for editing of property values and property names.
     */
    fun <P : PropertyItem, N : NewPropertyItem> create(
      nameType: Class<N>,
      nameControlTypeProvider: ControlTypeProvider<N>,
      nameEditorProvider: EditorProvider<N>,
      valueType: Class<P>,
      valueControlTypeProvider: ControlTypeProvider<P>,
      valueEditorProvider: EditorProvider<P>): TableUIProvider {

      return TableUIProviderImpl(nameType, nameControlTypeProvider, nameEditorProvider,
                                 valueType, valueControlTypeProvider, valueEditorProvider)
    }

    /**
     * A [TableUIProvider] for editing of property values only.
     */
    fun <P : PropertyItem> create(
      valueType: Class<P>,
      valueControlTypeProvider: ControlTypeProvider<P>,
      valueEditorProvider: EditorProvider<P>): TableUIProvider {

      return TableUIProviderImpl(
        NewPropertyItem::class.java, SimpleControlTypeProvider(ControlType.TEXT_EDITOR), EditorProvider.createForNames(),
        valueType, valueControlTypeProvider, valueEditorProvider)
    }
  }
}
