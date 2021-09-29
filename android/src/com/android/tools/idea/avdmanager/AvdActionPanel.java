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

import static com.android.tools.idea.wearpairing.WearPairingManagerKt.isWearOrPhone;

import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.adtui.common.ColoredIconGenerator;
import com.android.tools.idea.devicemanager.virtualtab.columns.ExploreAvdAction;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.log.LogWrapper;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.JBMenuItem;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An action panel that behaves similarly to an Android overflow menu. Actions which
 * do not fit in the space provided are relegated to an overflow menu that can be invoked by
 * clicking on the last icon of the menu.
 */
public class AvdActionPanel extends JPanel implements AvdUiAction.AvdInfoProvider {
  @NotNull private final AvdInfo myAvdInfo;
  private final AvdRefreshProvider myRefreshProvider;
  private final @NotNull Collection<@NotNull AvdUiAction> myOverflowActions = new ArrayList<>();
  private final FocusableHyperlinkLabel myOverflowMenuButton = new FocusableHyperlinkLabel("", AllIcons.Actions.MoveDown);

  private final @NotNull List<@NotNull HyperlinkLabel> myVisibleComponents = new ArrayList<>();

  private boolean myFocused;
  private int myFocusedComponent = -1;
  private boolean myHighlighted = false;

  public interface AvdRefreshProvider {
    void refreshAvds();

    void refreshAvdsAndSelect(@Nullable AvdInfo avdToSelect);

    @Nullable Project getProject();

    @NotNull JComponent getComponent();
  }

