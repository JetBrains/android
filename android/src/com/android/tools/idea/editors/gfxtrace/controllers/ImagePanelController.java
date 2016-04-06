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
import com.android.tools.idea.editors.gfxtrace.service.ErrDataUnavailable;
import com.android.tools.idea.editors.gfxtrace.service.image.FetchedImage;
import com.android.tools.idea.editors.gfxtrace.service.image.MultiLevelImage;
import com.android.tools.idea.editors.gfxtrace.widgets.ImagePanel;
import com.android.tools.idea.editors.gfxtrace.widgets.LoadablePanel;
import com.android.tools.rpclib.futures.SingleInFlight;
import com.android.tools.rpclib.rpccore.Rpc;
import com.android.tools.rpclib.rpccore.RpcException;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.ExecutionException;

public abstract class ImagePanelController extends Controller {
  @NotNull private static final Logger LOG = Logger.getInstance(ImagePanelController.class);

  @NotNull protected final JPanel myPanel = new JPanel(new BorderLayout());
  @NotNull private final SingleInFlight myImageRequestController;
  @NotNull private final ImagePanel myImagePanel = new ImagePanel();

  public ImagePanelController(@NotNull GfxTraceEditor editor, String emptyText) {
    super(editor);
    myImagePanel.getEmptyText().setText(emptyText);
    LoadablePanel loading = new LoadablePanel(myImagePanel, LoadablePanel.Style.OPAQUE);
    myPanel.add(loading.getContentLayer(), BorderLayout.CENTER);
    myImageRequestController = new SingleInFlight(loading);
    myImagePanel.setImageRequestController(myImageRequestController);
  }

  protected void initToolbar(DefaultActionGroup group, boolean enableVerticalFlip) {
    myImagePanel.addToolbarActions(group, enableVerticalFlip);
    myPanel.add(ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, false).getComponent(), BorderLayout.WEST);
  }

  protected void setEmptyText(String text) {
    myImagePanel.clearImage();
    myImagePanel.getEmptyText().setText(text);
  }

  protected void setImage(ListenableFuture<FetchedImage> imageFuture) {
    if (imageFuture == null) {
      myImagePanel.setImage(null);
      return;
    }

    Rpc.listen(imageFuture, LOG, myImageRequestController, new UiErrorCallback<FetchedImage, MultiLevelImage, String>() {
      @Override
      protected ResultOrError<MultiLevelImage, String> onRpcThread(
          Rpc.Result<FetchedImage> result) throws RpcException, ExecutionException {
        try {
          return success(result.get());
        }
        catch (ErrDataUnavailable e) {
          return error(e.getMessage());
        }
      }

      @Override
      protected void onUiThreadSuccess(MultiLevelImage result) {
        myImagePanel.setImage(result);
      }

      @Override
      protected void onUiThreadError(String error) {
        setEmptyText(error);
      }
    });
  }
}
