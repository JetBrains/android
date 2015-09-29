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
import com.android.tools.idea.editors.gfxtrace.actions.FramebufferTypeAction;
import com.android.tools.idea.editors.gfxtrace.actions.FramebufferWireframeAction;
import com.android.tools.idea.editors.gfxtrace.service.RenderSettings;
import com.android.tools.idea.editors.gfxtrace.service.WireframeMode;
import com.android.tools.idea.editors.gfxtrace.service.image.FetchedImage;
import com.android.tools.idea.editors.gfxtrace.service.path.AtomPath;
import com.android.tools.idea.editors.gfxtrace.service.path.DevicePath;
import com.android.tools.idea.editors.gfxtrace.service.path.ImageInfoPath;
import com.android.tools.idea.editors.gfxtrace.service.path.PathStore;
import com.android.tools.idea.editors.gfxtrace.widgets.ImagePanel;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.components.JBLoadingPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

public class FrameBufferController extends Controller {
  public static JComponent createUI(GfxTraceEditor editor) {
    return new FrameBufferController(editor).myPanel;
  }

  private static final int MAX_SIZE = 0xffff;

  public enum BufferType {
    Color,
    Depth
  }

  @NotNull private static final Logger LOG = Logger.getInstance(GfxTraceEditor.class);
  @NotNull private final JPanel myPanel = new JPanel(new BorderLayout());
  @NotNull private final PathStore<DevicePath> myRenderDevice = new PathStore<DevicePath>();
  @NotNull private final PathStore<AtomPath> myAtomPath = new PathStore<AtomPath>();
  @NotNull private final ImagePanel myImagePanel = new ImagePanel();
  @NotNull private final RenderSettings mySettings = new RenderSettings();
  @NotNull private JBLoadingPanel myLoading;
  @NotNull private BufferType myBufferType = BufferType.Color;

  private final AtomicInteger imageLoadCount = new AtomicInteger();
  private ListenableFuture<?> request = Futures.immediateFuture(0);

  public synchronized int newImageRequest(ListenableFuture<?> request) {
    this.request.cancel(true);
    this.request = request;
    return imageLoadCount.incrementAndGet();
  }

  public boolean isCurrentImageRequest(int request) {
    return imageLoadCount.get() == request;
  }

  private FrameBufferController(@NotNull GfxTraceEditor editor) {
    super(editor);

    myLoading = new JBLoadingPanel(new BorderLayout(), myEditor.getProject());
    myLoading.add(myImagePanel, BorderLayout.CENTER);

    mySettings.setMaxHeight(MAX_SIZE);
    mySettings.setMaxWidth(MAX_SIZE);
    mySettings.setWireframeMode(WireframeMode.noWireframe());
    myPanel.add(myLoading, BorderLayout.CENTER);

    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, getToolbarActions(), false);
    myPanel.add(toolbar.getComponent(), BorderLayout.WEST);
  }

  public ActionGroup getToolbarActions() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new FramebufferTypeAction(this, BufferType.Color, "Color", "Display the color framebuffer", AllIcons.Gutter.Colors));
    group.add(new FramebufferTypeAction(this, BufferType.Depth, "Depth", "Display the depth framebuffer", AllIcons.Gutter.OverridenMethod));
    group.add(new Separator());
    group.add(new FramebufferWireframeAction(this, WireframeMode.noWireframe(), "None", "Display the frambuffer without wireframing",
                                             AllIcons.Ide.Macro.Recording_1));
    group.add(new FramebufferWireframeAction(this, WireframeMode.wireframeOverlay(), "Overlay", "Wireframe the last draw call only",
                                             AllIcons.Gutter.Unique));
    group.add(new FramebufferWireframeAction(this, WireframeMode.allWireframe(), "All", "Draw the framebuffer with full wireframing",
                                             AllIcons.Graph.Grid));
    group.add(new Separator());
    myImagePanel.addToolbarActions(group);
    return group;
  }

  @NotNull
  public BufferType getBufferType() {
    return myBufferType;
  }

  public void setBufferType(@NotNull BufferType bufferType) {
    if (!myBufferType.equals(bufferType)) {
      myBufferType = bufferType;
      updateBuffer();
    }
  }

  @NotNull
  public WireframeMode getWireframeMode() {
    return mySettings.getWireframeMode();
  }

  public void setWireframeMode(@NotNull WireframeMode mode) {
    if (!mySettings.getWireframeMode().equals(mode)) {
      mySettings.setWireframeMode(mode);
      updateBuffer();
    }
  }

  @Override
  public void notifyPath(PathEvent event) {
    boolean updateTabs = false;
    if (event.path instanceof DevicePath) {
      updateTabs |= myRenderDevice.update((DevicePath)event.path);
    }
    if (event.path instanceof AtomPath) {
      updateTabs |= myAtomPath.update((AtomPath)event.path);
    }
    if (updateTabs && myRenderDevice.getPath() != null && myAtomPath.getPath() != null) {
      // TODO: maybe do the selected tab first, but it's probably not much of a win
      updateBuffer();
    }
  }

  public void updateBuffer() {
    final ListenableFuture<FetchedImage> imageFuture = loadImage();
    final int imageRequest = newImageRequest(imageFuture);

    myLoading.startLoading();

    Futures.addCallback(imageFuture, new FutureCallback<FetchedImage>() {
      @Override
      public void onSuccess(@Nullable FetchedImage result) {
        updateBuffer(imageRequest, result);
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

  private ListenableFuture<FetchedImage> loadImage() {
    return FetchedImage.load(myEditor.getClient(), getImageInfoPath());
  }

  private ListenableFuture<ImageInfoPath> getImageInfoPath() {
    switch (myBufferType) {
      case Color:
        return myEditor.getClient().getFramebufferColor(myRenderDevice.getPath(), myAtomPath.getPath(), mySettings);
      case Depth:
        return myEditor.getClient().getFramebufferDepth(myRenderDevice.getPath(), myAtomPath.getPath());
      default:
        return null;
    }
  }

  private void updateBuffer(final int imageRequest, FetchedImage fetchedImage) {
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
}
