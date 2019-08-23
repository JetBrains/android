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
import com.android.tools.adtui.model.trackgroup.TrackGroupModel;
import com.android.tools.adtui.model.trackgroup.TrackModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import org.jetbrains.annotations.NotNull;

/**
 * A collapsible UI component that contains a list of {@link Track}s to visualize multiple horizontal data series.
 */
public class TrackGroup {
  private final JPanel myComponent;

  /**
   * @param groupModel      {@link TrackGroup} data model
   * @param rendererFactory factory for instantiating {@link TrackRenderer}s
   */
  public TrackGroup(@NotNull TrackGroupModel groupModel, @NotNull TrackRendererFactory rendererFactory) {
    // Caches Tracks for the list cell renderer.
    Map<Integer, Track> trackModelToComponentMap = new HashMap<>();

    // Initializes UI components.
    DragAndDropList<TrackModel> trackList = new DragAndDropList<>(groupModel);
    trackList.setCellRenderer(new ListCellRenderer<TrackModel>() {
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

    myComponent = new JPanel(new BorderLayout());
    myComponent.add(new JLabel(groupModel.getTitle()), BorderLayout.NORTH);
    myComponent.add(trackList, BorderLayout.CENTER);
  }

  /**
   * @return the UI component of this Track Group
   */
  @NotNull
  public JComponent getComponent() {
    return myComponent;
  }
}
