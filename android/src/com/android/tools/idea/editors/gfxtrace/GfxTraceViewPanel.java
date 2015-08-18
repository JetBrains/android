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

import com.android.tools.idea.editors.gfxtrace.controllers.FrameBufferController;
import com.intellij.execution.ui.layout.impl.JBRunnerTabs;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.JBColor;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.HierarchyBoundsAdapter;
import java.awt.event.HierarchyEvent;

public class GfxTraceViewPanel implements Disposable {
  private JPanel mainPanel;
  private JBPanel myColorBuffer;
  private JBPanel myDepthBuffer;
  private JPanel myMemoryPanel;
  private JPanel myImagePanel;
  private JBList myScrubberList;
  private JBScrollPane myStateScrollPane;
  private JBScrollPane myScrubberScrollPane;
  private ThreeComponentsSplitter myThreePanes;
  private JPanel myHiddenComponents;
  private JBScrollPane myAtomScrollPane;
  private ComboBox myDeviceList;
  private ComboBox myCapturesList;
  private JBPanel myTopPanel;
  private ComboBox myGfxContextList;
  private JBScrollPane myColorScrollPane;
  private JBScrollPane myWireframeScrollPane;
  private JBScrollPane myDepthScrollPane;
  private JBScrollPane myStencilScrollPane;
  private JBPanel myDocsPanel;
  private JBScrollPane myDocsScrollPane;
  private JTextPane myDocsPane;
  private JBPanel myWireframeBuffer;
  private JBRunnerTabs myBufferTabs;

  @NotNull
  public JPanel getRootComponent() {
    return mainPanel;
  }

  public JPanel getImagePanel() {
    return myImagePanel;
  }

  public JPanel getMemoryPanel() {
    return myMemoryPanel;
  }

  public JBScrollPane getAtomScrollPane() {
    return myAtomScrollPane;
  }

  public JBList getScrubberList() {
    return myScrubberList;
  }

  public JBScrollPane getStateScrollPane() {
    return myStateScrollPane;
  }

  public ThreeComponentsSplitter getThreePanes() {
    return myThreePanes;
  }

  public JPanel getHiddenComponents() {
    return myHiddenComponents;
  }

  private void setHiddenComponents(@Nullable JPanel hiddenComponents) {
    //noinspection BoundFieldAssignment
    myHiddenComponents = hiddenComponents;
  }

  public JBRunnerTabs getBufferTabs() {
    return myBufferTabs;
  }

  public void setBufferTabs(@NotNull JBRunnerTabs bufferTabs) {
    myBufferTabs = bufferTabs;
  }

  public JBScrollPane getScrubberScrollPane() {
    return myScrubberScrollPane;
  }

  public ComboBox getDeviceList() {
    return myDeviceList;
  }

  public ComboBox getCapturesList() {
    return myCapturesList;
  }

  public JBPanel getTopPanel() {
    return myTopPanel;
  }

  public ComboBox getGfxContextList() {
    return myGfxContextList;
  }

  public JBScrollPane getColorScrollPane() {
    return myColorScrollPane;
  }

  public JBScrollPane getWireframeScrollPane() {
    return myWireframeScrollPane;
  }

  public JBScrollPane getDepthScrollPane() {
    return myDepthScrollPane;
  }

  public JBScrollPane getStencilScrollPane() {
    return myStencilScrollPane;
  }

  public JBScrollPane getDocsScrollPane() {
    return myDocsScrollPane;
  }

  public JBPanel getDocsPanel() {
    return myDocsPanel;
  }

  public JTextPane getDocsPane() {
    return myDocsPane;
  }

  @Override
  public void dispose() {
  }

