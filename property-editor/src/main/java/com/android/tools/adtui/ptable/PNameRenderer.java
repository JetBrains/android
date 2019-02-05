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
package com.android.tools.adtui.ptable;

import com.android.tools.adtui.common.SwingCoordinate;
import org.jetbrains.annotations.NotNull;

import javax.swing.table.TableCellRenderer;

public interface PNameRenderer extends TableCellRenderer {

  /**
   * Returns true if the given coordinate is considered on a star icon.
   */
  default boolean hitTestStarIcon(@SwingCoordinate int x, @SwingCoordinate int y) {
    return false;
  }

  /**
   * Returns true if the given coordinate is considered on the expand/collapse icon.
   */
  default boolean hitTestTreeNodeIcon(@NotNull PTableItem item, @SwingCoordinate int x, @SwingCoordinate int y) {
    return false;
  }
}
