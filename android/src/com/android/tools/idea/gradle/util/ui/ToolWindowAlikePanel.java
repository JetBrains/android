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
package com.android.tools.idea.gradle.util.ui;

import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.DataManager;
import com.intellij.ide.DefaultTreeExpander;
import com.intellij.ide.TreeExpander;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.impl.content.BaseLabel;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.tabs.TabsUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.TreeNode;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;

import static com.intellij.icons.AllIcons.General.CollapseAll;
import static com.intellij.icons.AllIcons.General.ExpandAll;
import static com.intellij.openapi.actionSystem.ActionPlaces.UNKNOWN;
import static com.intellij.ui.ScrollPaneFactory.createScrollPane;

/**
 * Panel that looks like an IDEA tool window. It has header with title and, optionally, action buttons.
 */
public class ToolWindowAlikePanel extends JPanel {
  private final Header myHeader;

  @NotNull
  public static ToolWindowAlikePanel createTreePanel(@NotNull String title, @NotNull JTree tree) {
    ToolWindowAlikePanel panel = new ToolWindowAlikePanel(title, createScrollPane(tree));

    Object root = tree.getModel().getRoot();
    if (root instanceof TreeNode && ((TreeNode)root).getChildCount() > 0) {
      TreeExpander expander = new DefaultTreeExpander(tree);
      CommonActionsManager actions = CommonActionsManager.getInstance();

      AnAction expandAllAction = actions.createExpandAllAction(expander, tree);
      expandAllAction.getTemplatePresentation().setIcon(ExpandAll);

      AnAction collapseAllAction = actions.createCollapseAllAction(expander, tree);
      collapseAllAction.getTemplatePresentation().setIcon(CollapseAll);

      panel.setAdditionalTitleActions(expandAllAction, collapseAllAction);
    }

    return panel;
  }

  public ToolWindowAlikePanel(@NotNull String title, @NotNull JComponent contents) {
    super(new BorderLayout());
    myHeader = new Header(title);
    add(myHeader, BorderLayout.NORTH);
    add(contents, BorderLayout.CENTER);
  }

  public void setAdditionalTitleActions(@NotNull AnAction... actions) {
    myHeader.setAdditionalActions(actions);
  }

  /**
   * Adapted from {@link com.intellij.openapi.wm.impl.ToolWindowHeader}.
   */
  private static class Header extends JPanel {
    private JPanel myButtonPanel;

    Header(@NotNull String title) {
      super(new BorderLayout());

      JLabel titleLabel = new JLabel(title);
      titleLabel.setFont(BaseLabel.getLabelFont());
      titleLabel.setForeground(JBColor.foreground());
      titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));
      add(titleLabel, BorderLayout.CENTER);

      myButtonPanel = new JPanel();
      myButtonPanel.setOpaque(false);
      myButtonPanel.setLayout(new BoxLayout(myButtonPanel, BoxLayout.X_AXIS));
      myButtonPanel.setBorder(BorderFactory.createEmptyBorder(0, 3, 0, 3));

      add(myButtonPanel, BorderLayout.EAST);
      setBorder(BorderFactory.createEmptyBorder(TabsUtil.TABS_BORDER, 1, TabsUtil.TABS_BORDER, 1));
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D g2d = (Graphics2D)g;
      Image image = drawToBuffer(getBounds().height);
      Rectangle clipBounds = g2d.getClip().getBounds();
      for (int x = clipBounds.x; x < clipBounds.x + clipBounds.width; x += 150) {
        //noinspection ConstantConditions
        UIUtil.drawImage(g, image, x, 0, null);
      }
    }

    @NotNull
    private static BufferedImage drawToBuffer(int height) {
      int width = 150;
      BufferedImage image = UIUtil.createImage(width, height, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = image.createGraphics();
      UIUtil.drawHeader(g, 0, width, height, true, true, true, true);
      g.dispose();
      return image;
    }

    @Override
    protected void paintChildren(Graphics g) {
      Graphics2D graphics = (Graphics2D)g.create();
      UISettings.setupAntialiasing(g);
      super.paintChildren(graphics);
      graphics.dispose();
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension size = super.getPreferredSize();
      return new Dimension(size.width, TabsUtil.getTabsHeight());
    }

    @Override
    public Dimension getMinimumSize() {
      Dimension size = super.getMinimumSize();
      return new Dimension(size.width, TabsUtil.getTabsHeight());
    }

    void setAdditionalActions(@NotNull AnAction[] actions) {
      myButtonPanel.removeAll();
      int actionCount = actions.length;
      for (int i = 0; i < actionCount; i++) {
        AnAction action = actions[i];
        if (action == null) {
          continue;
        }
        myButtonPanel.add(new ActionButton(action));
        if (i < actionCount -1) {
          myButtonPanel.add(Box.createHorizontalStrut(9));
        }
      }
    }
  }

  private static class ActionButton extends Wrapper implements ActionListener {
    private final InplaceButton myButton;
    private final AnAction myAction;

    ActionButton(@NotNull AnAction action) {
      myAction = action;
      Icon icon = action.getTemplatePresentation().getIcon();
      Icon hoveredIcon = action.getTemplatePresentation().getHoveredIcon();
      if (hoveredIcon == null) {
        hoveredIcon = icon;
      }

      String toolTip = KeymapUtil.createTooltipText(action.getTemplatePresentation().getText(), action);
      myButton = new InplaceButton(toolTip, icon, this);

      myButton.setIcons(icon, icon, hoveredIcon);

      myButton.setHoveringEnabled(!SystemInfo.isMac);
      setContent(myButton);
      setOpaque(false);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      DataContext dataContext = DataManager.getInstance().getDataContext(this);
      ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
      InputEvent inputEvent = e.getSource() instanceof InputEvent ? (InputEvent)e.getSource() : null;
      //noinspection ConstantConditions
      AnActionEvent event = AnActionEvent.createFromAnAction(myAction, inputEvent, UNKNOWN, dataContext);
      actionManager.fireBeforeActionPerformed(myAction, dataContext, event);
      myAction.actionPerformed(event);
    }
  }
}
