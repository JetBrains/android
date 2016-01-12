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
import com.android.tools.idea.editors.gfxtrace.LoadingDecoratorWrapper;
import com.android.tools.idea.editors.gfxtrace.service.image.FetchedImage;
import com.android.tools.idea.editors.gfxtrace.widgets.ImagePanel;
import com.android.tools.rpclib.futures.SingleInFlight;
import com.android.tools.rpclib.rpccore.Rpc;
import com.android.tools.rpclib.rpccore.RpcException;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.LoadingDecorator;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.ExecutionException;

public abstract class ImagePanelController extends Controller {
  @NotNull private static final Logger LOG = Logger.getInstance(ImagePanelController.class);

  @NotNull protected final JPanel myPanel = new JPanel(new BorderLayout());
  @NotNull private final SingleInFlight myImageRequestController;
  @NotNull private final ImagePanel myImagePanel = new ImagePanel();

  public ImagePanelController(@NotNull GfxTraceEditor editor, String emptyText) {
    super(editor);
    myImagePanel.getEmptyText().setText(emptyText);
    LoadingDecorator loadingDecorator = new LoadingDecorator(myImagePanel, myEditor.getProject(), -1) {
      @Override
      protected NonOpaquePanel customizeLoadingLayer(JPanel parent, JLabel text, AsyncProcessIcon icon) {
        NonOpaquePanel result = super.customizeLoadingLayer(parent, text, icon);
        result.setOpaque(true); // I regret nothing!
        result.setBackground(UIUtil.getPanelBackground());
        result.setBorder(JBUI.Borders.merge(JBUI.Borders.customLine(new JBColor(0, 0xffffff), 1), JBUI.Borders.empty(5), false));
        return result;
      }
    };

    myPanel.add(loadingDecorator.getComponent(), BorderLayout.CENTER);
    myImageRequestController = new SingleInFlight(new LoadingDecoratorWrapper(loadingDecorator));
  }

  protected void initToolbar(DefaultActionGroup group, boolean enableVerticalFlip) {
    myImagePanel.addToolbarActions(group, enableVerticalFlip);
    myPanel.add(ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, false).getComponent(), BorderLayout.WEST);
  }

  protected void setEmptyText(String text) {
    myImagePanel.getEmptyText().setText(text);
  }

  protected void setImage(ListenableFuture<FetchedImage> imageFuture) {
    if (imageFuture == null) {
      myImagePanel.setImage(null);
      return;
    }

    Rpc.listen(imageFuture, EdtExecutor.INSTANCE, LOG, myImageRequestController,
               new Rpc.Callback<FetchedImage>() {
      @Override
      public void onFinish(Rpc.Result<FetchedImage> result) throws RpcException, ExecutionException {
        // TODO: try{ result.get() } catch{ ErrDataUnavailable e }...
        FetchedImage fetchedImage = result.get();
        myImagePanel.setImage(fetchedImage.image);
      }
    });
  }
}
