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
import com.android.tools.idea.editors.gfxtrace.service.RenderSettings;
import com.android.tools.idea.editors.gfxtrace.service.WireframeMode;
import com.android.tools.idea.editors.gfxtrace.service.atom.AtomGroup;
import com.android.tools.idea.editors.gfxtrace.service.atom.AtomList;
import com.android.tools.idea.editors.gfxtrace.service.image.ImageInfo;
import com.android.tools.idea.editors.gfxtrace.service.path.*;
import com.android.tools.rpclib.binary.BinaryObject;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.execution.ui.layout.impl.JBRunnerTabs;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreeNode;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class FrameBufferController implements PathListener {
  private static final int MAX_SIZE = 0xffff;

  @NotNull private static final Logger LOG = Logger.getInstance(GfxTraceEditor.class);
  @NotNull private final GfxTraceEditor myEditor;
  @NotNull private final JBRunnerTabs myBufferTabs;

  @NotNull private final BufferTab colorTab = new BufferTab();
  @NotNull private final BufferTab wireframeTab = new BufferTab();
  @NotNull private final BufferTab depthTab = new BufferTab();

  private final PathStore<DevicePath> myRenderDevice = new PathStore<DevicePath>();
  private final PathStore<AtomPath> myAtomPath = new PathStore<AtomPath>();

  private final class BufferTab {
    public JBScrollPane myPane;
    public JBLoadingPanel myLoading;
    public boolean myIsDepth = false;
    public RenderSettings mySettings = new RenderSettings();
  }

  public FrameBufferController(@NotNull GfxTraceEditor editor,
                               @NotNull JBRunnerTabs bufferTabs,
                               @NotNull JBScrollPane colorScrollPane,
                               @NotNull JBScrollPane wireframePane,
                               @NotNull JBScrollPane depthScrollPane) {
    myEditor = editor;
    myEditor.addPathListener(this);
    myBufferTabs = bufferTabs;

    initTab(colorTab, colorScrollPane);
    initTab(wireframeTab, wireframePane);
    wireframeTab.mySettings.setWireframeMode(WireframeMode.AllWireframe);
    initTab(depthTab, depthScrollPane);
    depthTab.myIsDepth = true;
  }

  private void initTab(BufferTab tab, JBScrollPane pane) {
    tab.myLoading = new JBLoadingPanel(new BorderLayout(), myEditor.getProject());
    tab.myPane = pane;
    tab.myPane.getVerticalScrollBar().setUnitIncrement(20);
    tab.myPane.getHorizontalScrollBar().setUnitIncrement(20);
    tab.myPane.setBorder(BorderFactory.createLineBorder(JBColor.border()));
    tab.myPane.setViewportView(tab.myLoading);

    tab.mySettings.setMaxHeight(MAX_SIZE);
    tab.mySettings.setMaxWidth(MAX_SIZE);
    tab.mySettings.setWireframeMode(WireframeMode.NoWireframe);
    // TODO: Add a way to pan the viewport with the keyboard.
  }

  @Override
  public void notifyPath(Path path) {
    boolean updateTabs = false;
    if (path instanceof DevicePath) {
      updateTabs |= myRenderDevice.update((DevicePath)path);
    }
    if (path instanceof AtomPath) {
      updateTabs |= myAtomPath.update((AtomPath)path);
    }
    if (updateTabs && myRenderDevice.isValid() && myAtomPath.isValid()) {
      // TODO: maybe do the selected tab first, but it's probably not much of a win
      updateTab(colorTab);
      updateTab(wireframeTab);
      updateTab(depthTab);
    }
  }

  public void updateTab(final BufferTab tab) {
    if (!tab.myLoading.isLoading()) {
      tab.myLoading.startLoading();
    }
    ListenableFuture<ImageInfoPath> imagePathF;
    if (tab.myIsDepth) {
      imagePathF = myEditor.getClient().getFramebufferDepth(myRenderDevice.getPath(), myAtomPath.getPath());
    } else {
      imagePathF = myEditor.getClient().getFramebufferColor(myRenderDevice.getPath(), myAtomPath.getPath(), tab.mySettings);
    }
    Futures.addCallback(imagePathF, new LoadingCallback<ImageInfoPath>(LOG, tab.myLoading) {
      @Override
      public void onSuccess(@Nullable final ImageInfoPath imagePath) {
        updateTab(tab, imagePath);
      }
    });
  }

  private void updateTab(final BufferTab tab, final ImageInfoPath imageInfoPath) {
    Futures.addCallback(myEditor.getClient().get(imageInfoPath), new LoadingCallback<ImageInfo>(LOG, tab.myLoading) {
      @Override
      public void onSuccess(@Nullable final ImageInfo imageInfo) {
        Futures.addCallback(myEditor.getClient().get(imageInfo.getData()), new LoadingCallback<byte[]>(LOG, tab.myLoading) {
          @Override
          public void onSuccess(@Nullable final byte[] data) {
            final FetchedImage fetchedImage = new FetchedImage(imageInfo, data);
            final ImageIcon image = fetchedImage.createImageIcon();
            EdtExecutor.INSTANCE.execute(new Runnable() {
              @Override
              public void run() {
                // Back in the UI thread here
                tab.myLoading.stopLoading();
                tab.myLoading.getContentPanel().removeAll();
                tab.myLoading.add(new JBLabel(image));
                tab.myPane.repaint();
              }
            });
          }
        });
      }
    });
  }
}