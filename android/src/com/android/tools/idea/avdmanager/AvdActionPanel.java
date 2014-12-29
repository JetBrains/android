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
import com.google.common.collect.Maps;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.JBMenuItem;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Map;

/**
 * An action panel that behaves similarly to an Android overflow menu. Actions which
 * do not fit in the space provided are relegated to an overflow menu that can be invoked by
 * clicking on the last icon of the menu.
 */
public class AvdActionPanel extends JPanel implements AvdUiAction.AvdInfoProvider {
  private static final Logger LOG = Logger.getInstance(AvdActionPanel.class);
  @NotNull private final AvdInfo myAvdInfo;
  private final AvdRefreshProvider myRefreshProvider;
  private Map<JComponent, AvdUiAction> myButtonActionMap = Maps.newHashMap();
  private final JBPopupMenu myOverflowMenu = new JBPopupMenu();
  private final JBLabel myOverflowMenuButton = new JBLabel(AllIcons.ToolbarDecorator.Mac.MoveDown);
  private final Border myMargins = IdeBorderFactory.createEmptyBorder(5, 3, 5, 3);

  public interface AvdRefreshProvider {
    void refreshAvds();
    @Nullable Project getProject();
    void notifyRun();
  }

  public AvdActionPanel(@NotNull AvdInfo avdInfo, int numVisibleActions, AvdRefreshProvider refreshProvider) {
    myRefreshProvider = refreshProvider;
    setOpaque(true);
    setBorder(IdeBorderFactory.createEmptyBorder(10, 10, 10, 10));
    myAvdInfo = avdInfo;
    List<AvdUiAction> actions = getActions();
    setLayout(new FlowLayout(FlowLayout.RIGHT, 3, 0));
    int visibleActionCount = 0;

    MouseAdapter mouseAdapter = new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        myButtonActionMap.get(e.getSource()).actionPerformed(null);
      }
    };
    boolean errorState = false;
    if (avdInfo.getStatus() != AvdInfo.AvdStatus.OK) {
      if (AvdManagerConnection.isAvdRepairable(avdInfo.getStatus())) {
        JBLabel repairAction = new JBLabel("Repair Device", AllIcons.General.BalloonWarning, SwingConstants.LEADING);
        myButtonActionMap.put(repairAction, new EditAvdAction(this));
        repairAction.addMouseListener(mouseAdapter);
        add(repairAction, "Repair Device");
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
        menuItem.setText(action.getText());
        myOverflowMenu.add(menuItem);
        actionLabel = menuItem;
      } else {
        // Add visible items to the panel
        actionLabel = new JBLabel(action.getIcon());
        add(actionLabel);
        actionLabel.addMouseListener(mouseAdapter);
        visibleActionCount++;
      }
      actionLabel.setToolTipText(action.getDescription());
      actionLabel.setBorder(myMargins);
      myButtonActionMap.put(actionLabel, action);
    }
    myOverflowMenuButton.setBorder(myMargins);
    add(myOverflowMenuButton);
    myOverflowMenuButton.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        myOverflowMenu.show(myOverflowMenuButton, e.getX() - myOverflowMenu.getPreferredSize().width, e.getY());
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

  @Override
  public void notifyRun() {
    myRefreshProvider.notifyRun();
  }

  public void showPopup(@NotNull Component c, @NotNull MouseEvent e) {
    myOverflowMenu.show(c, e.getX(), e.getY());
  }
}
