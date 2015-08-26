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
package com.android.tools.idea.editors.gfxtrace;

import com.intellij.execution.ui.layout.impl.JBRunnerTabs;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LoadingDecorator;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.JBColor;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.HierarchyBoundsAdapter;
import java.awt.event.HierarchyEvent;

public class GfxTraceViewPanel implements Disposable {
  @NotNull public static final String TOOLBAR_NAME = "GfxTraceViewPanelToolbar";

  @NotNull private LoadingDecorator myLoadingDecorator;

  @NotNull private JBPanel mainPanel = new JBPanel(new BorderLayout());
  @NotNull private ThreeComponentsSplitter myThreePanes = new ThreeComponentsSplitter(true);

  @NotNull private JBScrollPane myScrubberScrollPane = new JBScrollPane();
  @NotNull private JBList myScrubberList = new JBList();

  @NotNull private JBScrollPane myAtomScrollPane = new JBScrollPane();

  @NotNull private JBScrollPane myColorScrollPane = new JBScrollPane();
  @NotNull private JBScrollPane myWireframeScrollPane = new JBScrollPane();
  @NotNull private JBScrollPane myDepthScrollPane = new JBScrollPane();

  @NotNull private JBScrollPane myStateScrollPane = new JBScrollPane();

  @NotNull private JPanel myMemoryPanel = new JPanel();
  @NotNull private JBScrollPane myDocsScrollPane = new JBScrollPane();
  @NotNull private JPanel myDocsPanel = new JPanel();
  @NotNull private JTextPane myDocsTextPane = new JTextPane();
  @NotNull private JPanel myImagePanel = new JPanel();

  GfxTraceViewPanel() {
    mainPanel.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
    myLoadingDecorator = new LoadingDecorator(mainPanel, this, 0);
    myLoadingDecorator.setLoadingText("Initializing GFX Trace System");
    myLoadingDecorator.startLoading(false);
  }

  void setLoadingError(@NotNull String errorString) {
    myLoadingDecorator.setLoadingText(errorString);
  }

  void finalizeUi(@NotNull Project project, @NotNull AnAction deviceContextAction) {
    mainPanel.add(myThreePanes, BorderLayout.CENTER);
    myThreePanes.setDividerWidth(5);

    // Add the toolbar for the device selection.
    DefaultActionGroup group = new DefaultActionGroup(deviceContextAction);
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true);
    toolbar.getComponent().setName(TOOLBAR_NAME);
    mainPanel.add(toolbar.getComponent(), BorderLayout.NORTH);

    // Add the scrubber view to the top panel.
    myScrubberList.setLayoutOrientation(JList.HORIZONTAL_WRAP);
    myScrubberScrollPane = new JBScrollPane();
    myScrubberScrollPane.setViewportView(myScrubberList);
    JPanel scrubberPanel = new JPanel(new BorderLayout());
    scrubberPanel.add(myScrubberScrollPane, BorderLayout.CENTER);
    myThreePanes.setFirstComponent(scrubberPanel);

    // Configure the Atom tree container.
    JPanel atomTreePanel = new JPanel(new BorderLayout());
    atomTreePanel.add(myAtomScrollPane, BorderLayout.CENTER);

    // Configure the framebuffer views.
    final JBRunnerTabs bufferTabs = new JBRunnerTabs(project, ActionManager.getInstance(), IdeFocusManager.findInstance(), this);
    bufferTabs.setPaintBorder(0, 0, 0, 0).setTabSidePaintBorder(1).setPaintFocus(UIUtil.isUnderDarcula() || UIUtil.isUnderIntelliJLaF())
      .setAlwaysPaintSelectedTab(UIUtil.isUnderDarcula() || UIUtil.isUnderIntelliJLaF());

    JPanel colorPanel = new JPanel(new BorderLayout());
    colorPanel.add(myColorScrollPane, BorderLayout.CENTER);
    JPanel wireframePanel = new JPanel(new BorderLayout());
    wireframePanel.add(myWireframeScrollPane, BorderLayout.CENTER);
    JPanel depthPanel = new JPanel(new BorderLayout());
    depthPanel.add(myDepthScrollPane, BorderLayout.CENTER);
    bufferTabs.addTab(new TabInfo(colorPanel).setText("Color"));
    bufferTabs.addTab(new TabInfo(wireframePanel).setText("Wireframe"));
    bufferTabs.addTab(new TabInfo(depthPanel).setText("Depth"));
    bufferTabs.setBorder(new EmptyBorder(0, 2, 0, 0));

    // Put the buffer views in a panel so a border can be drawn around it.
    JPanel bufferWrapper = new JPanel(new BorderLayout());
    bufferWrapper.setBorder(BorderFactory.createLineBorder(JBColor.border()));
    bufferWrapper.add(bufferTabs, BorderLayout.CENTER);

