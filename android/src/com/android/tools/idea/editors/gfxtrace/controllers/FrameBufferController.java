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
import com.android.tools.idea.editors.gfxtrace.service.image.ImageInfo;
import com.android.tools.idea.editors.gfxtrace.service.path.*;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.execution.ui.layout.impl.JBRunnerTabs;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class FrameBufferController extends Controller {
  public static JComponent createUI(GfxTraceEditor editor) {
    return new FrameBufferController(editor).myPanel;
  }

  private static final int MAX_SIZE = 0xffff;

  @NotNull private static final Logger LOG = Logger.getInstance(GfxTraceEditor.class);

  @NotNull private final JPanel myPanel = new JPanel(new BorderLayout());
  @NotNull private final BufferTab myColorTab = new BufferTab();
  @NotNull private final BufferTab myWireframeTab = new BufferTab();
  @NotNull private final BufferTab myDepthTab = new BufferTab();

  private final PathStore<DevicePath> myRenderDevice = new PathStore<DevicePath>();
  private final PathStore<AtomPath> myAtomPath = new PathStore<AtomPath>();

  private final class BufferTab {
    public final JPanel myPanel = new JPanel(new BorderLayout());
    public final JBScrollPane myScrollPane = new JBScrollPane();
    public JBLoadingPanel myLoading;
    public boolean myIsDepth = false;
    public RenderSettings mySettings = new RenderSettings();

    public BufferTab() {
      myPanel.add(myScrollPane, BorderLayout.CENTER);
      myLoading = new JBLoadingPanel(new BorderLayout(), myEditor.getProject());
      myScrollPane.getVerticalScrollBar().setUnitIncrement(20);
      myScrollPane.getHorizontalScrollBar().setUnitIncrement(20);
      myScrollPane.setBorder(BorderFactory.createLineBorder(JBColor.border()));
      myScrollPane.setViewportView(myLoading);
      mySettings.setMaxHeight(MAX_SIZE);
      mySettings.setMaxWidth(MAX_SIZE);
      mySettings.setWireframeMode(WireframeMode.noWireframe());
      // TODO: Add a way to pan the viewport with the keyboard.
    }
  }

  private FrameBufferController(@NotNull GfxTraceEditor editor) {
    super(editor);

    JBRunnerTabs bufferTabs = new JBRunnerTabs(editor.getProject(), ActionManager.getInstance(), IdeFocusManager.findInstance(), this);
    bufferTabs.setPaintBorder(0, 0, 0, 0).setTabSidePaintBorder(1).setPaintFocus(UIUtil.isUnderDarcula() || UIUtil.isUnderIntelliJLaF())
      .setAlwaysPaintSelectedTab(UIUtil.isUnderDarcula() || UIUtil.isUnderIntelliJLaF());

    bufferTabs.addTab(new TabInfo(myColorTab.myPanel).setText("Color"));
    bufferTabs.addTab(new TabInfo(myWireframeTab.myPanel).setText("Wireframe"));
    bufferTabs.addTab(new TabInfo(myDepthTab.myPanel).setText("Depth"));
    bufferTabs.setBorder(new EmptyBorder(0, 2, 0, 0));

    // Put the buffer views in a panel so a border can be drawn around it.
    myPanel.setBorder(BorderFactory.createLineBorder(JBColor.border()));
    myPanel.add(bufferTabs, BorderLayout.CENTER);

    myColorTab.mySettings.setWireframeMode(WireframeMode.wireframeOverlay());
    myWireframeTab.mySettings.setWireframeMode(WireframeMode.allWireframe());
    myDepthTab.myIsDepth = true;
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
      updateTab(myColorTab);
      updateTab(myWireframeTab);
      updateTab(myDepthTab);
    }
  }

  public void updateTab(final BufferTab tab) {
    if (!tab.myLoading.isLoading()) {
      tab.myLoading.startLoading();
    }
    ListenableFuture<ImageInfoPath> imagePathF;
    if (tab.myIsDepth) {
      imagePathF = myEditor.getClient().getFramebufferDepth(myRenderDevice.getPath(), myAtomPath.getPath());
    }
    else {
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
                tab.myPanel.repaint();
              }
            });
          }
        });
      }
    });
  }
}