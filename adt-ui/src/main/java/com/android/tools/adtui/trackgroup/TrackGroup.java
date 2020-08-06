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
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.HelpTooltip;
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

  /**
   * A column sizing that takes the reorder icon into account to allow mouse events to pass through.
   */
  private static final String COL_SIZES =
    Track.REORDER_ICON.getIconWidth() + "px," + (Track.DEFAULT_TITLE_COL_PX - Track.REORDER_ICON.getIconWidth()) + "px,*";

  private final TrackGroupModel myModel;

  private final JPanel myComponent;
  private final JLabel myTitleLabel;
  private final JLabel myTitleInfoIcon;
  private final JPanel myOverlay = new JPanel();
  private final JPanel myTrackTitleOverlay = new JPanel();
  private final DragAndDropList<TrackModel> myTrackList;
  private final CommonDropDownButton myActionsDropdown;
  private final FlatSeparator mySeparator = new FlatSeparator();
  private final CommonButton myCollapseButton;
  private final Map<Integer, Track> myTrackMap;
  private final AspectObserver myObserver = new AspectObserver();

  @Nullable
  private final BoxSelectionComponent myBoxSelectionComponent;

  private boolean myIsEnabled = true;

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
    myTitleInfoIcon = new JLabel(StudioIcons.Common.HELP);
    myTitleInfoIcon.setVisible(groupModel.getTitleHelpText() != null);
    if (groupModel.getTitleHelpText() != null) {
      HelpTooltip helpTooltip = new HelpTooltip().setDescription(groupModel.getTitleHelpText());
      if (groupModel.getTitleHelpLinkUrl() != null) {
        helpTooltip.setLink(groupModel.getTitleHelpLinkText(), () -> BrowserUtil.browse(groupModel.getTitleHelpLinkUrl()));
      }
      helpTooltip.installOn(myTitleInfoIcon);
    }

    JPanel titlePanel = new JPanel(new BorderLayout());
    titlePanel.setBorder(JBUI.Borders.customLine(StudioColorsKt.getBorder(), 1, 0, 1, 0));
    if (!groupModel.getHideHeader()) {
      JPanel westTitlePanel = new JPanel();
      westTitlePanel.add(myTitleLabel);
      westTitlePanel.add(myTitleInfoIcon);
      titlePanel.add(westTitlePanel, BorderLayout.WEST);
    }
    titlePanel.add(toolbarPanel, BorderLayout.EAST);

    // A panel responsible for forwarding mouse events to the tracks' content component.
    myOverlay.setOpaque(false);
    MouseEventHandler trackContentMouseEventHandler = new TrackContentMouseEventHandler();
    myOverlay.addMouseListener(trackContentMouseEventHandler);
    myOverlay.addMouseMotionListener(trackContentMouseEventHandler);

    // A panel responsible for forwarding mouse events to the tracks' title component.
    myTrackTitleOverlay.setOpaque(false);
    MouseEventHandler trackTitleMouseEventHandler = new TrackTitleMouseEventHandler();
    myTrackTitleOverlay.addMouseListener(trackTitleMouseEventHandler);
    myTrackTitleOverlay.addMouseMotionListener(trackTitleMouseEventHandler);

    myComponent = new JPanel(new TabularLayout(COL_SIZES));
    if (groupModel.getBoxSelectionModel() != null) {
      myBoxSelectionComponent = new BoxSelectionComponent(groupModel.getBoxSelectionModel(), myTrackList);
      DelegateMouseEventHandler.delegateTo(myOverlay)
        .installListenerOn(myBoxSelectionComponent)
        .installMotionListenerOn(myBoxSelectionComponent);
      myComponent.add(myBoxSelectionComponent, new TabularLayout.Constraint(1, 2));
    }
    else {
      myBoxSelectionComponent = null;
    }
    // +-----------------------------+
    // |title panel                  |
    // |-+-------------+-------------+
    // |=|>track title |track content|
    // |=|>track title |track content|
    // |=+-------------+-------------+
    //   |title overlay|overlay      |
    myComponent.add(myTrackTitleOverlay, new TabularLayout.Constraint(1, 1));
    myComponent.add(myOverlay, new TabularLayout.Constraint(1, 2));
    myComponent.add(titlePanel, new TabularLayout.Constraint(0, 0, 1, 3));
    myComponent.add(myTrackList, new TabularLayout.Constraint(1, 0, 1, 3));

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
      myOverlay.setVisible(false);
      myTrackTitleOverlay.setVisible(false);
      mySeparator.setVisible(false);
      myActionsDropdown.setVisible(false);
      myCollapseButton.setText("Expand Section");
      myCollapseButton.setIcon(EXPAND_ICON);
      getModel().getActionListeners().forEach(listener -> listener.onGroupCollapsed(getModel().getTitle()));
    }
    else {
      myTrackList.setVisible(true);
      myOverlay.setVisible(true);
      myTrackTitleOverlay.setVisible(true);
      mySeparator.setVisible(true);
      myActionsDropdown.setVisible(true);
      myCollapseButton.setText(null);
      myCollapseButton.setIcon(COLLAPSE_ICON);
      getModel().getActionListeners().forEach(listener -> listener.onGroupExpanded(getModel().getTitle()));
    }
  }

  /**
   * @param mover a mover to enable moving up and down this track group in a list. Null to disable moving.
   * @return this instance
   */
  public TrackGroup setMover(@Nullable TrackGroupMover mover) {
    initShowMoreDropdown();
    if (mover != null) {
      myActionsDropdown.getAction().addChildrenActions(new CommonAction("Move Up", null, () -> {
        mover.moveTrackGroupUp(this);
        getModel().getActionListeners().forEach(listener -> listener.onGroupMovedUp(getModel().getTitle()));
      }));
      myActionsDropdown.getAction().addChildrenActions(new CommonAction("Move Down", null, () -> {
        mover.moveTrackGroupDown(this);
        getModel().getActionListeners().forEach(listener -> listener.onGroupMovedDown(getModel().getTitle()));
      }));
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

  /**
   * Enable/disable mouse event handlers.
   */
  public void setEventHandlersEnabled(boolean enabled) {
    myIsEnabled = enabled;
    myTrackList.setEnabled(enabled);
    myTrackList.setDragEnabled(enabled);
    if (myBoxSelectionComponent != null) {
      myBoxSelectionComponent.setEventHandlersEnabled(enabled);
    }
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

  @VisibleForTesting
  JComponent getTrackTitleOverlay() {
    return myTrackTitleOverlay;
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

  /**
   * Mouse adapter for forwarding events to the underlying track content.
   */
  private class TrackContentMouseEventHandler extends MouseEventHandler {
    // Track index for the current mouse event.
    private int myTrackIndex = -1;

    @Override
    protected void handle(MouseEvent event) {
      if (!myIsEnabled) {
        // Dispatch event to the root component so that it can forward to the parent component.
        myComponent.dispatchEvent(event);
        return;
      }

      int oldTrackIndex = myTrackIndex;
      myTrackIndex = myTrackList.locationToIndex(event.getPoint());

      // Forward the mouse event to the current track because the cell renderer doesn't construct a component hierarchy tree for the mouse
      // event to propagate.
      MouseEvent newEvent = convertTrackListEvent(event, myTrackIndex);
      JComponent trackContent = getTrackMap().get(getTrackModelAt(myTrackIndex).getId()).getTrackContent();
      trackContent.dispatchEvent(newEvent);

      // If mouse moved between tracks, dispatch an additional MOUSE_EXITED event to the old track so that it can update its hover state.
      if (event.getID() == MouseEvent.MOUSE_MOVED) {
        if (myTrackIndex != oldTrackIndex && oldTrackIndex >= 0) {
          myTrackList.repaint(myTrackList.getCellBounds(oldTrackIndex, oldTrackIndex));
          JComponent oldTrackContent = getTrackMap().get(getTrackModelAt(oldTrackIndex).getId()).getTrackContent();
          oldTrackContent.dispatchEvent(SwingUtil.convertMouseEventID(newEvent, MouseEvent.MOUSE_EXITED));
        }
      }
      else if (event.getID() == MouseEvent.MOUSE_EXITED) {
        // Reset track index so we know the next time mouse enters.
        myTrackIndex = -1;
      }
    }
  }

  /**
   * Mouse adapter for forwarding the event to the underlying track title and the this track group itself.
   */
  private class TrackTitleMouseEventHandler extends MouseEventHandler {
    @Override
    protected void handle(MouseEvent event) {
      // Forward the mouse event to the current track because the cell renderer doesn't construct a component hierarchy tree for the mouse
      // event to propagate.
      int trackIndex = myTrackList.locationToIndex(event.getPoint());
      MouseEvent newEvent = convertTrackListEvent(event, trackIndex);
      JComponent trackTitle = getTrackMap().get(getTrackModelAt(trackIndex).getId()).getTitleLabel();
      trackTitle.dispatchEvent(newEvent);

      // Fall through for the track list itself.
      myTrackList.dispatchEvent(event);
    }

    @Override
    public void mousePressed(MouseEvent event) {
      // Clear box selection if users manually clicks on the track title to select a track.
      // This ensures that box selection doesn't carry over when user switches tracks. For this to work, it should be done before the normal
      // selection logic kicks in.
      if (myBoxSelectionComponent != null && myBoxSelectionComponent.getModel().isSelectionEnabled()) {
        myBoxSelectionComponent.clearSelection();
      }
      super.mousePressed(event);
    }
  }

  private MouseEvent convertTrackListEvent(MouseEvent oldEvent, int trackIndex) {
    // Find the origin location of the track (i.e. JList cell).
    Point trackOrigin = myTrackList.indexToLocation(trackIndex);
    // Manually translate the mouse point relative of the track origin.
    Point newPoint = oldEvent.getPoint();
    newPoint.translate(0, -trackOrigin.y);
    // Create a new mouse event with the translated location for the tooltip panel to show up at the correct location.
    // We may create another event based on this event to reuse the new location.
    return SwingUtil.convertMouseEventPoint(oldEvent, newPoint);
  }
}
