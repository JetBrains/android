/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.android.designer.model.grid;

/**
 * Defines the position of the drop position detection.
 * @see com.intellij.android.designer.designSurface.layout.grid.GridOperation#calculateGridInfo
 */
public enum GridInsertType {
  /** Drop position is well within the cell or in an empty cell. */
  in_cell,
  /** In an existing cell with components, before the components in the Y axis (top) */
  before_h_cell,
  /** In an existing cell with components, after the components in the Y axis (bottom) */
  after_h_cell,
  /** In an existing cell with components, before the components in the X axis (left) */
  before_v_cell,
  /** In an existing cell with components, after the components in the X axis (right) */
  after_v_cell,
  /** In an existing cell, top left side. */
  corner_top_left,
  /** In an existing cell, top right side. */
  corner_top_right,
  /** In an existing cell, bottom left side. */
  corner_bottom_left,
  /** In an existing cell, bottom right side. */
  corner_bottom_right
}