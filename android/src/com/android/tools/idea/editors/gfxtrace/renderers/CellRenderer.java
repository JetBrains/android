/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.gfxtrace.renderers;

import com.android.tools.idea.editors.gfxtrace.widgets.CellWidget;
import com.android.tools.idea.editors.gfxtrace.widgets.Repaintable;
import com.android.tools.idea.editors.gfxtrace.widgets.Repaintables;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public abstract class CellRenderer<T extends CellWidget.Data> implements ListCellRenderer {
  @NotNull private final CellLoader<T> myCellLoader;
  @NotNull private final T myNullCell;

  public CellRenderer(CellLoader<T> loader) {
    myCellLoader = loader;
    myNullCell = createNullCell();
  }

  protected abstract T createNullCell();

  @Override
  public Component getListCellRendererComponent(
      @NotNull final JList list, Object data, int index, boolean isSelected, boolean cellHasFocus) {

    final T cell;
    if (data != null) {
      assert (data instanceof CellWidget.Data);
      cell = (T)data;
    } else {
      cell = myNullCell;
    }
    if (cell.requiresLoading()) {
      myCellLoader.loadCell(cell, new Runnable() {
        @Override
        public void run() {
          onCellLoaded(list, cell);
        }
      });
    }
    cell.isSelected = isSelected;
    return getRendererComponent(list, cell);
  }

  protected Repaintable getRepaintable(JList list) {
    return Repaintables.forComponent(list);
  }

  protected void onCellLoaded(JList list, T cell) {
  }

  protected abstract Component getRendererComponent(@NotNull JList list, @NotNull T cell);

  public abstract Dimension getInitialCellSize();

  public interface CellLoader<T extends CellWidget.Data> {
    void loadCell(T cell, Runnable onLoad);
  }
}
