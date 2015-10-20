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
package com.android.tools.idea.editors.gfxtrace.controllers;

import com.android.tools.idea.ddms.EdtExecutor;
import com.android.tools.idea.editors.gfxtrace.GfxTraceEditor;
import com.android.tools.idea.editors.gfxtrace.LoadingCallback;
import com.android.tools.idea.editors.gfxtrace.renderers.CellRenderer;
import com.android.tools.idea.editors.gfxtrace.renderers.ImageCellRenderer;
import com.android.tools.idea.editors.gfxtrace.service.ServiceClient;
import com.android.tools.idea.editors.gfxtrace.service.image.FetchedImage;
import com.android.tools.idea.editors.gfxtrace.service.path.Path;
import com.android.tools.idea.editors.gfxtrace.widgets.*;
import com.google.common.util.concurrent.Futures;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.StatusText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseWheelEvent;
import java.util.List;

public abstract class ImageCellController<T extends ImageCellList.Data> extends Controller
    implements CellList.SelectionListener<T>, CellRenderer.CellLoader<T> {
  @NotNull private static final Logger LOG = Logger.getInstance(ImageCellController.class);
  @NotNull protected CellWidget<T, ?> myList;

  public ImageCellController(@NotNull GfxTraceEditor editor) {
    super(editor);
  }

  protected ImageCellController<T> usingListWidget(
    @NotNull CellList.Orientation orientation, String emptyText, final Dimension maxCellSize) {
    myList = new ImageCellList<T>(orientation, emptyText, this) {
      @Override
      protected Dimension getMaxCellSize() {
        return maxCellSize;
      }
    };
    myList.addSelectionListener(this);
    return this;
  }

  protected ImageCellController<T> usingComboBoxWidget(final Dimension imageSize) {
    myList = new CellComboBox<T>(this) {
      @Override
      protected CellRenderer<T> createCellRenderer(CellRenderer.CellLoader<T> loader) {
        return new ImageCellRenderer<T>(loader, imageSize) {
          {
            setMinimumIconSize(imageSize);
          }

          @Override
          protected Repaintable getRepaintable(JList list) {
            return Repaintables.forComponents(list, myComponent);
          }

          @Override
          public Dimension getInitialCellSize() {
            return
              new Dimension(imageSize.width + 2 * ImageCellRenderer.BORDER_SIZE, imageSize.height + 2 * ImageCellRenderer.BORDER_SIZE);
          }
        };
      }
    };
    myList.addSelectionListener(this);
    return this;
  }


  protected void loadCellImage(final T cell, final ServiceClient client, final Path imagePath, final Runnable onLoad) {
    cell.startLoading();
    Futures.addCallback(FetchedImage.load(client, imagePath), new LoadingCallback<FetchedImage>(LOG, cell) {
      @Override
      public void onSuccess(final FetchedImage fetchedImage) {
        EdtExecutor.INSTANCE.execute(new Runnable() {
          @Override
          public void run() {
            // Back in the UI thread here
            cell.icon = fetchedImage.icon;
            cell.stopLoading();
            onLoad.run();
            myList.repaint();
          }
        });
      }
    });
  }
}
