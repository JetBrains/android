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
import com.google.common.annotations.VisibleForTesting;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import kotlin.Pair;
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
      model -> myTrackGroups.add(new TrackGroup(model, myTrackRendererFactory).setMover(enableTrackGroupMoving ? this : null)));
    registerTooltipListeners();
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
    myRangeTooltipComponent = rangeTooltipComponent;
    registerTooltipListeners();
  }

  /**
   * Enable/disable dragging and selection for all track groups.
   */
  public void setEnabled(boolean enabled) {
    myTrackGroups.forEach(trackGroup -> trackGroup.setEventHandlersEnabled(enabled));
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
      JComponent trackGroupComponent = myTrackGroups.get(i).getComponent();
      myPanel.add(trackGroupComponent, new TabularLayout.Constraint(i, 0, 1, 2), i);
      // Delegate events to the track group component so SPACE + dragging works on it.
      DelegateMouseEventHandler.delegateTo(myPanel)
        .installListenerOn(trackGroupComponent)
        .installMotionListenerOn(trackGroupComponent)
        .installMouseWheelListenerOn(trackGroupComponent);
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

  private void registerTooltipListeners() {
    for (TrackGroup trackGroup : myTrackGroups) {
      MouseAdapter adapter = new TooltipMouseAdapter(trackGroup);
      trackGroup.getOverlay().addMouseListener(adapter);
      trackGroup.getOverlay().addMouseMotionListener(adapter);
      if (myRangeTooltipComponent != null) {
        myRangeTooltipComponent.registerListenersOn(trackGroup.getOverlay());
      }
    }
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
      Iterable<Map.Entry<Object, Set<Object>>> selections =
        myTrackGroup.getModel().select(Arrays.stream(myTrackGroup.getTrackList().getSelectedIndices())
                                         .mapToObj(i -> (TrackModel<Object, ?>)myTrackGroup.getTrackModelAt(i))
                                         .collect(Collectors.toSet()));
      selections.forEach(s -> myMultiSelectionModel.setSelection(s.getKey(), (Set<T>)s.getValue()));
    }
  }

  private class TooltipMouseAdapter extends MouseAdapter {
    @NotNull private final TrackGroup myTrackGroup;

    private TooltipMouseAdapter(@NotNull TrackGroup trackGroup) {
      myTrackGroup = trackGroup;
    }

    @Override
    public void mouseMoved(MouseEvent event) {
      updateTooltipOnMove(event);
    }

    @Override
    public void mouseEntered(MouseEvent event) {
      updateTooltipOnMove(event);
    }

    @Override
    public void mouseExited(MouseEvent event) {
      setTooltip(null);
    }

    private void updateTooltipOnMove(MouseEvent event) {
      int trackIndex = myTrackGroup.getTrackList().locationToIndex(event.getPoint());
      setTooltip(trackIndex == -1 ? null : myTrackGroup.getTrackModelAt(trackIndex).getActiveTooltipModel());
    }
  }
}
