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
package com.android.tools.idea.profiling.view;

import com.android.annotations.NonNull;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.perflib.analyzer.AnalysisReport;
import com.android.tools.perflib.analyzer.AnalysisResultEntry;
import com.android.tools.perflib.analyzer.AnalyzerTask;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventCategory;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.MatteBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Responsible for creating tree nodes and rendering them for the capture analysis results light tool window.
 * However, since the tool window can be reused, we need to stash the entire component so we don't destroy/recreate it constantly. This also
 * helps to preserve selection state of the components.
 */
public abstract class AnalysisContentsDelegate extends ColoredTreeCellRenderer implements Disposable {
  @NotNull protected CapturePanel myCapturePanel;
  @NotNull private ThreeComponentsSplitter mySplitter;
  @NotNull private JPanel myTaskPanel;
  @NotNull protected Tree myResultsTree;
  @NotNull private JTextPane myResultExplanationArea;

  @NotNull private Set<AnalyzerTask> myEnabledTasks = new HashSet<AnalyzerTask>();
  @NotNull private Map<String, DefaultMutableTreeNode> myCategoryNodes = new HashMap<String, DefaultMutableTreeNode>();
  private boolean myCanRunAnalysis = false;

  public AnalysisContentsDelegate(@NotNull CapturePanel capturePanel) {
    myCapturePanel = capturePanel;
    mySplitter = new ThreeComponentsSplitter(true);
    mySplitter.setDividerWidth(10);
    Disposer.register(this, mySplitter);

    myTaskPanel = new JPanel(new LayoutManager() {
      @Override
      public void addLayoutComponent(String s, Component component) {

      }

      @Override
      public void removeLayoutComponent(Component component) {

      }

      @Override
      public Dimension preferredLayoutSize(Container container) {
        return minimumLayoutSize(container);
      }

      @Override
      public Dimension minimumLayoutSize(Container container) {
        int width = 0;
        int height = 0;

        for (Component component : container.getComponents()) {
          Dimension componentSize = component.getPreferredSize();
          width = Math.max(componentSize.width, width);
          height += componentSize.height;
        }

        Insets insets = container.getInsets();
        return new Dimension(width + insets.left + insets.right, height + insets.top + insets.bottom);
      }

      @Override
      public void layoutContainer(Container container) {
        Insets insets = container.getInsets();
        int width = container.getWidth() - insets.left - insets.right;
        int startY = insets.top;

        for (Component component : container.getComponents()) {
          int componentHeight = component.getPreferredSize().height;
          component.setBounds(0, startY, width, componentHeight);
          startY += componentHeight;
        }
      }
    });
    myTaskPanel.setBackground(UIUtil.getListBackground());

    JScrollPane topScrollPane = ScrollPaneFactory
      .createScrollPane(myTaskPanel, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    topScrollPane.setBorder(new MatteBorder(0, 0, 1, 0, JBColor.border()));

    JLabel resultsTitle = new JLabel(AndroidBundle.message("android.captures.analysis.results.title"), SwingConstants.LEFT);
    resultsTitle.setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));
    resultsTitle.setBorder(BorderFactory.createEmptyBorder(2, 5, 5, 10));

