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
import com.android.tools.idea.editors.gfxtrace.service.image.FetchedImage;
import com.android.tools.idea.editors.gfxtrace.widgets.ImagePanel;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.LoadingDecorator;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class ImagePanelController extends Controller {
  @NotNull private static final Logger LOG = Logger.getInstance(ImagePanelController.class);

  @NotNull protected final JPanel myPanel = new JPanel(new BorderLayout());
  @NotNull private final LoadingDecorator myLoading;
  @NotNull private final ImagePanel myImagePanel = new ImagePanel();
  @NotNull private final AtomicInteger imageLoadCount = new AtomicInteger();
  @NotNull private ListenableFuture<?> request = Futures.immediateFuture(0);

  public ImagePanelController(@NotNull GfxTraceEditor editor, String emptyText) {
    super(editor);
    myImagePanel.getEmptyText().setText(emptyText);
    myLoading =  new LoadingDecorator(myImagePanel, myEditor.getProject(), -1) {
      @Override
      protected NonOpaquePanel customizeLoadingLayer(JPanel parent, JLabel text, AsyncProcessIcon icon) {
        NonOpaquePanel result = super.customizeLoadingLayer(parent, text, icon);
        result.setOpaque(true); // I regret nothing!
        result.setBackground(UIUtil.getPanelBackground());
        result.setBorder(JBUI.Borders.merge(JBUI.Borders.customLine(new JBColor(0, 0xffffff), 1), JBUI.Borders.empty(5), false));
        return result;
      }
    };

    myPanel.add(myLoading.getComponent(), BorderLayout.CENTER);
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

    final int imageRequest = newImageRequest(imageFuture);
    myLoading.startLoading(false);

    Futures.addCallback(imageFuture, new FutureCallback<FetchedImage>() {
      @Override
      public void onSuccess(@Nullable FetchedImage result) {
        updateImage(imageRequest, result);
      }

      @Override
      public void onFailure(Throwable t) {
        if (!(t instanceof CancellationException)) {
          LOG.error(t);
        }
        if (isCurrentImageRequest(imageRequest)) {
          myLoading.stopLoading();
        }
      }
    }, EdtExecutor.INSTANCE);
  }

  private void updateImage(final int imageRequest, FetchedImage fetchedImage) {
    if (isCurrentImageRequest(imageRequest)) {
      myLoading.stopLoading();
      myImagePanel.setImage(fetchedImage.icon.getImage());
    }
  }

  private synchronized int newImageRequest(ListenableFuture<?> request) {
    this.request.cancel(true);
    this.request = request;
    return imageLoadCount.incrementAndGet();
  }

  private boolean isCurrentImageRequest(int request) {
    return imageLoadCount.get() == request;
  }
}
