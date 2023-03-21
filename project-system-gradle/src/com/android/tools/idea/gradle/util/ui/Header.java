/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.impl.content.BaseLabel;
import com.intellij.toolWindow.ToolWindowHeader;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

import static com.intellij.ide.ui.UISettings.setupAntialiasing;
import static com.intellij.openapi.actionSystem.ActionPlaces.UNKNOWN;
import static com.intellij.openapi.keymap.KeymapUtil.createTooltipText;
import static com.intellij.ui.tabs.TabsUtil.getTabsHeight;
import static com.intellij.util.ui.UIUtil.*;

/**
 * Adapted from {@link ToolWindowHeader}.
 */
public class Header extends JPanel {
  @NotNull private final String myTitle;

  private JPanel myButtonPanel;
  private BufferedImage myImage;
  private BufferedImage myActiveImage;

  private final EventDispatcher<ActivationListener> myEventDispatcher = EventDispatcher.create(ActivationListener.class);

  public Header(@NotNull String title) {
    super(new BorderLayout());
    myTitle = title;

    JLabel titleLabel = new JLabel(title);
    titleLabel.setFont(BaseLabel.getLabelFont());
    titleLabel.setForeground(JBColor.foreground());
    titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));
    add(titleLabel, BorderLayout.CENTER);

    MouseAdapter mouseListener = new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        myEventDispatcher.getMulticaster().activated();
      }
    };
    titleLabel.addMouseListener(mouseListener);
    addMouseListener(mouseListener);

    myButtonPanel = new JPanel();
    myButtonPanel.setOpaque(false);
    myButtonPanel.setLayout(new BoxLayout(myButtonPanel, BoxLayout.X_AXIS));
    myButtonPanel.setBorder(BorderFactory.createEmptyBorder(0, 3, 0, 3));

    add(myButtonPanel, BorderLayout.EAST);
    setBorder(JBUI.Borders.empty());
  }

  @NotNull
  public String getTitle() {
    return myTitle;
  }

  @Override
  protected void paintComponent(Graphics g) {
    Rectangle r = getBounds();
    Graphics2D g2d = (Graphics2D)g;

    Image image;
    if (isActive()) {
      if (myActiveImage == null) {
        myActiveImage = drawToBuffer(true, r.height);
      }
      image = myActiveImage;
    }
    else {
      if (myImage == null) {
        myImage = drawToBuffer(false, r.height);
      }
      image = myImage;
    }

    Rectangle clipBounds = g2d.getClip().getBounds();
    for (int x = clipBounds.x; x < clipBounds.x + clipBounds.width; x += 150) {
      drawImage(g, image, x, 0, null);
    }
  }

  @NotNull
  private static BufferedImage drawToBuffer(boolean active, int height) {
    int width = 150;
    BufferedImage image = ImageUtil.createImage(width, height, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    drawHeader(g, 0, width, height, active, true, false, true);
    g.dispose();
    return image;
  }

  @Override
  protected void paintChildren(Graphics g) {
    Graphics2D graphics = (Graphics2D)g.create();

    setupAntialiasing(graphics);
    super.paintChildren(graphics);

    Rectangle r = getBounds();
    if (!isActive() && !isUnderDarcula()) {
      //noinspection UseJBColor
      graphics.setColor(new Color(255, 255, 255, 30));
      graphics.fill(r);
    }
    graphics.dispose();
  }

  @Override
  public Dimension getPreferredSize() {
    Dimension size = super.getPreferredSize();
    return new Dimension(size.width, getTabsHeight());
  }

  @Override
  public Dimension getMinimumSize() {
    Dimension size = super.getMinimumSize();
    return new Dimension(size.width, getTabsHeight());
  }

  public void setAdditionalActions(@NotNull List<AnAction> actions) {
    setAdditionalActions(actions.toArray(AnAction.EMPTY_ARRAY));
  }

  public void setAdditionalActions(@NotNull AnAction...actions) {
    myButtonPanel.removeAll();
    myButtonPanel.revalidate();
    myButtonPanel.repaint();

    int actionCount = actions.length;
    for (int i = 0; i < actionCount; i++) {
      AnAction action = actions[i];
      if (action == null) {
        continue;
      }
      myButtonPanel.add(new ActionButton(action));
      if (i < actionCount - 1) {
        myButtonPanel.add(Box.createHorizontalStrut(9));
      }
    }
  }

  public void addActivationListener(@NotNull ActivationListener listener, @NotNull Disposable parent) {
    myEventDispatcher.addListener(listener, parent);
  }

  public boolean isActive() {
    return true;
  }

  private static class ActionButton extends Wrapper implements ActionListener {
    private final InplaceButton myButton;
    private final AnAction myAction;

    ActionButton(@NotNull AnAction action) {
      myAction = action;
      Icon icon;
      Icon hoveredIcon = null;
      if (action instanceof Separator) {
        icon = AllIcons.General.Divider;
      }
      else {
        icon = action.getTemplatePresentation().getIcon();
        hoveredIcon = action.getTemplatePresentation().getHoveredIcon();
      }
      if (hoveredIcon == null) {
        hoveredIcon = icon;
      }

      String toolTip = createTooltipText(action.getTemplatePresentation().getText(), action);
      myButton = new InplaceButton(toolTip, icon, this);

      myButton.setIcons(icon, icon, hoveredIcon);

      myButton.setHoveringEnabled(!SystemInfo.isMac);
      setContent(myButton);
      setOpaque(false);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      DataContext dataContext = DataManager.getInstance().getDataContext(this);
      InputEvent inputEvent = e.getSource() instanceof InputEvent ? (InputEvent)e.getSource() : null;
      AnActionEvent event = AnActionEvent.createFromAnAction(myAction, inputEvent, UNKNOWN, dataContext);
      ActionUtil.performActionDumbAwareWithCallbacks(myAction, event);
    }
  }

  public interface ActivationListener extends EventListener {
    void activated();
  }
}
