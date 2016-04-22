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
package com.android.tools.idea.editors.gfxtrace.widgets;

import com.android.tools.idea.editors.gfxtrace.renderers.CellRenderer;
import com.android.tools.idea.editors.gfxtrace.renderers.ImageCellRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * A {@link CellList} that shows an Image in each cell.
 */
public abstract class ImageCellList<T extends ImageCellList.Data> extends CellList<T> {
  public static class Data extends CellWidget.Data {
    @NotNull private final String label;
    @Nullable public ImageIcon icon;

    public Data(String label) {
      this.label = label;
    }

    @Override
    public void stopLoading() {
      // Wait for the icon before finishing.
    }

    public void stopLoading(ImageIcon icon) {
      this.icon = icon;
      super.stopLoading();
    }

    @Override
    public boolean isLoaded() {
      return super.isLoaded() && icon != null;
    }

    public boolean hasFailed() {
      return super.isLoaded() && icon == null;
    }

    public String getLabel() {
      return label;
    }
  }

  public ImageCellList(Orientation orientation, String emptyText, CellRenderer.CellLoader<T> loader) {
    super(orientation, emptyText, loader);
  }

  @Override
  protected CellRenderer<T> createCellRenderer(CellRenderer.CellLoader<T> loader) {
    return new ImageCellRenderer<T>(loader, getMaxCellSize());
  }

  protected abstract Dimension getMaxCellSize();
}