  public AvdActionPanel(@NotNull AvdRefreshProvider refreshProvider,
                        @NotNull AvdInfo avdInfo,
                        boolean logDeviceManagerEvents,
                        boolean projectOpen,
                        int numVisibleActions) {
    myRefreshProvider = refreshProvider;
    setOpaque(true);
    setBorder(JBUI.Borders.empty(10));
    myAvdInfo = avdInfo;
    List<AvdUiAction> actions = getActions(logDeviceManagerEvents, projectOpen);
    setLayout(new FlowLayout(FlowLayout.RIGHT, JBUIScale.scale(12), 0));
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
        FocusableHyperlinkLabel repairAction = new FocusableHyperlinkLabel(action.getText(), action.getIcon(), action.isEnabled());
        add(repairAction);
        repairAction.addHyperlinkListener(action);
        myVisibleComponents.add(repairAction);
      }
      else {
        add(new JBLabel("Failed to load", AllIcons.General.BalloonError, SwingConstants.LEADING));
      }
      numVisibleActions = 0;
      errorState = true;
    }

    for (AvdUiAction action : actions) {
      // Add extra items to the overflow menu
      if (errorState || numVisibleActions != -1 && visibleActionCount >= numVisibleActions) {
        myOverflowActions.add(action);
      }
      else {
        // Add visible items to the panel
        FocusableHyperlinkLabel actionLabel = new FocusableHyperlinkLabel("", action.getIcon());
        actionLabel.addHyperlinkListener(action);
        actionLabel.setToolTipText(action.getDescription());
        add(actionLabel);
        myVisibleComponents.add(actionLabel);
        visibleActionCount++;
      }
    }
    add(myOverflowMenuButton);
    myVisibleComponents.add(myOverflowMenuButton);
    myOverflowMenuButton.addHyperlinkListener(event -> showPopup(myOverflowMenuButton, JBUI.scale(28), JBUI.scale(10)));
    addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyChar() == KeyEvent.VK_ENTER || e.getKeyChar() == KeyEvent.VK_SPACE) {
          runFocusedAction();
        }
      }
    });
  }

  private @NotNull List<@NotNull AvdUiAction> getActions(boolean logDeviceManagerEvents, boolean projectOpen) {
    List<AvdUiAction> actionList = new ArrayList<>();
    actionList.add(new RunAvdAction(this, logDeviceManagerEvents));

    if (projectOpen) {
      actionList.add(new ExploreAvdAction(this, logDeviceManagerEvents));
    }

    actionList.add(new EditAvdAction(this, logDeviceManagerEvents));

    if (StudioFlags.WEAR_OS_VIRTUAL_DEVICE_PAIRING_ASSISTANT_ENABLED.get() && isWearOrPhone(myAvdInfo)) {
      actionList.add(new PairDeviceAction(this, logDeviceManagerEvents));
      // TODO(http://b/193748564) Removed until the Virtual tab menu updates its items
      // actionList.add(new UnpairDeviceAction(this, logDeviceManagerEvents));
      actionList.add(new Separator(this));
    }

    actionList.add(new DuplicateAvdAction(this, logDeviceManagerEvents));
    actionList.add(new WipeAvdDataAction(this, logDeviceManagerEvents));

    if (EmulatorAdvFeatures.emulatorSupportsFastBoot(AndroidSdks.getInstance().tryToChooseSdkHandler(),
                                                     new StudioLoggerProgressIndicator(AvdActionPanel.class),
                                                     new LogWrapper(Logger.getInstance(AvdManagerConnection.class)))) {
      actionList.add(new ColdBootNowAction(this, logDeviceManagerEvents));
    }

    actionList.add(new ShowAvdOnDiskAction(this, logDeviceManagerEvents));

    if (!StudioFlags.ENABLE_NEW_DEVICE_MANAGER_PANEL.get()) {
      actionList.add(new AvdSummaryAction(this));
    }

    actionList.add(new Separator(this));
    actionList.add(new DeleteAvdAction(this, logDeviceManagerEvents));

    if (!StudioFlags.ENABLE_NEW_DEVICE_MANAGER_PANEL.get()) {
      actionList.add(new StopAvdAction(this, logDeviceManagerEvents));
    }

    return actionList;
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

  @Override
  public void refreshAvdsAndSelect(@Nullable AvdInfo avdToSelect) {
    myRefreshProvider.refreshAvdsAndSelect(avdToSelect);
  }

  @Nullable
  @Override
  public Project getProject() {
    return myRefreshProvider.getProject();
  }

  @NotNull
  @Override
  public JComponent getAvdProviderComponent() {
    return myRefreshProvider.getComponent();
  }

  public void showPopup(@NotNull Component c, @NotNull MouseEvent e) {
    showPopup(c, e.getX(), e.getY());
  }

  private void showPopup(@NotNull Component c, int x, int y) {
    JPopupMenu overflowMenu = new JBPopupMenu();
    Border border = JBUI.Borders.empty(5, 3);

    for (AvdUiAction action : myOverflowActions) {
      if (action instanceof Separator) {
        overflowMenu.addSeparator();
      }
      else {
        JMenuItem menuItem = new JBMenuItem(action);

        menuItem.setBorder(border);
        menuItem.setToolTipText(action.getDescription());

        overflowMenu.add(menuItem);
      }
    }

    overflowMenu.show(c, x, y);
  }

  private void runFocusedAction() {
    myVisibleComponents.get(myFocusedComponent).doClick();
  }

  public int getFocusedComponent() {
    return myFocusedComponent;
  }

  public void setFocusedComponent(int focusedComponent) {
    assert 0 <= focusedComponent && focusedComponent < myVisibleComponents.size();

    myFocusedComponent = focusedComponent;
    myFocused = true;
  }

  public int getVisibleComponentCount() {
    return myVisibleComponents.size();
  }

  public boolean cycleFocus(boolean backward) {
    if (backward) {
      if (myFocusedComponent == -1) {
        myFocusedComponent = myVisibleComponents.size() - 1;
        return true;
      }
      else {
        myFocusedComponent--;
        return myFocusedComponent != -1;
      }
    }
    else {
      if (myFocusedComponent == myVisibleComponents.size() - 1) {
        myFocusedComponent = -1;
        return false;
      }
      else {
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

  public void setHighlighted(boolean highlighted) {
    myHighlighted = highlighted;
  }

  private class FocusableHyperlinkLabel extends HyperlinkLabel {
    Icon myHighlightedIcon;

    FocusableHyperlinkLabel(String text, Icon icon) {
      super(text, JBColor.foreground(), JBColor.background(), JBColor.foreground());
      setIcon(icon);
      setOpaque(false);
      setUseIconAsLink(true);

      if (text.isEmpty()) {
        setMaximumSize(new JBDimension(22, 22));
      }

      if (icon != null) {
        myHighlightedIcon = ColoredIconGenerator.generateWhiteIcon(myIcon);
      }
    }

    FocusableHyperlinkLabel(String text, Icon icon, boolean enabled) {
      this(text, icon);
      setEnabled(enabled);
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      if (myFocused && myFocusedComponent != -1 && myVisibleComponents.get(myFocusedComponent) == this) {
        g.setColor(UIUtil.getTableSelectionForeground(true));
        UIUtil.drawDottedRectangle(g, 0, 0, getWidth() - 2, getHeight() - 2);
      }
      if (myIcon != null) {
        // Repaint the icon
        Icon theIcon = myIcon;
        if (myHighlighted && myHighlightedIcon != null) {
          // Use white when the cell is highlighted
          theIcon = myHighlightedIcon;
        }
        theIcon.paintIcon(this, g, 0, (getHeight() - theIcon.getIconHeight()) / 2);
      }
    }

    @Override
    public @NotNull Dimension getPreferredSize() {
      if (myText.isEmpty()) {
        return new JBDimension(22, 22);
      }

      return super.getPreferredSize();
    }
  }
}
