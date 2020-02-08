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

import com.android.tools.adtui.RangeTooltipComponent;
import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.TooltipView;
import com.android.tools.adtui.common.StudioColorsKt;
import com.android.tools.adtui.event.DelegateMouseEventHandler;
import com.android.tools.adtui.model.MultiSelectionModel;
import com.android.tools.adtui.model.TooltipModel;
import com.android.tools.adtui.model.ViewBinder;
import com.android.tools.adtui.model.trackgroup.SelectableTrackModel;
import com.android.tools.adtui.model.trackgroup.TrackGroupModel;
import com.android.tools.adtui.model.trackgroup.TrackModel;
import com.android.tools.adtui.util.SwingUtil;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.util.ui.MouseEventHandler;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Container for a list of track group list. Supports moving up/down a track group.
 */
public class TrackGroupListPanel implements TrackGroupMover {
  private final JPanel myPanel;
  private final JPanel myTooltipPanel;

  private final List<TrackGroup> myTrackGroups;
  private final TrackRendererFactory myTrackRendererFactory;
  private final ViewBinder<JComponent, TooltipModel, TooltipView> myTooltipBinder = new ViewBinder<>();

  @Nullable private TooltipModel myActiveTooltip;
  @Nullable private TooltipView myActiveTooltipView;
  @Nullable private RangeTooltipComponent myRangeTooltipComponent;

  public TrackGroupListPanel(@NotNull TrackRendererFactory trackRendererFactory) {
    // The panel should match Track's column sizing in order for tooltip component to only cover the content column.
    myPanel = new JPanel(new TabularLayout(Track.COL_SIZES, "Fit"));

    // A dedicated tooltip panel to display tooltip content.
    myTooltipPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    myTooltipPanel.setBackground(StudioColorsKt.getCanvasTooltipBackground());

    myTrackGroups = new ArrayList<>();
    myTrackRendererFactory = trackRendererFactory;
  }

  /**
   * Loads a list of {@link TrackGroupModel} for display without support for moving up and down each track group.
   *
   * @param trackGroupModels the models to display
   */
  public void loadTrackGroups(@NotNull List<TrackGroupModel> trackGroupModels) {
    loadTrackGroups(trackGroupModels, false);
  }

  /**
   * Loads a list of {@link TrackGroupModel} for display
   *
   * @param trackGroupModels       the models to display
   * @param enableTrackGroupMoving true to enable moving up and down each track group
   */
  public void loadTrackGroups(@NotNull List<TrackGroupModel> trackGroupModels, boolean enableTrackGroupMoving) {
    myTrackGroups.clear();
    trackGroupModels.forEach(
      model -> {
        TrackGroup trackGroup = new TrackGroup(model, myTrackRendererFactory).setMover(enableTrackGroupMoving ? this : null);
        myTrackGroups.add(trackGroup);

        // Register tooltip mouse adapter
        MouseAdapter adapter = new TrackGroupMouseEventHandler(trackGroup);
        trackGroup.getTrackList().addMouseListener(adapter);
        trackGroup.getTrackList().addMouseMotionListener(adapter);
        DelegateMouseEventHandler.delegateTo(getComponent())
          .installListenerOn(trackGroup.getTrackList())
          .installMotionListenerOn(trackGroup.getTrackList());
      });
    initTrackGroups();
  }

  @Override
  public void moveTrackGroupUp(@NotNull TrackGroup trackGroup) {
    int index = myTrackGroups.indexOf(trackGroup);
    assert index >= 0;
    if (index > 0) {
      myTrackGroups.remove(index);
      myTrackGroups.add(index - 1, trackGroup);
      initTrackGroups();
    }
  }

  @Override
  public void moveTrackGroupDown(@NotNull TrackGroup trackGroup) {
    int index = myTrackGroups.indexOf(trackGroup);
    assert index >= 0;
    if (index < myTrackGroups.size() - 1) {
      myTrackGroups.remove(index);
      myTrackGroups.add(index + 1, trackGroup);
      initTrackGroups();
    }
  }

