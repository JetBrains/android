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
import com.android.tools.rpclib.rpc.RenderSettings;
import com.intellij.execution.ui.layout.impl.JBRunnerTabs;
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
  @NotNull private final JBRunnerTabs myBufferTabs;
  @NotNull private final JBScrollPane myColorScrollPane;
  @NotNull private final JBScrollPane myDepthScrollPane;
  @NotNull private final JBScrollPane myStencilScrollPane;
  @NotNull private AtomicLong myCurrentFetchAtomId = new AtomicLong();
  @Nullable private ImageFetcher myImageFetcher;
  private boolean myIsWireframeMode;

  @NotNull private ImageIcon[] myIconCache = new ImageIcon[BufferType.length];

  public FrameBufferController(@NotNull GfxTraceEditor editor,
                               @NotNull JBRunnerTabs bufferTabs,
                               @NotNull JBScrollPane colorScrollPane,
                               @NotNull JToggleButton wireframeButton,
                               @NotNull JBScrollPane depthScrollPane,
                               @NotNull JBScrollPane stencilScrollPane) {
    myEditor = editor;
    myBufferTabs = bufferTabs;

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
          refreshIcons();
        }
        else if (itemEvent.getStateChange() == ItemEvent.DESELECTED) {
          myIsWireframeMode = false;
          refreshIcons();
        }
      }
    });
  }

  @Nullable
  private static FetchedImage fetchImage(long atomId,
                                         @NotNull ImageFetcher imageFetcher,
                                         @NotNull BufferType instance,
                                         @Nullable RenderSettings renderSettings,
                                         boolean isWireframeMode) {
    assert (instance == BufferType.DEPTH_BUFFER || renderSettings != null);
    if (renderSettings != null) {
      renderSettings.setWireframe(isWireframeMode);
    }

    ImageFetcher.ImageFetchHandle imageFetchHandle =
      (instance == BufferType.DEPTH_BUFFER) ? imageFetcher.queueDepthImage(atomId) : imageFetcher.queueColorImage(atomId, renderSettings);

    if (imageFetchHandle == null) {
      return null;
    }

    return imageFetcher.resolveImage(imageFetchHandle);
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
    assert (myEditor.getCaptureId() != null);
    assert (myImageFetcher != null);

    if (myCurrentFetchAtomId.get() == atomId) {
      // Early out if the given atomId was already fetched or is in the process of being fetched.
      return;
    }

    clearCache();

    myCurrentFetchAtomId.set(atomId);
    final ImageFetcher imageFetcher = myImageFetcher;
    final boolean wireframeFirst = myIsWireframeMode;

    // This needs to run in parallel to the main worker thread since it's slow and it doesn't affect the other controllers.
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        ImageIcon[] iconCache = new ImageIcon[BufferType.length];
        RenderSettings renderSettings = new RenderSettings();
        renderSettings.setMaxWidth(4096);
        renderSettings.setMaxHeight(4096);

        BufferType[] bufferOrder = new BufferType[3];

        if (myBufferTabs.getSelectedInfo() != null &&
            BufferTabNames.DEPTH_BUFFER.getValue().equals(myBufferTabs.getSelectedInfo().getText())) {
          bufferOrder[0] = BufferType.DEPTH_BUFFER;
          bufferOrder[1] = wireframeFirst ? BufferType.COLOR_BUFFER_WIREFRAME : BufferType.COLOR_BUFFER;
          bufferOrder[2] = wireframeFirst ? BufferType.COLOR_BUFFER : BufferType.COLOR_BUFFER_WIREFRAME;
        }
        else {
          bufferOrder[0] = wireframeFirst ? BufferType.COLOR_BUFFER_WIREFRAME : BufferType.COLOR_BUFFER;
          bufferOrder[1] = wireframeFirst ? BufferType.COLOR_BUFFER : BufferType.COLOR_BUFFER_WIREFRAME;
          bufferOrder[2] = BufferType.DEPTH_BUFFER;
        }

        for (BufferType bufferType : bufferOrder) {
          if (atomId != myCurrentFetchAtomId.get()) {
            return;
          }

          FetchedImage fetchedImage =
            fetchImage(atomId, imageFetcher, bufferType, renderSettings, bufferType == BufferType.COLOR_BUFFER_WIREFRAME);
          if (fetchedImage == null) {
            return;
          }

          iconCache[bufferType.ordinal()] = fetchedImage.createImageIcon();
          setIcons(atomId, iconCache);
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
    clearIconCache();
  }

  public void clearIconCache() {
    for (int i = 0; i < BufferType.length; ++i) {
      myIconCache[i] = null;
    }
  }

  private void setIcons(final long closedAtomId, @NotNull final ImageIcon[] iconCache) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        if (myCurrentFetchAtomId.get() == closedAtomId) {
          System.arraycopy(iconCache, 0, myIconCache, 0, BufferType.length);
          refreshIcons();
        }
      }
    });
  }

  private void refreshIcons() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    clearViewCaches();

    ImageIcon colorImageToSet =
      myIsWireframeMode ? myIconCache[BufferType.COLOR_BUFFER_WIREFRAME.ordinal()] : myIconCache[BufferType.COLOR_BUFFER.ordinal()];
    if (colorImageToSet != null) {
      myColorScrollPane.setViewportView(new JBLabel(colorImageToSet));
    }

    if (myIconCache[BufferType.DEPTH_BUFFER.ordinal()] != null) {
      myDepthScrollPane.setViewportView(new JBLabel(myIconCache[BufferType.DEPTH_BUFFER.ordinal()]));
    }

    if (myIconCache[BufferType.STENCIL_BUFFER.ordinal()] != null) {
      myStencilScrollPane.setViewportView(new JBLabel(myIconCache[BufferType.STENCIL_BUFFER.ordinal()]));
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

  public enum BufferTabNames {
    COLOR_BUFFER("Color"),
    DEPTH_BUFFER("Depth"),
    STENCIL_BUFFER("Stencil");

    @NotNull final private String myValue;

    BufferTabNames(@NotNull String value) {
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

  private enum BufferType {
    COLOR_BUFFER,
    COLOR_BUFFER_WIREFRAME,
    DEPTH_BUFFER,
    STENCIL_BUFFER;

    private static final int length = BufferType.values().length;
  }
}
