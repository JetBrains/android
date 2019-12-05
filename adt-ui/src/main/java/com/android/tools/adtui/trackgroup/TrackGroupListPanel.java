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
import java.util.List;
import javax.swing.JComponent;
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

  public <T> void registerMultiSelectionModel(@NotNull MultiSelectionModel<T> multiSelectionModel) {
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

  private static class TrackGroupSelectionListener<T> implements ListSelectionListener {
    private final TrackGroup myTrackGroup;
    private final MultiSelectionModel<T> myMultiSelectionModel;

    TrackGroupSelectionListener(@NotNull TrackGroup trackGroup, @NotNull MultiSelectionModel<T> multiSelectionModel) {
      myTrackGroup = trackGroup;
      myMultiSelectionModel = multiSelectionModel;
    }

    @Override
    public void valueChanged(ListSelectionEvent e) {
      for (int i = e.getFirstIndex(); i <= e.getLastIndex(); ++i) {
        T selectedModel = (T)myTrackGroup.getTrackList().getModel().getElementAt(i).getDataModel();
        if (myTrackGroup.getTrackList().isSelectedIndex(i)) {
          myMultiSelectionModel.addToSelection(selectedModel);
        }
        else {
          myMultiSelectionModel.deselect(selectedModel);
        }
      }
    }
  }

  private class TrackGroupMouseEventHandler extends MouseEventHandler {
    @NotNull private final TrackGroup myTrackGroup;

    private TrackGroupMouseEventHandler(@NotNull TrackGroup trackGroup) {
      myTrackGroup = trackGroup;
    }

    @Override
    protected void handle(MouseEvent event) {
      int trackIndex = myTrackGroup.getTrackList().locationToIndex(event.getPoint());

      TrackModel trackModel = myTrackGroup.getTrackList().getModel().getElementAt(trackIndex);
      assert myTrackGroup.getTrackMap().containsKey(trackModel.getId());
      // Find the origin location of the track (i.e. JList cell).
      Point trackOrigin = myTrackGroup.getTrackList().indexToLocation(trackIndex);
      // Manually translate the mouse point relative of the track origin.
      Point newPoint = event.getPoint();
      newPoint.translate(-trackOrigin.x, -trackOrigin.y);
      // Forward the mouse event to a specific track because the cell renderer doesn't construct a component hierarchy tree for the mouse
      // event to propagate.
      // We create a new mouse event so that the tooltip panel can still show up at the correct location.
      myTrackGroup.getTrackMap().get(trackModel.getId()).getComponent().dispatchEvent(SwingUtil.convertMouseEventPoint(event, newPoint));

      setTooltip(trackIndex == -1 ? null : trackModel.getActiveTooltipModel());
    }
  }
}
