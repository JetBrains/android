/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.editors.layoutInspector.ui;

import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.layoutinspector.model.ViewNode;
import com.android.tools.adtui.workbench.ToolContent;
import com.android.tools.idea.editors.layoutInspector.LayoutInspectorContext;
import com.android.tools.idea.observable.InvalidationListener;
import com.android.tools.idea.observable.ObservableValue;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import icons.StudioIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class LayoutTreePanel extends JPanel implements ToolContent<LayoutInspectorContext>, InvalidationListener {
  @NotNull private final JScrollPane myTreePanel;
  @Nullable private RollOverTree myTree;
  @Nullable private ViewNodeTreeRenderer myTreeCellRenderer;
  @NotNull private JPanel myBackPanel;
  @NotNull private JLabel myBackLabel;
  @Nullable private LayoutInspectorContext myContext;

  public LayoutTreePanel() {
    setLayout(new BorderLayout());
    myTreePanel = new JBScrollPane();
    myTreePanel.setBackground(JBColor.WHITE);
    add(myTreePanel, BorderLayout.CENTER);
    add(createBackPanel(), BorderLayout.NORTH);
  }

  @Override
  public void setToolContext(@Nullable LayoutInspectorContext toolContext) {
    if (toolContext != null) {
      myContext = toolContext;
      myContext.getSubviewList().addListener(this);
      setBackground(JBColor.WHITE);
      myTree = toolContext.getNodeTree();
      if (myTree == null) return;
      myTreeCellRenderer = (ViewNodeTreeRenderer)myTree.getCellRenderer();
      myTreePanel.setViewportView(myTree);
      myTreePanel.getViewport().setBackground(JBColor.WHITE);
    }
  }

  @NotNull
  private JComponent createBackPanel() {
    // TODO(kelvinhanma b/69255011) refactor to be a common component with DestinationList's back panel.
    myBackPanel = new JPanel(new BorderLayout());
    myBackLabel = new JLabel("Back", StudioIcons.Common.BACK_ARROW, SwingConstants.LEFT);
    myBackLabel.setBorder(new EmptyBorder(8, 6, 8, 0));
    myBackPanel.setBackground(JBColor.WHITE);
    myBackPanel.setVisible(false);
    myBackPanel.add(myBackLabel, BorderLayout.WEST);
    myBackLabel.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        goBack();
      }
    });

    myBackPanel.add(new JSeparator(), BorderLayout.SOUTH);
    return myBackPanel;
  }

  private void goBack() {
    myContext.goBackSubView();
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return this;
  }

  @Override
  public boolean supportsFiltering() {
    return true;
  }

  @Override
  public void setFilter(@Nullable String filter) {
    if (myTreeCellRenderer != null) {
      myTreeCellRenderer.setHighlight(filter);
      myTree.repaint();
    }
  }

  @Override
  public void dispose() {
    myContext.getSubviewList().removeListener(this);
  }

  @Override
  public void onInvalidated(@NotNull ObservableValue<?> sender) {
    if (!myContext.getSubviewList().isEmpty()) {
      ViewNode parentNode = myContext.getSubviewList().get(myContext.getSubviewList().size() - 1);
      if (parentNode == null) return;

      myBackPanel.setVisible(true);
      String id = ViewNodeTreeRenderer.getId(parentNode);
      myBackLabel.setText(id != null ? id : ViewNodeTreeRenderer.getName(parentNode));
    }
    else {
      myBackPanel.setVisible(false);
    }
    myTree = myContext.getNodeTree();
    myTreePanel.setViewportView(myTree);
  }

  @NotNull
  @VisibleForTesting
  JPanel getBackPanel() {
    return myBackPanel;
  }
}
