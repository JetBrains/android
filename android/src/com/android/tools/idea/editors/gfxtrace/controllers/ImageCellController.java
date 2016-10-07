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

import com.android.tools.idea.editors.gfxtrace.GfxTraceEditor;
import com.android.tools.idea.editors.gfxtrace.UiErrorCallback;
import com.android.tools.idea.editors.gfxtrace.renderers.CellRenderer;
import com.android.tools.idea.editors.gfxtrace.renderers.ImageCellRenderer;
import com.android.tools.idea.editors.gfxtrace.service.ErrDataUnavailable;
import com.android.tools.idea.editors.gfxtrace.service.ServiceClient;
import com.android.tools.idea.editors.gfxtrace.service.image.FetchedImage;
import com.android.tools.idea.editors.gfxtrace.service.path.Path;
import com.android.tools.idea.editors.gfxtrace.widgets.*;
import com.android.tools.rpclib.rpccore.Rpc;
import com.android.tools.rpclib.rpccore.RpcException;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.ExecutionException;

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
    Rpc.listen(Futures.transform(FetchedImage.load(client, imagePath), new AsyncFunction<FetchedImage, BufferedImage>() {
      @Override
      public ListenableFuture<BufferedImage> apply(FetchedImage image) throws Exception {
        return getLevelToShow(image);
      }
    }), LOG, cell.controller, new UiErrorCallback<BufferedImage, BufferedImage, String>() {
      @Override
      protected ResultOrError<BufferedImage, String> onRpcThread(Rpc.Result<BufferedImage> result) throws RpcException, ExecutionException {
        try {
          return success(result.get());
        } catch (ErrDataUnavailable e) {
          return error(e.getMessage());
        }
      }

      @Override
      protected void onUiThreadSuccess(BufferedImage image) {
        cell.stopLoading(new ImageIcon(image));
        onLoad.run();
        myList.repaint();
      }

      @Override
      protected void onUiThreadError(String message) {
        cell.stopLoading(null);
        myList.repaint();
      }
    });
  }

  /** @return the level to show for the image (0 by default). Subclasses can override this to pick a diferent level. */
  protected ListenableFuture<BufferedImage> getLevelToShow(FetchedImage image) {
    return image.getLevel(0);
  }
}
