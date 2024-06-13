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
package com.android.tools.property.panel.api

import com.android.tools.property.panel.impl.support.SimpleControlTypeProvider
import com.android.tools.property.panel.impl.table.TableUIProviderImpl
import com.android.tools.property.ptable.PTableCellEditorProvider
import com.android.tools.property.ptable.PTableCellRendererProvider

interface TableUIProvider {
  val tableCellRendererProvider: PTableCellRendererProvider
  val tableCellEditorProvider: PTableCellEditorProvider
}

/** A [TableUIProvider] for editing of property values only. */
inline fun <reified P : PropertyItem> TableUIProvider(
  valueControlTypeProvider: ControlTypeProvider<P>,
  valueEditorProvider: EditorProvider<P>,
): TableUIProvider {
  return TableUIProviderImpl(
    NewPropertyItem::class.java,
    SimpleControlTypeProvider(ControlType.TEXT_EDITOR),
    EditorProvider.createForNames(),
    P::class.java,
    valueControlTypeProvider,
    valueEditorProvider,
  )
}

/** A [TableUIProvider] for editing of property values and property names. */
inline fun <reified P : PropertyItem, reified N : NewPropertyItem> TableUIProvider(
  nameControlTypeProvider: ControlTypeProvider<N>,
  nameEditorProvider: EditorProvider<N>,
  valueControlTypeProvider: ControlTypeProvider<P>,
  valueEditorProvider: EditorProvider<P>,
): TableUIProvider {

  return TableUIProviderImpl(
    N::class.java,
    nameControlTypeProvider,
    nameEditorProvider,
    P::class.java,
    valueControlTypeProvider,
    valueEditorProvider,
  )
}
