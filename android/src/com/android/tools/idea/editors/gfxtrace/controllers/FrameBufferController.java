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
import com.android.tools.idea.editors.gfxtrace.service.path.Path;
import com.android.tools.idea.editors.gfxtrace.service.path.PathListener;
import com.intellij.execution.ui.layout.impl.JBRunnerTabs;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class FrameBufferController implements PathListener {
  @NotNull private final GfxTraceEditor myEditor;
  @NotNull private final JBRunnerTabs myBufferTabs;
  @NotNull private final JBScrollPane[] myBufferScrollPanes;
  @NotNull private AtomicLong myCurrentFetchAtomIndex = new AtomicLong();
  @NotNull private JBLoadingPanel[] myLoadingPanels = new JBLoadingPanel[BufferType.length];

  public FrameBufferController(@NotNull GfxTraceEditor editor,
                               @NotNull JBRunnerTabs bufferTabs,
                               @NotNull JBScrollPane colorScrollPane,
                               @NotNull JBScrollPane wireframePane,
                               @NotNull JBScrollPane depthScrollPane) {
    myEditor = editor;
    myEditor.addPathListener(this);
    myBufferTabs = bufferTabs;

    myBufferScrollPanes = new JBScrollPane[]{colorScrollPane, wireframePane, depthScrollPane};
    assert (myBufferScrollPanes.length == BufferType.length);

    for (int i = 0; i < myBufferScrollPanes.length; ++i) {
      myBufferScrollPanes[i].getVerticalScrollBar().setUnitIncrement(20);
      myBufferScrollPanes[i].getHorizontalScrollBar().setUnitIncrement(20);
      myBufferScrollPanes[i].setBorder(BorderFactory.createLineBorder(JBColor.border()));

      myLoadingPanels[i] = new JBLoadingPanel(new BorderLayout(), myEditor.getProject());
      myBufferScrollPanes[i].setViewportView(myLoadingPanels[i]);
    }

    // TODO: Add a way to pan the viewport with the keyboard.
  }

  public void setImageForId(final long atomIndex) {
    // TODO: Add toggle for between scaled and full size.

    ApplicationManager.getApplication().assertIsDispatchThread();

    if (myCurrentFetchAtomIndex.get() == atomIndex) {
      // Early out if the given atomIndex was already fetched or is in the process of being fetched.
      return;
    }

    clearCache();

    // Only color, wireframe, and depth are fetched at the moment, since the server hasn't implemented stencil buffers.
    final List<BufferType> bufferOrder =
      new ArrayList<BufferType>(Arrays.asList(BufferType.COLOR_BUFFER, BufferType.WIREFRAME_BUFFER, BufferType.DEPTH_BUFFER));

    for (JBLoadingPanel panel : myLoadingPanels) {
      if (!panel.isLoading()) {
        panel.startLoading();
      }
    }

    myCurrentFetchAtomIndex.set(atomIndex);

    // This needs to run in parallel to the main worker thread since it's slow and it doesn't affect the other controllers.
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        // Prioritize the currently selected tab in bufferOrder.
        if (myBufferTabs.getSelectedInfo() != null) {
          String tabName = myBufferTabs.getSelectedInfo().getText();
          for (BufferType buffer : BufferType.values()) {
            if (buffer.getName().equals(tabName)) {
              if (bufferOrder.remove(buffer)) {
                bufferOrder.add(0, buffer);
              }
              break;
            }
          }
        }

        for (BufferType buffer : bufferOrder) {
          if (atomIndex != myCurrentFetchAtomIndex.get()) {
            return;
          }

          //setIcons(atomIndex, fetchedImage.createImageIcon(), buffer);
        }

        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            if (atomIndex == myCurrentFetchAtomIndex.get()) {
              stopLoading();
            }
          }
        });
      }
    });
  }

  public void clear() {
    stopLoading();
    clearCache();
  }

  public void clearCache() {
    for (JBLoadingPanel panel : myLoadingPanels) {
      panel.getContentPanel().removeAll();
    }
    myCurrentFetchAtomIndex.set(-1);
  }

  private void stopLoading() {
    for (JBLoadingPanel panel : myLoadingPanels) {
      if (panel.isLoading()) {
        panel.stopLoading();
      }
    }
  }

  private void setIcons(final long closedAtomIndex, @NotNull final ImageIcon image, @NotNull final BufferType bufferType) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        if (myCurrentFetchAtomIndex.get() == closedAtomIndex) {
          int index = bufferType.ordinal();
          myLoadingPanels[index].add(new JBLabel(image));
          if (myLoadingPanels[index].isLoading()) {
            myLoadingPanels[index].stopLoading();
          }

          myBufferScrollPanes[index].repaint();
        }
      }
    });
  }

  @Override
  public void notifyPath(Path path) {
    // TODO: detect selections that should update the frame buffer
  }

  public enum BufferType {
    COLOR_BUFFER("Color"),
    WIREFRAME_BUFFER("Wirefame"),
    DEPTH_BUFFER("Depth");

    private static final int length = BufferType.values().length;
    @NotNull final private String myName;

    BufferType(@NotNull String name) {
      myName = name;
    }

    @NotNull
    public String getName() {
      return myName;
    }

    @Override
    @NotNull
    public String toString() {
      return getName();
    }
  }
}
