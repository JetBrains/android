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
package com.android.tools.property.panel.impl.table

import com.android.tools.property.panel.api.ControlTypeProvider
import com.android.tools.property.panel.api.EditorProvider
import com.android.tools.property.panel.api.NewPropertyItem
import com.android.tools.property.panel.api.PropertyItem
import com.android.tools.property.panel.api.TableUIProvider

class TableUIProviderImpl<P : PropertyItem, N : NewPropertyItem>(
  nameType: Class<N>,
  nameControlTypeProvider: ControlTypeProvider<N>,
  nameEditorProvider: EditorProvider<N>,
  valueType: Class<P>,
  valueControlTypeProvider: ControlTypeProvider<P>,
  valueEditorProvider: EditorProvider<P>,
) : TableUIProvider {

  override val tableCellRendererProvider =
    PTableCellRendererProviderImpl(
      nameType,
      nameControlTypeProvider,
      nameEditorProvider,
      valueType,
      valueControlTypeProvider,
      valueEditorProvider,
    )

  override val tableCellEditorProvider =
    PTableCellEditorProviderImpl(
      nameType,
      nameControlTypeProvider,
      nameEditorProvider,
      valueType,
      valueControlTypeProvider,
      valueEditorProvider,
    )
}
