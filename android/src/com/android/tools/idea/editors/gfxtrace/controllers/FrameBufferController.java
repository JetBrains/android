/*
 * Copyright (C) 2014 The Android Open Source Project
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
import com.android.tools.rpclib.rpc.RenderSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.concurrent.atomic.AtomicLong;

public class FrameBufferController implements GfxController {
  @NotNull private final GfxTraceEditor myEditor;
  @NotNull private final JBScrollPane myColorScrollPane;
  @NotNull private final JBScrollPane myDepthScrollPane;
  @NotNull private final JBScrollPane myStencilScrollPane;
  @NotNull private AtomicLong myCurrentFetchAtomId = new AtomicLong();
  @Nullable private ImageFetcher myImageFetcher;
  private boolean myIsWireframeMode;

  @Nullable private ImageIcon myCachedColor;
  @Nullable private ImageIcon myCachedWireframe;
  @Nullable private ImageIcon myCachedDepth;

  public FrameBufferController(@NotNull GfxTraceEditor editor,
                               @NotNull JBScrollPane colorScrollPane,
                               @NotNull JToggleButton wireframeButton,
                               @NotNull JBScrollPane depthScrollPane,
                               @NotNull JBScrollPane stencilScrollPane) {
    myEditor = editor;

    myColorScrollPane = colorScrollPane;
    myDepthScrollPane = depthScrollPane;
    myStencilScrollPane = stencilScrollPane;

    // TODO: Add a way to pan the viewport with the keyboard.

    myColorScrollPane.getVerticalScrollBar().setUnitIncrement(20);
    myColorScrollPane.getHorizontalScrollBar().setUnitIncrement(20);
    myDepthScrollPane.getVerticalScrollBar().setUnitIncrement(20);
    myDepthScrollPane.getHorizontalScrollBar().setUnitIncrement(20);
    myStencilScrollPane.getVerticalScrollBar().setUnitIncrement(20);
    myStencilScrollPane.getHorizontalScrollBar().setUnitIncrement(20);

    wireframeButton.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent itemEvent) {
        if (itemEvent.getStateChange() == ItemEvent.SELECTED) {
          myIsWireframeMode = true;
          updateViewports(myCachedColor, myCachedWireframe, myCachedDepth, null);
        }
        else if (itemEvent.getStateChange() == ItemEvent.DESELECTED) {
          myIsWireframeMode = false;
          updateViewports(myCachedColor, myCachedWireframe, myCachedDepth, null);
        }
      }
    });
  }

  @Override
  public void commitData(@NotNull GfxContextChangeState state) {
    myImageFetcher = new ImageFetcher(myEditor.getClient());
    assert (myEditor.getCaptureId() != null);
    if (myEditor.getDeviceId() == null || myEditor.getContext() == null) {
      // If there is no device selected, don't do anything.
      return;
    }
    myImageFetcher.prepareFetch(myEditor.getDeviceId(), myEditor.getCaptureId(), myEditor.getContext());
  }

  public void setImageForId(final long atomId) {
    // TODO: Add toggle for between scaled and full size.

    ApplicationManager.getApplication().assertIsDispatchThread();
    assert (myImageFetcher != null);
    clearCache();

    myCurrentFetchAtomId.set(atomId);
    final ImageFetcher imageFetcher = myImageFetcher;
    final boolean wireframeFirst = myIsWireframeMode;

    // This needs to run in parallel to the main worker thread since it's slow and it doesn't affect the other controllers.
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        RenderSettings colorRenderSettings = new RenderSettings();
        colorRenderSettings.setMaxWidth(4096);
        colorRenderSettings.setMaxHeight(4096);

        // The first image to be queued should depend on the wireframe setting. This could result in faster rendering.
        colorRenderSettings.setWireframe(wireframeFirst);
        ImageFetcher.ImageFetchHandle colorHandle0 = imageFetcher.queueColorImage(atomId, colorRenderSettings);
        if (myCurrentFetchAtomId.get() != atomId || colorHandle0 == null) {
          // The server may take a (really) long time to respond. So we should cancel if the user has selected a different image.
          return;
        }

        colorRenderSettings.setWireframe(!wireframeFirst);
        ImageFetcher.ImageFetchHandle colorHandle1 = imageFetcher.queueColorImage(atomId, colorRenderSettings);
        if (myCurrentFetchAtomId.get() != atomId || colorHandle1 == null) {
          return;
        }

        ImageFetcher.ImageFetchHandle depthHandle = imageFetcher.queueDepthImage(atomId);
        if (myCurrentFetchAtomId.get() != atomId || depthHandle == null) {
          return;
        }

        ImageIcon color = null;
        ImageIcon wireframe = null;
        ImageIcon depth = null;

        // TODO: Add logic to fetch the currently showing pane's image first.
        FetchedImage colorImage = imageFetcher.resolveImage(wireframeFirst ? colorHandle1 : colorHandle0);
        if (myCurrentFetchAtomId.get() != atomId) {
          return;
        }
        if (colorImage != null) {
          color = colorImage.createImageIcon();
          //noinspection ConstantConditions
          setIcons(atomId, color, wireframe, depth);
        }

        FetchedImage wireframeImage = imageFetcher.resolveImage(wireframeFirst ? colorHandle0 : colorHandle1);
        if (myCurrentFetchAtomId.get() != atomId) {
          return;
        }
        if (wireframeImage != null) {
          wireframe = wireframeImage.createImageIcon();
          //noinspection ConstantConditions
          setIcons(atomId, color, wireframe, depth);
        }

        FetchedImage depthImage = imageFetcher.resolveImage(depthHandle);
        if (myCurrentFetchAtomId.get() != atomId) {
          return;
        }
        if (depthImage != null) {
          depth = depthImage.createImageIcon();
          setIcons(atomId, color, wireframe, depth);
        }
      }
    });
  }

  @Override
  public void clear() {
    myImageFetcher = null;
    clearCache();
  }

  @Override
  public void clearCache() {
    clearViewCaches();
    myCurrentFetchAtomId.set(-1);
  }

  private void setIcons(final long closedAtomId,
                        @Nullable final ImageIcon color,
                        @Nullable final ImageIcon wireframe,
                        @Nullable final ImageIcon depth) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        if (myCurrentFetchAtomId.get() == closedAtomId) {
          myCachedColor = color;
          myCachedWireframe = wireframe;
          myCachedDepth = depth;
          updateViewports(color, wireframe, depth, null);
        }
      }
    });
  }

  private void updateViewports(@Nullable ImageIcon color,
                               @Nullable ImageIcon wireframe,
                               @Nullable ImageIcon depth,
                               @Nullable ImageIcon stencil) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    clearViewCaches();

    ImageIcon colorImageToSet = myIsWireframeMode ? wireframe : color;
    if (colorImageToSet != null) {
      myColorScrollPane.setViewportView(new JBLabel(colorImageToSet));
    }

    if (depth != null) {
      myDepthScrollPane.setViewportView(new JBLabel(depth));
    }

    if (stencil != null) {
      myStencilScrollPane.setViewportView(new JBLabel(stencil));
    }

    repaint();
  }

  private void clearViewCaches() {
    myColorScrollPane.setViewportView(null);
    myDepthScrollPane.setViewportView(null);
    myStencilScrollPane.setViewportView(null);
  }

  private void repaint() {
    myColorScrollPane.repaint();
    myDepthScrollPane.repaint();
    myStencilScrollPane.repaint();
  }

  public enum BufferNames {
    COLOR_BUFFER("Color"),
    DEPTH_BUFFER("Depth"),
    STENCIL_BUFFER("Stencil");

    @NotNull final private String myValue;

    BufferNames(@NotNull String value) {
      myValue = value;
    }

    @NotNull
    public String getValue() {
      return myValue;
    }

    @Override
    @NotNull
    public String toString() {
      return getValue();
    }
  }
}
