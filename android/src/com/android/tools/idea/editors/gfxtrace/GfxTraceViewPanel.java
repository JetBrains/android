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

import com.android.tools.idea.editors.gfxtrace.controllers.*;
import com.intellij.execution.ui.layout.impl.JBRunnerTabs;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LoadingDecorator;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.openapi.util.Disposer;
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

  @NotNull private JBPanel myMainPanel = new JBPanel(new BorderLayout());

  @NotNull private ContextController myContextController;
  @NotNull private AtomController myAtomController;
  @NotNull private ScrubberController myScrubberController;
  @NotNull private FrameBufferController myFrameBufferController;
  @NotNull private StateController myStateController;
  @NotNull private DocumentationController myDocumentationController;
  @NotNull private TexturesController myTexturesController;


  GfxTraceViewPanel() {
    myMainPanel.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
    myLoadingDecorator = new LoadingDecorator(myMainPanel, this, 0);
    myLoadingDecorator.setLoadingText("Initializing GFX Trace System");
    myLoadingDecorator.startLoading(false);
  }

  void setLoadingError(@NotNull String errorString) {
    myLoadingDecorator.setLoadingText(errorString);
  }

  void finalizeUi(@NotNull GfxTraceEditor editor) {
    ThreeComponentsSplitter threePanes = new ThreeComponentsSplitter(true);
    myMainPanel.add(threePanes, BorderLayout.CENTER);
    threePanes.setDividerWidth(5);

    // Add the toolbar for the device selection.
    myContextController = new ContextController(editor);
    DefaultActionGroup group = new DefaultActionGroup(myContextController.getContextAction());
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true);
    toolbar.getComponent().setName(TOOLBAR_NAME);
    myMainPanel.add(toolbar.getComponent(), BorderLayout.NORTH);

    // Add the scrubber view to the top panel.
    JBList scrubberList = new JBList();
    scrubberList.setLayoutOrientation(JList.HORIZONTAL_WRAP);
    JBScrollPane scrubberScrollPane = new JBScrollPane();
    scrubberScrollPane.setViewportView(scrubberList);
    JPanel scrubberPanel = new JPanel(new BorderLayout());
    scrubberPanel.add(scrubberScrollPane, BorderLayout.CENTER);
    threePanes.setFirstComponent(scrubberPanel);
    threePanes.setFirstSize(150);
    myScrubberController = new ScrubberController(editor, scrubberScrollPane, scrubberList);

    // Configure the Atom tree container.
    JPanel atomTreePanel = new JPanel(new BorderLayout());
    JBScrollPane atomScrollPane = new JBScrollPane();
    atomTreePanel.add(atomScrollPane, BorderLayout.CENTER);
    myAtomController = new AtomController(editor, editor.getProject(), atomScrollPane);

    // Configure the framebuffer views.
    final JBRunnerTabs bufferTabs = new JBRunnerTabs(editor.getProject(), ActionManager.getInstance(), IdeFocusManager.findInstance(), this);
    bufferTabs.setPaintBorder(0, 0, 0, 0).setTabSidePaintBorder(1).setPaintFocus(UIUtil.isUnderDarcula() || UIUtil.isUnderIntelliJLaF())
      .setAlwaysPaintSelectedTab(UIUtil.isUnderDarcula() || UIUtil.isUnderIntelliJLaF());

    JPanel colorPanel = new JPanel(new BorderLayout());
    JBScrollPane colorScrollPane = new JBScrollPane();
    colorPanel.add(colorScrollPane, BorderLayout.CENTER);
    JPanel wireframePanel = new JPanel(new BorderLayout());
    JBScrollPane wireframeScrollPane = new JBScrollPane();
    wireframePanel.add(wireframeScrollPane, BorderLayout.CENTER);
    JPanel depthPanel = new JPanel(new BorderLayout());
    JBScrollPane depthScrollPane = new JBScrollPane();
    depthPanel.add(depthScrollPane, BorderLayout.CENTER);
    bufferTabs.addTab(new TabInfo(colorPanel).setText("Color"));
    bufferTabs.addTab(new TabInfo(wireframePanel).setText("Wireframe"));
    bufferTabs.addTab(new TabInfo(depthPanel).setText("Depth"));
    bufferTabs.setBorder(new EmptyBorder(0, 2, 0, 0));

    // Put the buffer views in a panel so a border can be drawn around it.
    JPanel bufferWrapper = new JPanel(new BorderLayout());
    bufferWrapper.setBorder(BorderFactory.createLineBorder(JBColor.border()));
    bufferWrapper.add(bufferTabs, BorderLayout.CENTER);
    myFrameBufferController = new FrameBufferController(editor, colorScrollPane, wireframeScrollPane, depthScrollPane);

    // Now add the atom tree and buffer views to the middle pane in the main pane.
    final JBSplitter middleSplitter = new JBSplitter(false);
    middleSplitter.setMinimumSize(new Dimension(100, 10));
    middleSplitter.setFirstComponent(atomTreePanel);
    middleSplitter.setSecondComponent(bufferWrapper);
    middleSplitter.setProportion(0.3f);
    threePanes.setInnerComponent(middleSplitter);

    // Configure the miscellaneous tabs.
    JBRunnerTabs miscTabs = new JBRunnerTabs(editor.getProject(), ActionManager.getInstance(), IdeFocusManager.findInstance(), this);
    miscTabs.setPaintBorder(0, 0, 0, 0).setTabSidePaintBorder(1).setPaintFocus(UIUtil.isUnderDarcula() || UIUtil.isUnderIntelliJLaF())
      .setAlwaysPaintSelectedTab(UIUtil.isUnderDarcula() || UIUtil.isUnderIntelliJLaF());

    // Add the textures view to the misc tabs.
    JBList texturesList = new JBList();
    texturesList.setLayoutOrientation(JList.VERTICAL);
    JBScrollPane texturesScrollPane = new JBScrollPane();
    texturesScrollPane.setViewportView(texturesList);
    JPanel texturesPanel = new JPanel(new BorderLayout());
    texturesPanel.add(texturesScrollPane, BorderLayout.CENTER);
    miscTabs.addTab(new TabInfo(texturesPanel).setText("Textures"));
    myTexturesController = new TexturesController(editor, texturesScrollPane, texturesList);

    // Add the memory viewer to the misc tabs
    JPanel memoryPanel = new JPanel();
    miscTabs.addTab(new TabInfo(memoryPanel).setText("Memory"));

    JPanel docsPanel = new JPanel();
    miscTabs.addTab(new TabInfo(docsPanel).setText("Docs"));
    miscTabs.setBorder(new EmptyBorder(0, 2, 0, 0));
    JBScrollPane docsScrollPane = new JBScrollPane();
    JTextPane docsTextPane = new JTextPane();
    docsScrollPane.setViewportView(docsTextPane);
    // TODO: Rewrite to use IntelliJ documentation view.
    myDocumentationController = new DocumentationController(editor, docsTextPane);

    // More borders for miscellaneous tabs.
    JPanel miscPanel = new JPanel(new BorderLayout());
    miscPanel.setBorder(BorderFactory.createLineBorder(JBColor.border()));
    miscPanel.add(miscTabs, BorderLayout.CENTER);

    // Borders for the state tree as well.
    JPanel stateWrapper = new JPanel(new BorderLayout());
    JBScrollPane stateScrollPane = new JBScrollPane();
    stateWrapper.add(stateScrollPane, BorderLayout.CENTER);
    myStateController = new StateController(editor, stateScrollPane);

    // Configure the bottom splitter.
    JBSplitter bottomSplitter = new JBSplitter(false);
    bottomSplitter.setMinimumSize(new Dimension(100, 10));
    bottomSplitter.setFirstComponent(stateWrapper);
    bottomSplitter.setSecondComponent(miscPanel);
    threePanes.setLastComponent(bottomSplitter);
    threePanes.setLastSize(300);

    // Make sure the bottom splitter honors minimum sizes.
    threePanes.setHonorComponentsMinimumSize(true);
    Disposer.register(this, threePanes);

    myLoadingDecorator.stopLoading();
  }

  @NotNull
  public JPanel getRootComponent() {
    return myMainPanel;
  }

  @Override
  public void dispose() {
    myAtomController.clear();
    myScrubberController.clear();
    //myFrameBufferController.clear();
    myStateController.clear();
    //myDocumentationController.clear();
    myTexturesController.clear();
    myMainPanel.removeAll();
    myLoadingDecorator = null;
  }
}
