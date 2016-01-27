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
package com.android.tools.idea.avdmanager;

import com.android.sdklib.internal.avd.AvdInfo;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.JBMenuItem;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * An action panel that behaves similarly to an Android overflow menu. Actions which
 * do not fit in the space provided are relegated to an overflow menu that can be invoked by
 * clicking on the last icon of the menu.
 */
public class AvdActionPanel extends JPanel implements AvdUiAction.AvdInfoProvider {
  @NotNull private final AvdInfo myAvdInfo;
  private final AvdRefreshProvider myRefreshProvider;
  private final JBPopupMenu myOverflowMenu = new JBPopupMenu();
  private final FocusableHyperlinkLabel myOverflowMenuButton = new FocusableHyperlinkLabel("", AllIcons.ToolbarDecorator.Mac.MoveDown);
  private final Border myMargins = IdeBorderFactory.createEmptyBorder(5, 3, 5, 3);

  public List<FocusableHyperlinkLabel> myVisibleComponents = Lists.newArrayList();

  private boolean myFocused;
  private int myFocusedComponent = -1;

  public interface AvdRefreshProvider {
    void refreshAvds();
    @Nullable Project getProject();

    @NotNull JComponent getComponent();
  }

  public AvdActionPanel(@NotNull AvdInfo avdInfo, int numVisibleActions, AvdRefreshProvider refreshProvider) {
    myRefreshProvider = refreshProvider;
    setOpaque(true);
    setBorder(IdeBorderFactory.createEmptyBorder(10, 10, 10, 10));
    myAvdInfo = avdInfo;
    List<AvdUiAction> actions = getActions();
    setLayout(new FlowLayout(FlowLayout.RIGHT, 3, 0));
    int visibleActionCount = 0;
    boolean errorState = false;
    if (avdInfo.getStatus() != AvdInfo.AvdStatus.OK) {
      AvdUiAction action = null;
      if (AvdManagerConnection.isSystemImageDownloadProblem(avdInfo.getStatus())) {
        action = new InstallSystemImageAction(this);
      }
      else if (AvdManagerConnection.isAvdRepairable(avdInfo.getStatus())) {
        action = new RepairAvdAction(this);
      }
      if (action != null) {
        FocusableHyperlinkLabel repairAction = new FocusableHyperlinkLabel(action.getText(), action.getIcon());
        add(repairAction);
        repairAction.addHyperlinkListener(action);
        myVisibleComponents.add(repairAction);
      } else {
        add(new JBLabel("Failed to load", AllIcons.General.BalloonError, SwingConstants.LEADING));
      }
      numVisibleActions = 0;
      errorState = true;
    }

    for (AvdUiAction action : actions) {
      JComponent actionLabel;
      // Add extra items to the overflow menu
      if (errorState || numVisibleActions != -1 && visibleActionCount >= numVisibleActions) {
        JBMenuItem menuItem = new JBMenuItem(action);
        myOverflowMenu.add(menuItem);
        actionLabel = menuItem;
      } else {
        // Add visible items to the panel
        actionLabel = new FocusableHyperlinkLabel("", action.getIcon());
        ((FocusableHyperlinkLabel)actionLabel).addHyperlinkListener(action);
        add(actionLabel);
        myVisibleComponents.add((FocusableHyperlinkLabel)actionLabel);
        visibleActionCount++;
      }
      actionLabel.setToolTipText(action.getDescription());
      actionLabel.setBorder(myMargins);
    }
    myOverflowMenuButton.setBorder(myMargins);
    add(myOverflowMenuButton);
    myVisibleComponents.add(myOverflowMenuButton);
    myOverflowMenuButton.addHyperlinkListener(new HyperlinkListener() {
      @Override
      public void hyperlinkUpdate(HyperlinkEvent e) {
        myOverflowMenu
          .show(myOverflowMenuButton, myOverflowMenuButton.getX() - myOverflowMenu.getPreferredSize().width, myOverflowMenuButton.getY());
      }
    });
    addKeyListener(new KeyAdapter() {
      @Override
      public void keyTyped(KeyEvent e) {
        if (e.getKeyChar() == KeyEvent.VK_ENTER || e.getKeyChar() == KeyEvent.VK_SPACE) {
          runFocusedAction();
        }
      }
    });
  }

  @NotNull
  private List<AvdUiAction> getActions() {
    return ImmutableList.of(new RunAvdAction(this),
                            new EditAvdAction(this),
                            new DuplicateAvdAction(this),
                            //new ExportAvdAction(this), // TODO: implement export/import
                            new WipeAvdDataAction(this),
                            new ShowAvdOnDiskAction(this),
                            new AvdSummaryAction(this),
                            new DeleteAvdAction(this),
                            new StopAvdAction(this));
  }

  @NotNull
  @Override
  public AvdInfo getAvdInfo() {
    return myAvdInfo;
  }

  @Override
  public void refreshAvds() {
    myRefreshProvider.refreshAvds();
  }

  @Nullable
  @Override
  public Project getProject() {
    return myRefreshProvider.getProject();
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myRefreshProvider.getComponent();
  }

  public void showPopup(@NotNull Component c, @NotNull MouseEvent e) {
    myOverflowMenu.show(c, e.getX(), e.getY());
  }

  public void runFocusedAction() {
    myVisibleComponents.get(myFocusedComponent).doClick();
  }

  public boolean cycleFocus(boolean backward) {
    if (backward) {
      if (myFocusedComponent == -1) {
        myFocusedComponent = myVisibleComponents.size() - 1;
        return true;
      } else {
        myFocusedComponent--;
        return myFocusedComponent != -1;
      }
    } else {
      if (myFocusedComponent == myVisibleComponents.size() - 1) {
        myFocusedComponent = -1;
        return false;
      } else {
        myFocusedComponent++;
        return true;
      }
    }
  }

  public void setFocused(boolean focused) {
    myFocused = focused;
    if (!focused) {
      myFocusedComponent = -1;
    }
  }

  private class FocusableHyperlinkLabel extends HyperlinkLabel {
    FocusableHyperlinkLabel(String text, Icon icon) {
      super(text, JBColor.foreground(), JBColor.background(), JBColor.foreground());
      setIcon(icon);
      setOpaque(false);
      setUseIconAsLink(true);
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      if (myFocused && myFocusedComponent != -1 && myVisibleComponents.get(myFocusedComponent) == this) {
        g.setColor(UIUtil.getTableSelectionForeground());
        UIUtil.drawDottedRectangle(g, 0, 0, getWidth() - 2, getHeight() - 2);
      }
    }
  }
}
