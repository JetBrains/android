/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.adtui.trackgroup;

import com.android.tools.adtui.DragAndDropList;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.common.StudioColorsKt;
import com.android.tools.adtui.flat.FlatSeparator;
import com.android.tools.adtui.model.stdui.CommonAction;
import com.android.tools.adtui.model.trackgroup.TrackGroupModel;
import com.android.tools.adtui.model.trackgroup.TrackModel;
import com.android.tools.adtui.stdui.CommonButton;
import com.android.tools.adtui.stdui.menu.CommonDropDownButton;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.icons.AllIcons;
import com.intellij.util.ui.JBUI;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.util.HashMap;
import java.util.Map;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A collapsible UI component that contains a list of {@link Track}s to visualize multiple horizontal data series.
 */
public class TrackGroup {
  private static final Icon EXPAND_ICON = AllIcons.Actions.FindAndShowNextMatches;
  private static final Icon COLLAPSE_ICON = AllIcons.Actions.FindAndShowPrevMatches;
  private static final Font TITLE_FONT = AdtUiUtils.DEFAULT_FONT.biggerOn(5f);

  private final JPanel myComponent;
  private final JLabel myTitleLabel;
  private final DragAndDropList<TrackModel> myTrackList;
  private final CommonButton myFilterButton;
  private final CommonDropDownButton myActionsDropdown;
  private final CommonButton myCollapseButton;

  /**
   * @param groupModel      {@link TrackGroup} data model
   * @param rendererFactory factory for instantiating {@link TrackRenderer}s
   */
  public TrackGroup(@NotNull TrackGroupModel groupModel, @NotNull TrackRendererFactory rendererFactory) {
    // Caches Tracks for the list cell renderer.
    Map<Integer, Track> trackModelToComponentMap = new HashMap<>();

    // Initializes UI components.
    myTrackList = new DragAndDropList<>(groupModel);
    myTrackList.setCellRenderer(new ListCellRenderer<TrackModel>() {
      @Override
      public Component getListCellRendererComponent(JList<? extends TrackModel> list,
                                                    TrackModel value,
                                                    int index,
                                                    boolean isSelected,
                                                    boolean cellHasFocus) {
        return trackModelToComponentMap
          .computeIfAbsent(value.getId(), id -> Track.create(value, rendererFactory.createRenderer(value.getRendererType())))
          .getComponent();
      }
    });

    myFilterButton = new CommonButton(AllIcons.General.Filter);
    myActionsDropdown = new CommonDropDownButton(new CommonAction("", AllIcons.Actions.More));
    initShowMoreDropdown();

    myCollapseButton = new CommonButton(COLLAPSE_ICON);
    myCollapseButton.setHorizontalTextPosition(SwingConstants.LEFT);
    myCollapseButton.addActionListener(actionEvent -> setCollapsed(myTrackList.isVisible()));
    setCollapsed(groupModel.isCollapsedInitially());

    JPanel toolbarPanel = new JPanel(new GridBagLayout());
    toolbarPanel.setBorder(JBUI.Borders.emptyRight(16));
    toolbarPanel.add(myFilterButton);
    toolbarPanel.add(myActionsDropdown);
    toolbarPanel.add(new FlatSeparator());
    toolbarPanel.add(myCollapseButton);

    myTitleLabel = new JLabel(groupModel.getTitle());
    myTitleLabel.setFont(TITLE_FONT);
    myTitleLabel.setBorder(JBUI.Borders.emptyLeft(16));

    JPanel titlePanel = new JPanel(new BorderLayout());
    titlePanel.setBorder(JBUI.Borders.customLine(StudioColorsKt.getBorder(), 1, 0, 1, 0));
    titlePanel.add(myTitleLabel, BorderLayout.WEST);
    titlePanel.add(toolbarPanel, BorderLayout.EAST);

    myComponent = new JPanel(new BorderLayout());
    myComponent.add(titlePanel, BorderLayout.NORTH);
    myComponent.add(myTrackList, BorderLayout.CENTER);
  }

  /**
   * @param collapsed set true to collapse the track group, false to expand it.
   */
  public void setCollapsed(boolean collapsed) {
    if (collapsed) {
      myTrackList.setVisible(false);
      myCollapseButton.setText("Expand Selection");
      myCollapseButton.setIcon(EXPAND_ICON);
    }
    else {
      myTrackList.setVisible(true);
      myCollapseButton.setText(null);
      myCollapseButton.setIcon(COLLAPSE_ICON);
    }
  }

  /**
   * @param mover a mover to enable moving up and down this track group in a list. Null to disable moving.
   * @return this instance
   */
  public TrackGroup setMover(@Nullable TrackGroupMover mover) {
    initShowMoreDropdown();
    if (mover != null) {
      myActionsDropdown.getAction().addChildrenActions(new CommonAction("Move Up", null, () -> mover.moveTrackGroupUp(this)));
      myActionsDropdown.getAction().addChildrenActions(new CommonAction("Move Down", null, () -> mover.moveTrackGroupDown(this)));
    }
    return this;
  }

  private void initShowMoreDropdown() {
    myActionsDropdown.getAction().clear();

    // Add children actions.
  }

  @VisibleForTesting
  protected JLabel getTitleLabel() {
    return myTitleLabel;
  }

  @VisibleForTesting
  protected DragAndDropList<TrackModel> getTrackList() {
    return myTrackList;
  }

  @VisibleForTesting
  protected CommonButton getCollapseButton() {
    return myCollapseButton;
  }

  /**
   * @return the UI component of this Track Group
   */
  @NotNull
  public JComponent getComponent() {
    return myComponent;
  }
}
