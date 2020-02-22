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

import com.android.tools.adtui.BoxSelectionComponent;
import com.android.tools.adtui.DragAndDropList;
import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.common.StudioColorsKt;
import com.android.tools.adtui.event.DelegateMouseEventHandler;
import com.android.tools.adtui.flat.FlatSeparator;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.stdui.CommonAction;
import com.android.tools.adtui.model.trackgroup.TrackGroupModel;
import com.android.tools.adtui.model.trackgroup.TrackModel;
import com.android.tools.adtui.stdui.CommonButton;
import com.android.tools.adtui.stdui.menu.CommonDropDownButton;
import com.android.tools.adtui.util.SwingUtil;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.icons.AllIcons;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.MouseEventHandler;
import icons.StudioIcons;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.Map;
import javax.swing.AbstractAction;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A collapsible UI component that contains a list of {@link Track}s to visualize multiple horizontal data series.
 */
public class TrackGroup extends AspectObserver {
  private static final Icon EXPAND_ICON = AllIcons.Actions.FindAndShowNextMatches;
  private static final Icon COLLAPSE_ICON = AllIcons.Actions.FindAndShowPrevMatches;
  private static final Font TITLE_FONT = AdtUiUtils.DEFAULT_FONT.biggerOn(5f);
  private static final String TOGGLE_EXPAND_COLLAPSE_TRACK_KEY = "TOGGLE_EXPAND_COLLAPSE_KEY";

  private final TrackGroupModel myModel;

  private final JPanel myComponent;
  private final JLabel myTitleLabel;
  private final JLabel myTitleInfoIcon;
  private final JPanel myOverlay;
  private final DragAndDropList<TrackModel> myTrackList;
  private final CommonDropDownButton myActionsDropdown;
  private final FlatSeparator mySeparator = new FlatSeparator();
  private final CommonButton myCollapseButton;
  private final Map<Integer, Track> myTrackMap;
  private final AspectObserver myObserver = new AspectObserver();

  /**
   * @param groupModel      {@link TrackGroup} data model
   * @param rendererFactory factory for instantiating {@link TrackRenderer}s
   */
  public TrackGroup(@NotNull TrackGroupModel groupModel, @NotNull TrackRendererFactory rendererFactory) {
    myModel = groupModel;

    // Caches Tracks for the list cell renderer.
    myTrackMap = new HashMap<>();

    // Initializes UI components.
    myTrackList = new DragAndDropList<>(groupModel);
    myTrackList.setCellRenderer(new ListCellRenderer<TrackModel>() {
      @Override
      public Component getListCellRendererComponent(JList<? extends TrackModel> list,
                                                    TrackModel value,
                                                    int index,
                                                    boolean isSelected,
                                                    boolean cellHasFocus) {
        return myTrackMap
          .computeIfAbsent(value.getId(), id -> {
            TrackRenderer<?, ?> renderer = rendererFactory.createRenderer(value.getRendererType());
            // When track's collapse state changes, render the track again.
            value.getAspectModel().addDependency(myObserver).onChange(TrackModel.Aspect.COLLAPSE_CHANGE, () -> {
              myTrackMap.put(id, Track.create(value, renderer));
            });
            return Track.create(value, renderer);
          })
          .updateSelected(groupModel.isTrackSelectable() && isSelected)
          .getComponent();
      }
    });

    myActionsDropdown = new CommonDropDownButton(new CommonAction("", AllIcons.Actions.More));
    myActionsDropdown.setToolTipText("More actions");
    initShowMoreDropdown();

    myCollapseButton = new CommonButton(COLLAPSE_ICON);
    myCollapseButton.setHorizontalTextPosition(SwingConstants.LEFT);
    myCollapseButton.addActionListener(actionEvent -> setCollapsed(myTrackList.isVisible()));
    setCollapsed(groupModel.isCollapsedInitially());

    JPanel toolbarPanel = new JPanel(new GridBagLayout());
    toolbarPanel.setBorder(JBUI.Borders.emptyRight(16));
    toolbarPanel.add(myActionsDropdown);
    toolbarPanel.add(mySeparator);
    toolbarPanel.add(myCollapseButton);

    myTitleLabel = new JLabel(groupModel.getTitle());
    myTitleLabel.setFont(TITLE_FONT);
    myTitleLabel.setBorder(JBUI.Borders.emptyLeft(16));
    myTitleInfoIcon = new JLabel(StudioIcons.Common.INFO);
    myTitleInfoIcon.setVisible(groupModel.getTitleInfo() != null);
    myTitleInfoIcon.setToolTipText(groupModel.getTitleInfo());

    JPanel titlePanel = new JPanel(new BorderLayout());
    titlePanel.setBorder(JBUI.Borders.customLine(StudioColorsKt.getBorder(), 1, 0, 1, 0));
    if (!groupModel.getHideHeader()) {
      JPanel westTitlePanel = new JPanel();
      westTitlePanel.add(myTitleLabel);
      westTitlePanel.add(myTitleInfoIcon);
      titlePanel.add(westTitlePanel, BorderLayout.WEST);
    }
    titlePanel.add(toolbarPanel, BorderLayout.EAST);

    // A panel responsible for forwarding mouse events to the tracks.
    myOverlay = new JPanel();
    myOverlay.setOpaque(false);
    MouseEventHandler mouseEventHandler = new TrackGroupMouseEventHandler();
    myOverlay.addMouseListener(mouseEventHandler);
    myOverlay.addMouseMotionListener(mouseEventHandler);

    myComponent = new JPanel(new TabularLayout(Track.COL_SIZES, "Fit,Fit"));
    if (groupModel.getRangeSelectionModel() != null) {
      BoxSelectionComponent boxSelection = new BoxSelectionComponent(groupModel.getRangeSelectionModel(), myTrackList);
      DelegateMouseEventHandler.delegateTo(myOverlay)
        .installListenerOn(boxSelection)
        .installMotionListenerOn(boxSelection);
      myComponent.add(boxSelection, new TabularLayout.Constraint(1, 1));
    }
    myComponent.add(myOverlay, new TabularLayout.Constraint(1, 1));
    myComponent.add(titlePanel, new TabularLayout.Constraint(0, 0, 1, 2));
    myComponent.add(myTrackList, new TabularLayout.Constraint(1, 0, 1, 2));

    initKeyBindings(myTrackList);
  }