    // Now add the atom tree and buffer views to the middle pane in the main pane.
    final JBSplitter middleSplitter = new JBSplitter(false);
    middleSplitter.setMinimumSize(new Dimension(100, 10));
    middleSplitter.setFirstComponent(atomTreePanel);
    middleSplitter.setSecondComponent(bufferWrapper);
    myThreePanes.setInnerComponent(middleSplitter);

    // Configure the miscellaneous tabs.
    JBRunnerTabs miscTabs = new JBRunnerTabs(project, ActionManager.getInstance(), IdeFocusManager.findInstance(), this);
    miscTabs.setPaintBorder(0, 0, 0, 0).setTabSidePaintBorder(1).setPaintFocus(UIUtil.isUnderDarcula() || UIUtil.isUnderIntelliJLaF())
      .setAlwaysPaintSelectedTab(UIUtil.isUnderDarcula() || UIUtil.isUnderIntelliJLaF());
    miscTabs.addTab(new TabInfo(myMemoryPanel).setText("Memory"));
    miscTabs.addTab(new TabInfo(myImagePanel).setText("Image"));
    miscTabs.addTab(new TabInfo(myDocsPanel).setText("Docs"));
    miscTabs.setBorder(new EmptyBorder(0, 2, 0, 0));
    getDocsScrollPane().setViewportView(getDocsTextPane());

    // More borders for miscellaneous tabs.
    JPanel miscPanel = new JPanel(new BorderLayout());
    miscPanel.setBorder(BorderFactory.createLineBorder(JBColor.border()));
    miscPanel.add(miscTabs, BorderLayout.CENTER);

    // Borders for the state tree as well.
    JPanel stateWrapper = new JPanel(new BorderLayout());
    stateWrapper.add(myStateScrollPane, BorderLayout.CENTER);

    // Configure the bottom splitter.
    JBSplitter bottomSplitter = new JBSplitter(false);
    bottomSplitter.setMinimumSize(new Dimension(100, 10));
    bottomSplitter.setFirstComponent(stateWrapper);
    bottomSplitter.setSecondComponent(miscPanel);
    myThreePanes.setLastComponent(bottomSplitter);

    // Make sure the bottom splitter honors minimum sizes.
    myThreePanes.setHonorComponentsMinimumSize(true);

    myThreePanes.addHierarchyBoundsListener(new HierarchyBoundsAdapter() {
      @Override
      public void ancestorResized(HierarchyEvent hierarchyEvent) {
        super.ancestorResized(hierarchyEvent);
        resize();
      }
    });

    myLoadingDecorator.stopLoading();
  }

  @NotNull
  public JPanel getRootComponent() {
    return mainPanel;
  }

  @NotNull
  public JBScrollPane getScrubberScrollPane() {
    return myScrubberScrollPane;
  }

  @NotNull
  public JBList getScrubberList() {
    return myScrubberList;
  }

  @NotNull
  JBScrollPane getAtomScrollPane() {
    return myAtomScrollPane;
  }

  @NotNull
  public JBScrollPane getColorScrollPane() {
    return myColorScrollPane;
  }

  @NotNull
  public JBScrollPane getWireframeScrollPane() {
    return myWireframeScrollPane;
  }

  @NotNull
  public JBScrollPane getDepthScrollPane() {
    return myDepthScrollPane;
  }

  @NotNull
  public JBScrollPane getStateScrollPane() {
    return myStateScrollPane;
  }

  @NotNull
  public JPanel getImagePanel() {
    return myImagePanel;
  }

  @NotNull
  public JPanel getMemoryPanel() {
    return myMemoryPanel;
  }

  @NotNull
  public JBScrollPane getDocsScrollPane() {
    return myDocsScrollPane;
  }

  @NotNull
  public JPanel getDocsPanel() {
    return myDocsPanel;
  }

  @NotNull
  public JTextPane getDocsTextPane() {
    return myDocsTextPane;
  }

  @Override
  public void dispose() {
  }

  void resize() {
    assert myThreePanes.getLastComponent() != null;
    JComponent bottomSplitter = myThreePanes.getLastComponent();

    assert myThreePanes.getInnerComponent() != null;
    JComponent middleSplitter = myThreePanes.getInnerComponent();

    assert myThreePanes.getFirstComponent() != null;
    int scrubberHeight = myThreePanes.getFirstComponent().getMinimumSize().height;
    if (myThreePanes.getFirstSize() < scrubberHeight) {
      int totalHeight = myThreePanes.getHeight();
      int residualHeightAfter = Math.max(0, totalHeight - scrubberHeight);

      myThreePanes.setFirstSize(scrubberHeight);
      int middleSize = middleSplitter.getPreferredSize().height;
      int bottomSize = bottomSplitter.getPreferredSize().height;
      if (bottomSize + middleSize > 0) {
        myThreePanes.setLastSize(residualHeightAfter * bottomSize / (bottomSize + middleSize)); // Split the middle and bottom panes evenly.
      }
      else {
        myThreePanes.setLastSize(residualHeightAfter / 2);
      }
    }
  }
}
