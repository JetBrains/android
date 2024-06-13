/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.adtui.model.stdui.ValueChangedListener

/**
 * The expansion state of a table cell editor.
 *
 * If the editor is in a table cell, the cell may be too small to show the complete value. The
 * [TableExpansionState] describes the state the editor should be rendered as.
 */
enum class TableExpansionState {
  /** The property editor is not expanded or not in a table. */
  NORMAL,

  /** The property editor should render the left part of an expanded value inside a table cell. */
  EXPANDED_CELL_FOR_POPUP,

  /** The property editor should render expanded, but the text fits in the table cell. */
  EXPANDED_CELL_NO_POPUP,

  /**
   * The property editor is being used to render the entire value. The right part will be shown in a
   * popup.
   */
  EXPANDED_POPUP,
}

/**
 * Model for a property editor.
 *
 * Instances of this class will be created for each property editor in the [InspectorPanel] by
 * [InspectorBuilder]s. If only builtin editors are created (preferred) then clients should never
 * need to implement this interface.
 */
interface PropertyEditorModel {
  /**
   * The property this editor is for.
   *
   * This [property] is usually readonly except for models used during table cell rendering where we
   * want to cache editors for displaying properties of the same type.
   */
  var property: PropertyItem

  /** The value shown in the editor. */
  val value: String

  /** The default value shown in the editor if the value is blank */
  val defaultValue: String

  /** Returns the line (if known) where this editor is kept. */
  var lineModel: InspectorLineModel?

  /**
   * Controls the visibility of the editor.
   *
   * The editor should show/hide itself based on this value.
   */
  var visible: Boolean

  /**
   * Controls whether the editor is readonly.
   *
   * The editor should update itself to ignore focus requests and mouse interactions.
   */
  var readOnly: Boolean

  /** Returns true if the editor currently has the focus. */
  val hasFocus: Boolean

  /**
   * Controls the visuals of the editor.
   *
   * The editor is a table cell renderer. If true the editor should display itself as being a
   * selected state.
   */
  var isUsedInRendererWithSelection: Boolean

  /**
   * Controls the visuals of the editor.
   *
   * The editor is a table cell renderer. If true the editor is currently displaying a cell in an
   * expanded table row. The editor may decide to show different content based on this value.
   */
  var isExpandedTableItem: Boolean

  /**
   * Controls the visuals of the editor.
   *
   * An item can be expanded in a popup when the value if too large to display in a table cell.
   */
  var tableExpansionState: TableExpansionState

  /** Returns true if the editor may require a custom height. */
  var isCustomHeight: Boolean

  /**
   * Returns support for table operations if this is a table cell editor (null otherwise).
   *
   * When a property editor is created for a table cell [tableSupport] will be specified to provide
   * table access from events in the editor.
   */
  var tableSupport: TableSupport?

  /** Request focus to be placed on this editor. */
  fun requestFocus()

  /**
   * Toggle the value of this editor.
   *
   * This is a noop for most editors. Boolean editors should implement this method.
   */
  fun toggleValue()

  /** Update the value shown in the editor. */
  fun refresh()

  /**
   * Cancel editing and revert to the value of the property.
   *
   * i.e. discard changes by the editor that are not yet applied. The return value is used if this
   * is a table cell editor. Return true to stop cell editing, false to remain editing the cell.
   */
  fun cancelEditing(): Boolean

  /** Add a listener that respond to value changes. */
  fun addListener(listener: ValueChangedListener)

  /** Remove the value listener. */
  fun removeListener(listener: ValueChangedListener)
}