    myResultsTree = new Tree(new DefaultTreeModel(new DefaultMutableTreeNode(null)));
    myResultsTree.setRootVisible(false);
    myResultsTree.setShowsRootHandles(true);
    myResultsTree.setRowHeight(19);
    JScrollPane middleScrollPane = ScrollPaneFactory.createScrollPane(myResultsTree, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                                                      ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    middleScrollPane.setBorder(new MatteBorder(1, 0, 1, 0, JBColor.border()));

    JPanel middlePanel = new JPanel(new BorderLayout());
    middlePanel.add(resultsTitle, BorderLayout.NORTH);
    middlePanel.add(middleScrollPane, BorderLayout.CENTER);

    JLabel explanationTitle = new JLabel(AndroidBundle.message("android.captures.analysis.explanation.title"), SwingConstants.LEFT);
    explanationTitle.setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));
    explanationTitle.setBorder(BorderFactory.createEmptyBorder(2, 5, 5, 10));

    myResultExplanationArea = new JTextPane();
    myResultExplanationArea.setBorder(new MatteBorder(1, 0, 0, 0, JBColor.border()));
    JScrollPane bottomScrollPane = ScrollPaneFactory
      .createScrollPane(myResultExplanationArea, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    bottomScrollPane.setBorder(BorderFactory.createEmptyBorder());

    JPanel bottomPanel = new JPanel(new BorderLayout());
    bottomPanel.add(explanationTitle, BorderLayout.NORTH);
    bottomPanel.add(bottomScrollPane, BorderLayout.CENTER);

    mySplitter.setFirstComponent(topScrollPane);
    mySplitter.setInnerComponent(middlePanel);
    mySplitter.setLastComponent(bottomPanel);

    mySplitter.setFirstSize(128);
    mySplitter.setLastSize(128);

    myResultsTree.setCellRenderer(this);
    myCanRunAnalysis = myCapturePanel.getAnalyzerTasks().length > 0;
    for (final AnalyzerTask task : myCapturePanel.getAnalyzerTasks()) {
      final JBCheckBox taskCheckbox = new JBCheckBox(task.getTaskName(), true);
      taskCheckbox.addItemListener(new ItemListener() {
        @Override
        public void itemStateChanged(ItemEvent itemEvent) {
          if (itemEvent.getStateChange() == ItemEvent.SELECTED) {
            myEnabledTasks.add(task);
            myCanRunAnalysis = true;
          }
          else if (itemEvent.getStateChange() == ItemEvent.DESELECTED) {
            myEnabledTasks.remove(task);
          }
        }
      });
      myTaskPanel.add(taskCheckbox);
      myEnabledTasks.add(task);
    }
  }

  @NotNull
  public abstract Icon getToolIcon();

  @NotNull
  public JComponent getComponent() {
    return mySplitter;
  }

  @NotNull
  public JComponent getFocusComponent() {
    return myTaskPanel;
  }

  @Override
  public void dispose() {
  }

  public boolean canRunAnalysis() {
    return !myEnabledTasks.isEmpty() && myCanRunAnalysis;
  }

  /**
   * This method is responsible for resetting the results panel, messaging the main window to perform the analysis, and
   * collecting/displaying the results.
   */
  public void performAnalysis() {
    myCanRunAnalysis = false;

    UsageTracker.getInstance().log(AndroidStudioEvent.newBuilder()
                                   .setCategory(EventCategory.PROFILING)
                                   .setKind(EventKind.PROFILING_ANALYSIS_RUN));

    final DefaultTreeModel model = (DefaultTreeModel)myResultsTree.getModel();
    final DefaultMutableTreeNode root = (DefaultMutableTreeNode)model.getRoot();
    root.removeAllChildren();
    myCategoryNodes.clear();

    Set<AnalysisReport.Listener> singletonListener = Collections.<AnalysisReport.Listener>singleton(new AnalysisReport.Listener() {
      @Override
      public void onResultsAdded(@NonNull final List<AnalysisResultEntry<?>> entries) {
        UIUtil.invokeLaterIfNeeded(new Runnable() {
          @Override
          public void run() {
            boolean rootChanged = false;
            Set<DefaultMutableTreeNode> changedCategories = new HashSet<DefaultMutableTreeNode>();

            for (AnalysisResultEntry<?> entry : entries) {
              String category = entry.getCategory();
              DefaultMutableTreeNode categoryNode;
              if (!myCategoryNodes.containsKey(category)) {
                categoryNode = new DefaultMutableTreeNode(new String(category));
                myCategoryNodes.put(category, categoryNode);
                root.add(categoryNode);
                rootChanged = true;
              }
              else {
                categoryNode = myCategoryNodes.get(category);
              }

              DefaultMutableTreeNode node = myCapturePanel.getContentsDelegate().getNodeForEntry(categoryNode.getChildCount(), entry);
              if (node != null) {
                changedCategories.add(categoryNode);
                categoryNode.add(node);
              }
            }

            if (rootChanged) {
              model.nodeStructureChanged(root);
            }
            else {
              for (DefaultMutableTreeNode categoryNode : changedCategories) {
                model.nodeStructureChanged(categoryNode);
              }
            }
          }
        });
      }

      @Override
      public void onAnalysisComplete() {

      }

      @Override
      public void onAnalysisCancelled() {

      }
    });

    myCapturePanel.performAnalysis(myEnabledTasks, singletonListener);
  }

  @Nullable
  public abstract DefaultMutableTreeNode getNodeForEntry(int index, @NotNull AnalysisResultEntry<?> entry);
}