  public void setupViewHierarchy(@NotNull Project project) {
    JPanel rootPanel = getRootComponent();
    rootPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

    final ThreeComponentsSplitter threePane = getThreePanes();
    threePane.setDividerWidth(5);

    // Set some preferences on the scrubber.
    getTopPanel().setBorder(BorderFactory.createLineBorder(JBColor.border()));
    getScrubberScrollPane().setViewportView(getScrubberList());

    // Get rid of the hidden component and remove references to its children, since it's just a placeholder for us to use the UI designer.
    getHiddenComponents().removeAll();
    rootPanel.remove(getHiddenComponents());
    setHiddenComponents(null);

    // Configure the buffer views.
    final JBRunnerTabs bufferTabs = new JBRunnerTabs(project, ActionManager.getInstance(), IdeFocusManager.findInstance(), this);
    bufferTabs.setPaintBorder(0, 0, 0, 0).setTabSidePaintBorder(1).setPaintFocus(UIUtil.isUnderDarcula() || UIUtil.isUnderIntelliJLaF())
      .setAlwaysPaintSelectedTab(UIUtil.isUnderDarcula() || UIUtil.isUnderIntelliJLaF());
    JBPanel[] frameBuffers = new JBPanel[]{myColorBuffer, myWireframeBuffer, myDepthBuffer};
    for (int i = 0; i < frameBuffers.length; ++i) {
      bufferTabs.addTab(new TabInfo(frameBuffers[i]).setText(FrameBufferController.BufferType.values()[i].getName()));
    }
    bufferTabs.setBorder(new EmptyBorder(0, 2, 0, 0));
    setBufferTabs(bufferTabs);

    // Put the buffer views in a wrapper so a border can be drawn around it.
    Wrapper bufferWrapper = new Wrapper();
    bufferWrapper.setLayout(new BorderLayout());
    bufferWrapper.setBorder(BorderFactory.createLineBorder(JBColor.border()));
    bufferWrapper.setContent(bufferTabs);

    // Configure the Atom tree container.
    Wrapper atomTreeWrapper = new Wrapper();
    atomTreeWrapper.setBorder(BorderFactory.createLineBorder(JBColor.border()));
    atomTreeWrapper.setContent(myAtomScrollPane);

    // Now add the atom tree and buffer views to the middle pane in the main pane.
    final JBSplitter middleSplitter = new JBSplitter(false);
    middleSplitter.setMinimumSize(new Dimension(100, 10));
    middleSplitter.setFirstComponent(atomTreeWrapper);
    middleSplitter.setSecondComponent(bufferWrapper);
    threePane.setInnerComponent(middleSplitter);

    // Configure the miscellaneous tabs.
    JBRunnerTabs miscTabs = new JBRunnerTabs(project, ActionManager.getInstance(), IdeFocusManager.findInstance(), this);
    miscTabs.setPaintBorder(0, 0, 0, 0).setTabSidePaintBorder(1).setPaintFocus(UIUtil.isUnderDarcula() || UIUtil.isUnderIntelliJLaF())
      .setAlwaysPaintSelectedTab(UIUtil.isUnderDarcula() || UIUtil.isUnderIntelliJLaF());
    miscTabs.addTab(new TabInfo(getMemoryPanel()).setText("Memory"));
    miscTabs.addTab(new TabInfo(getImagePanel()).setText("Image"));
    miscTabs.addTab(new TabInfo(getDocsPanel()).setText("Docs"));
    miscTabs.setBorder(new EmptyBorder(0, 2, 0, 0));

    getDocsScrollPane().setViewportView(getDocsPane());

    // More borders for miscellaneous tabs.
    Wrapper miscWrapper = new Wrapper();
    miscWrapper.setBorder(BorderFactory.createLineBorder(JBColor.border()));
    miscWrapper.setContent(miscTabs);

    // Borders for the state tree as well.
    Wrapper stateWrapper = new Wrapper();
    stateWrapper.setContent(getStateScrollPane());
    stateWrapper.setBorder(BorderFactory.createLineBorder(JBColor.border()));

    // Configure the bottom splitter.
    final JBSplitter bottomSplitter = new JBSplitter(false);
    bottomSplitter.setMinimumSize(new Dimension(100, 10));
    bottomSplitter.setFirstComponent(stateWrapper);
    bottomSplitter.setSecondComponent(miscWrapper);
    threePane.setLastComponent(bottomSplitter);

    // Make sure the bottom splitter honors minimum sizes.
    threePane.setHonorComponentsMinimumSize(true);

    threePane.addHierarchyBoundsListener(new HierarchyBoundsAdapter() {
      @Override
      public void ancestorResized(HierarchyEvent hierarchyEvent) {
        super.ancestorResized(hierarchyEvent);
        resize();
      }
    });
  }

  public void resize() {
    ThreeComponentsSplitter threePane = getThreePanes();

    assert (threePane.getLastComponent() instanceof JBSplitter);
    JBSplitter bottomSplitter = (JBSplitter)threePane.getLastComponent();

    assert (threePane.getInnerComponent() instanceof JBSplitter);
    JBSplitter middleSplitter = (JBSplitter)threePane.getInnerComponent();

    int scrubberHeight = getScrubberScrollPane().getMinimumSize().height;
    if (threePane.getFirstSize() < scrubberHeight) {
      int totalHeight = threePane.getHeight();
      int residualHeightAfter = Math.max(0, totalHeight - scrubberHeight);

      threePane.setFirstSize(scrubberHeight);
      int middleSize = middleSplitter.getPreferredSize().height;
      int bottomSize = bottomSplitter.getPreferredSize().height;
      if (bottomSize + middleSize > 0) {
        threePane.setLastSize(residualHeightAfter * bottomSize / (bottomSize + middleSize)); // Split the middle and bottom panes evenly.
      }
      else {
        threePane.setLastSize(residualHeightAfter / 2);
      }
    }
  }
}
