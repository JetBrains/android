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

import org.jetbrains.annotations.NotNull;

import javax.swing.table.TableCellRenderer;

public class PTableDefaultCellRendererProvider implements PTableCellRendererProvider {
  private final PNameRenderer myRenderer;

  PTableDefaultCellRendererProvider() {
    myRenderer = new DefaultRenderer();
  }

  @NotNull
  @Override
  public PNameRenderer getNameCellRenderer(@NotNull PTableItem item) {
    return myRenderer;
  }

  @NotNull
  @Override
  public TableCellRenderer getValueCellRenderer(@NotNull PTableItem item) {
    return myRenderer;
  }

  private static class DefaultRenderer extends PTableCellRenderer implements PNameRenderer {
    @Override
    protected void customizeCellRenderer(@NotNull PTable table, @NotNull PTableItem value,
                                         boolean selected, boolean hasFocus, int row, int column) {
    }
  }
}
