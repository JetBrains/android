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

import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.common.StudioColorsKt;
import com.android.tools.adtui.model.trackgroup.TrackModel;
import com.intellij.util.ui.JBEmptyBorder;
import com.intellij.util.ui.MouseEventHandler;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import org.jetbrains.annotations.NotNull;

/**
 * Represents horizontal data visualization (e.g. time-based data series), used in a {@link TrackGroup}.
 */
public class Track {
  /**
   * The first column displays the title header. The second column displays the track content.
   */
  private static final int DEFAULT_TITLE_COL_PX = 150;
  public static final String COL_SIZES = DEFAULT_TITLE_COL_PX + "px,*";

  private final JPanel myComponent;

  private Track(@NotNull TrackModel trackModel, @NotNull JComponent trackContent) {
    JLabel titleLabel = new JLabel(trackModel.getTitle());
    titleLabel.setBorder(new JBEmptyBorder(4, 36, 4, 0));
    titleLabel.setVerticalAlignment(SwingConstants.TOP);

    trackContent.setBorder(new JBEmptyBorder(4, 0, 4, 0));

    myComponent = new JPanel(new TabularLayout(COL_SIZES, "Fit"));
    if (trackModel.getHideHeader()) {
      myComponent.add(trackContent, new TabularLayout.Constraint(0, 0, 2));
      MouseAdapter adapter = new TrackMouseEventHandler(trackContent, 0, 0);
      myComponent.addMouseMotionListener(adapter);
    }
    else {
      myComponent.add(titleLabel, new TabularLayout.Constraint(0, 0));
      myComponent.add(trackContent, new TabularLayout.Constraint(0, 1));
      // Offsets mouse event using width of the title column.
      MouseAdapter adapter = new TrackMouseEventHandler(trackContent, -DEFAULT_TITLE_COL_PX, 0);
      myComponent.addMouseMotionListener(adapter);
    }
  }

  /**
   * Factory method to instantiate a Track.
   *
   * @param <M>           data model type
   * @param <R>           renderer enum type
   * @param trackModel    data model
   * @param trackRenderer UI renderer
   * @return a Track that visualizes the given {@link TrackModel} using the provided {@link TrackRenderer}
   */
  @NotNull
  public static <M, R extends Enum> Track create(@NotNull TrackModel<M, R> trackModel, @NotNull TrackRenderer<M, R> trackRenderer) {
    return new Track(trackModel, trackRenderer.render(trackModel));
  }

  /**
   * Update UI to reflect selection state.
   *
   * @return current instance
   */
  @NotNull
  public Track updateSelected(boolean selected) {
    myComponent.setBackground(selected ? StudioColorsKt.getActiveSelection() : null);
    return this;
  }

  /**
   * @return the UI component of this Track.
   */
  @NotNull
  public JComponent getComponent() {
    return myComponent;
  }

  /**
   * Mouse adapter for dispatching mouse events to the track content. Translates the mouse point using provided offsets.
   */
  private static class TrackMouseEventHandler extends MouseEventHandler {
    @NotNull private final JComponent myTrackContent;
    private final int myXOffset;
    private final int myYOffset;

    TrackMouseEventHandler(@NotNull JComponent trackContent, int XOffset, int yOffset) {
      myTrackContent = trackContent;
      myXOffset = XOffset;
      myYOffset = yOffset;
    }

    @Override
    protected void handle(MouseEvent event) {
      event.translatePoint(myXOffset, myYOffset);
      myTrackContent.dispatchEvent(event);
    }
  }
}
