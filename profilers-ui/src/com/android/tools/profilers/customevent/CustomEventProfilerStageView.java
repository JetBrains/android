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
package com.android.tools.profilers.customevent;


import static com.android.tools.profilers.ProfilerLayout.createToolbarLayout;

import com.android.tools.adtui.DragAndDropList;
import com.android.tools.adtui.model.trackgroup.TrackGroupListModel;
import com.android.tools.adtui.model.trackgroup.TrackGroupModel;
import com.android.tools.profilers.StageView;
import com.android.tools.profilers.StudioProfilersView;
import com.google.common.annotations.VisibleForTesting;
import java.awt.BorderLayout;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

/**
 * This class represents the view of all the custom events that users have chosen to track for Custom Event Visualization.
 */
public class CustomEventProfilerStageView extends StageView<CustomEventProfilerStage> {

  @NotNull
  private final JList<TrackGroupModel> myTrackGroupList;

  public CustomEventProfilerStageView(@NotNull StudioProfilersView profilersView, @NotNull CustomEventProfilerStage stage) {
    super(profilersView, stage);

    //TODO: add the track group list into the view
    myTrackGroupList = createTrackGroups(stage.getTrackGroupListModel());
  }

  @Override
  public JComponent getToolbar() {
    // Currently an empty toolbar
    JPanel toolBar = new JPanel(createToolbarLayout());
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(toolBar, BorderLayout.WEST);
    return panel;
  }

  /**
   * Creates the JList containing all the track groups in the stage.
   */
  private static JList<TrackGroupModel> createTrackGroups(@NotNull TrackGroupListModel trackGroupListModel) {
    DragAndDropList<TrackGroupModel> trackGroupList = new DragAndDropList<>(trackGroupListModel);
    return trackGroupList;
  }

  @VisibleForTesting
  @NotNull
  protected final JList<TrackGroupModel> getTrackGroupList() {
    return myTrackGroupList;
  }
}