  public <T extends SelectableTrackModel> void registerMultiSelectionModel(@NotNull MultiSelectionModel<T> multiSelectionModel) {
    myTrackGroups.forEach(
      trackGroup -> {
        if (trackGroup.getModel().isTrackSelectable()) {
          trackGroup.getTrackList().addListSelectionListener(new TrackGroupSelectionListener<>(trackGroup, multiSelectionModel));
        }
      }
    );
  }

  /**
   * Sets the range tooltip component that will overlay on top of all track groups. Setting it to null will clear it.
   */
  public void setRangeTooltipComponent(@Nullable RangeTooltipComponent rangeTooltipComponent) {
    if (myRangeTooltipComponent == rangeTooltipComponent) {
      return;
    }
    myRangeTooltipComponent = rangeTooltipComponent;
    if (rangeTooltipComponent != null) {
      rangeTooltipComponent.registerListenersOn(getComponent());
    }
  }

  /**
   * Enable/disable dragging and selection for all track groups.
   */
  public void setEnabled(boolean enabled) {
    myTrackGroups.forEach(trackGroup -> {
      trackGroup.getTrackList().setDragEnabled(enabled);
      trackGroup.getTrackList().setEnabled(enabled);
    });
  }

  @NotNull
  public JComponent getComponent() {
    return myPanel;
  }

  @NotNull
  public JPanel getTooltipPanel() {
    return myTooltipPanel;
  }

  @NotNull
  public ViewBinder<JComponent, TooltipModel, TooltipView> getTooltipBinder() {
    return myTooltipBinder;
  }

  @VisibleForTesting
  @NotNull
  public List<TrackGroup> getTrackGroups() {
    return myTrackGroups;
  }

  @VisibleForTesting
  @Nullable
  public TooltipModel getActiveTooltip() {
    return myActiveTooltip;
  }

  private void initTrackGroups() {
    myPanel.removeAll();
    if (myRangeTooltipComponent != null) {
      // Tooltip component is an overlay on top of the second column to align with the content of the tracks.
      // It occupies all rows to cover the track groups.
      // Illustration:
      // |Col 0  |Col 1           |
      //         +----------------+
      //         |TooltipComponent|
      // +-------|---------------+|
      // |[Title]|[Track Content]|| Row 0
      // |[Title]|[Track Content]|| Row 1
      // +-------|---------------+|
      //         +----------------+
      myPanel.add(myRangeTooltipComponent, new TabularLayout.Constraint(0, 1, myTrackGroups.size(), 1));
    }
    for (int i = 0; i < myTrackGroups.size(); ++i) {
      // Track groups occupy both columns regardless of whether we have a tooltip component.
      myPanel.add(myTrackGroups.get(i).getComponent(), new TabularLayout.Constraint(i, 0, 1, 2), i);
    }
    myPanel.revalidate();
  }

  private void setTooltip(TooltipModel tooltip) {
    if (tooltip != myActiveTooltip) {
      myActiveTooltip = tooltip;
      tooltipChanged();
    }
  }

  private void tooltipChanged() {
    if (myActiveTooltipView != null) {
      myActiveTooltipView.dispose();
      myActiveTooltipView = null;
    }
    myTooltipPanel.removeAll();
    myTooltipPanel.setVisible(false);

    if (myActiveTooltip != null) {
      myActiveTooltipView = myTooltipBinder.build(getComponent(), myActiveTooltip);
      myTooltipPanel.add(myActiveTooltipView.createComponent());
      myTooltipPanel.setVisible(true);
    }
    myTooltipPanel.invalidate();
    myTooltipPanel.repaint();
  }

  private static class TrackGroupSelectionListener<T extends SelectableTrackModel> implements ListSelectionListener {
    private final TrackGroup myTrackGroup;
    private final MultiSelectionModel<T> myMultiSelectionModel;
    private boolean handleListSelectionEvent = true;

