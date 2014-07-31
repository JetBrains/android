/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.editors.strings.table;

import javax.swing.table.TableCellRenderer;

public interface HeaderCellRenderer extends TableCellRenderer {
  int PADDING = 20;

  /**
   * A "minimum" width for the column.  The user can resize the column to be narrower, but when it is programmatically
   * collapsed, it will always be kept at this width or greater.
   */
  int getCollapsedWidth();

  /**
   * A width at which the column displays its full content.
   */
  int getFullExpandedWidth();

  /**
   * The minimum width at which the column displays enough of its content to be interesting.  This is used, for
   * example, to determine whether to expand or collapse a column that is between its collapsed and full expanded widths.
   */
  int getMinimumExpandedWidth();
}
