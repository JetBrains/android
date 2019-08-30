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
import com.android.tools.adtui.model.trackgroup.TrackModel;
import com.intellij.util.ui.JBEmptyBorder;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import org.jetbrains.annotations.NotNull;

/**
 * Represents horizontal data visualization (e.g. time-based data series), used in a {@link TrackGroup}.
 */
public class Track {
  private final JPanel myComponent;

  private Track(@NotNull TrackModel trackModel, @NotNull JComponent trackComponent) {
    JLabel titleLabel = new JLabel(trackModel.getTitle());
    titleLabel.setBorder(new JBEmptyBorder(4, 36, 4, 0));
    titleLabel.setVerticalAlignment(SwingConstants.TOP);

    trackComponent.setBorder(new JBEmptyBorder(4, 0, 4, 0));

    myComponent = new JPanel(new TabularLayout("150px,*", "Fit"));
    myComponent.add(titleLabel, new TabularLayout.Constraint(0, 0));
    myComponent.add(trackComponent, new TabularLayout.Constraint(0, 1));
  }

  /**
   * Factory method to instantiate a Track.
   *
   * @param trackModel    data model
   * @param trackRenderer UI renderer
   * @param <M>           data model type
   * @param <R>           renderer enum type
   * @return a Track that visualizes the given {@link TrackModel} using the provided {@link TrackRenderer}
   */
  @NotNull
  public static <M, R extends Enum> Track create(@NotNull TrackModel<M, R> trackModel, @NotNull TrackRenderer<M, R> trackRenderer) {
    return new Track(trackModel, trackRenderer.render(trackModel));
  }

  /**
   * @return the UI component of this Track.
   */
  @NotNull
  public JComponent getComponent() {
    return myComponent;
  }
}