    TrackGroupSelectionListener(@NotNull TrackGroup trackGroup, @NotNull MultiSelectionModel<T> multiSelectionModel) {
      myTrackGroup = trackGroup;
      myMultiSelectionModel = multiSelectionModel;

      // Subscribe to multi-selection changes as selection can be modified by other sources.
      // For example, multiple track groups share the same MultiSelectionModel in CPU capture stage. Selecting a track in Track Group A
      // should clear the track selection in Track Group B and vice versa.
      multiSelectionModel.addDependency(trackGroup).onChange(MultiSelectionModel.Aspect.CHANGE_SELECTION, () -> {
        // The logic here is for matching the selection state of the track groups to the multi-selection model when the that model is
        // modified by another source (e.g. trace event selection) and therefore we don't want to handle the ListSelectionEvent, which will
        // modify the multi-selection model again.
        handleListSelectionEvent = false;
        if (multiSelectionModel.isEmpty()) {
          trackGroup.getTrackList().clearSelection();
        }
        else if (!trackGroup.isEmpty()) {
          // Selection is changed and may come from another source.
          T selection = multiSelectionModel.getSelection().get(0);
          T listModel = (T)trackGroup.getTrackModelAt(0).getDataModel();
          // This may cause false positives if both selection are of the same type and isCompatibleWith() performs a type check only.
          if (!listModel.isCompatibleWith(selection)) {
            // Selection no longer contains this list model, update the list selection state to match that.
            trackGroup.getTrackList().clearSelection();
          }
        }
        handleListSelectionEvent = true;
      });
    }

    @Override
    public void valueChanged(ListSelectionEvent e) {
      if (handleListSelectionEvent) {
        Set<T> selection = new HashSet<>();
        for (int selectedIndex : myTrackGroup.getTrackList().getSelectedIndices()) {
          selection.add((T)myTrackGroup.getTrackModelAt(selectedIndex).getDataModel());
        }
        myMultiSelectionModel.setSelection(selection);
      }
    }
  }

  private class TrackGroupMouseEventHandler extends MouseEventHandler {
    @NotNull private final TrackGroup myTrackGroup;

    // Track index for the current mouse event.
    private int myTrackIndex = -1;

    private TrackGroupMouseEventHandler(@NotNull TrackGroup trackGroup) {
      myTrackGroup = trackGroup;
    }

    @Override
    protected void handle(MouseEvent event) {
      JList<?> trackList = myTrackGroup.getTrackList();
      int oldTrackIndex = myTrackIndex;
      myTrackIndex = trackList.locationToIndex(event.getPoint());
      TrackModel<?, ?> trackModel = myTrackGroup.getTrackModelAt(myTrackIndex);
      JComponent trackComponent = getTrackComponent(trackModel);

      // Find the origin location of the track (i.e. JList cell).
      Point trackOrigin = trackList.indexToLocation(myTrackIndex);
      // Manually translate the mouse point relative of the track origin.
      Point newPoint = event.getPoint();
      newPoint.translate(-trackOrigin.x, -trackOrigin.y);
      // Create a new mouse event with the translated location for the tooltip panel to show up at the correct location.
      // We may create another event based on this event to reuse the new location.
      MouseEvent newEvent = SwingUtil.convertMouseEventPoint(event, newPoint);
      // Forward the mouse event to the current track because the cell renderer doesn't construct a component hierarchy tree for the mouse
      // event to propagate.
      trackComponent.dispatchEvent(newEvent);

      if (event.getID() == MouseEvent.MOUSE_MOVED) {
        // If mouse moved between tracks, dispatch an additional MOUSE_EXITED event to the old track so that it can update its hover state.
        if (myTrackIndex != oldTrackIndex && oldTrackIndex >= 0) {
          trackList.repaint(trackList.getCellBounds(oldTrackIndex, oldTrackIndex));
          JComponent oldTrackComponent = getTrackComponent(myTrackGroup.getTrackModelAt(oldTrackIndex));
          oldTrackComponent.dispatchEvent(SwingUtil.convertMouseEventID(newEvent, MouseEvent.MOUSE_EXITED));
        }
      }
      else if (event.getID() == MouseEvent.MOUSE_EXITED) {
        // Reset track index so we know the next time mouse enters. Also ensure tooltip disappears when mouse exits.
        myTrackIndex = -1;
      }

      setTooltip(myTrackIndex == -1 ? null : trackModel.getActiveTooltipModel());
    }

    private JComponent getTrackComponent(TrackModel<?, ?> trackModel) {
      assert myTrackGroup.getTrackMap().containsKey(trackModel.getId());
      return myTrackGroup.getTrackMap().get(trackModel.getId()).getComponent();
    }
  }
}
