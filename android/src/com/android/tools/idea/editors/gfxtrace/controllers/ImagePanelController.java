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
import com.intellij.ui.components.JBLoadingPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class ImagePanelController extends Controller {
  @NotNull private static final Logger LOG = Logger.getInstance(ImagePanelController.class);

  @NotNull protected final JPanel myPanel = new JPanel(new BorderLayout());
  @NotNull private final JBLoadingPanel myLoading;
  @NotNull private final ImagePanel myImagePanel = new ImagePanel();
  @NotNull private final AtomicInteger imageLoadCount = new AtomicInteger();
  @NotNull private ListenableFuture<?> request = Futures.immediateFuture(0);

  public ImagePanelController(@NotNull GfxTraceEditor editor) {
    super(editor);
    myLoading = new JBLoadingPanel(new BorderLayout(), myEditor.getProject());
    myLoading.add(myImagePanel, BorderLayout.CENTER);
    myPanel.add(myLoading, BorderLayout.CENTER);
  }

  protected void initToolbar(DefaultActionGroup group) {
    myImagePanel.addToolbarActions(group);
    myPanel.add(ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, false).getComponent(), BorderLayout.WEST);
  }

  protected void setImage(ListenableFuture<FetchedImage> imageFuture) {
    final int imageRequest = newImageRequest(imageFuture);
    myLoading.startLoading();

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

        EdtExecutor.INSTANCE.execute(new Runnable() {
          @Override
          public void run() {
            if (isCurrentImageRequest(imageRequest)) {
              myLoading.stopLoading();
            }
          }
        });
      }
    });
  }

  private void updateImage(final int imageRequest, FetchedImage fetchedImage) {
    final Image image = fetchedImage.icon.getImage();
    EdtExecutor.INSTANCE.execute(new Runnable() {
      @Override
      public void run() {
        // Back in the UI thread here
        if (isCurrentImageRequest(imageRequest)) {
          myLoading.stopLoading();
          myImagePanel.setImage(image);
        }
      }
    });
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