  @NotNull
  public TrackGroupModel getModel() {
    return myModel;
  }

  /**
   * @param collapsed set true to collapse the track group, false to expand it.
   */
  public void setCollapsed(boolean collapsed) {
    if (collapsed) {
      myTrackList.setVisible(false);
      mySeparator.setVisible(false);
      myActionsDropdown.setVisible(false);
      myCollapseButton.setText("Expand Section");
      myCollapseButton.setIcon(EXPAND_ICON);
    }
    else {
      myTrackList.setVisible(true);
      mySeparator.setVisible(false);
      myActionsDropdown.setVisible(true);
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

  /**
   * @return the underlying JList that displays all tracks.
   */
  @NotNull
  public DragAndDropList<TrackModel> getTrackList() {
    return myTrackList;
  }

  /**
   * @return true if there are no tracks in this group.
   */
  public boolean isEmpty() {
    return myTrackList.getModel().getSize() == 0;
  }

  /**
   * Returns the track model at the specified index.
   *
   * @param index the requested index
   * @return the track model at <code>index</code>
   */
  public TrackModel getTrackModelAt(int index) {
    return myTrackList.getModel().getElementAt(index);
  }

  /**
   * @return a mapping from track ID to a track.
   */
  @NotNull
  public Map<Integer, Track> getTrackMap() {
    return myTrackMap;
  }

  private void initShowMoreDropdown() {
    myActionsDropdown.getAction().clear();

    // Add children actions.
  }

  private void initKeyBindings(@NotNull JList list) {
    list.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), TOGGLE_EXPAND_COLLAPSE_TRACK_KEY);
    list.getActionMap().put(TOGGLE_EXPAND_COLLAPSE_TRACK_KEY, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        for (int selectedIndex : list.getSelectedIndices()) {
          TrackModel model = myModel.get(selectedIndex);
          if (model.isCollapsible()) {
            model.setCollapsed(!model.isCollapsed());
          }
        }
      }
    });
  }

  @VisibleForTesting
  JLabel getTitleLabel() {
    return myTitleLabel;
  }

  @VisibleForTesting
  JLabel getTitleInfoIcon() {
    return myTitleInfoIcon;
  }

  @VisibleForTesting
  CommonDropDownButton getActionsDropdown() {
    return myActionsDropdown;
  }

  @VisibleForTesting
  FlatSeparator getSeparator() {
    return mySeparator;
  }

  @VisibleForTesting
  CommonButton getCollapseButton() {
    return myCollapseButton;
  }

  /**
   * @return the UI component of this Track Group
   */
  @NotNull
  public JComponent getComponent() {
    return myComponent;
  }

  @NotNull
  public JPanel getOverlay() {
    return myOverlay;
  }

  private class TrackGroupMouseEventHandler extends MouseEventHandler {
    // Track index for the current mouse event.
    private int myTrackIndex = -1;

    @Override
    protected void handle(MouseEvent event) {
      int oldTrackIndex = myTrackIndex;
      myTrackIndex = myTrackList.locationToIndex(event.getPoint());

      // Find the origin location of the track (i.e. JList cell).
      Point trackOrigin = myTrackList.indexToLocation(myTrackIndex);
      // Manually translate the mouse point relative of the track origin.
      Point newPoint = event.getPoint();
      newPoint.translate(0, -trackOrigin.y);
      // Create a new mouse event with the translated location for the tooltip panel to show up at the correct location.
      // We may create another event based on this event to reuse the new location.
      MouseEvent newEvent = SwingUtil.convertMouseEventPoint(event, newPoint);
      // Forward the mouse event to the current track because the cell renderer doesn't construct a component hierarchy tree for the mouse
      // event to propagate.
      JComponent trackContent = getTrackMap().get(getTrackModelAt(myTrackIndex).getId()).getTrackContent();
      trackContent.dispatchEvent(newEvent);

      // If mouse moved between tracks, dispatch an additional MOUSE_EXITED event to the old track so that it can update its hover state.
      if (event.getID() == MouseEvent.MOUSE_MOVED) {
        if (myTrackIndex != oldTrackIndex && oldTrackIndex >= 0) {
          myTrackList.repaint(myTrackList.getCellBounds(oldTrackIndex, oldTrackIndex));
          JComponent oldTrackContent = getTrackMap().get(getTrackModelAt(oldTrackIndex).getId()).getTrackContent();
          oldTrackContent.dispatchEvent(SwingUtil.convertMouseEventID(newEvent, MouseEvent.MOUSE_EXITED));
        }
      } else if (event.getID() == MouseEvent.MOUSE_EXITED) {
        // Reset track index so we know the next time mouse enters.
        myTrackIndex = -1;
      }
    }
  }
}
