/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers.sessions;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * A {@link JList} for Sessions that handle selection/click events via mouse interaction manually. A traditional {@link JList} does not
 * propagate events into components inside the cell, because the cell contents are painted as a flat image. To support custom component
 * interactions such as clicking on a expand/collapse button, this class manually routes the events to the proper component within the cell.
 */
final class SessionsList extends JList<SessionArtifact> {

  static final int INVALID_INDEX = -1;

  @NotNull private final SessionsCellRenderer myCellRenderer;

  public SessionsList(@NotNull ListModel<SessionArtifact> model) {
    super(model);
    myCellRenderer = new SessionsCellRenderer(this);
    setCellRenderer(myCellRenderer);

    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(@NotNull MouseEvent event) {
        setCurrentIndex(event);
        myCellRenderer.handleClick(event);
      }

      @Override
      public void mouseEntered(MouseEvent event) {
        setCurrentIndex(event);
      }

      @Override
      public void mouseExited(MouseEvent event) {
        setCurrentIndex(event);
      }
    });

    addMouseMotionListener(new MouseAdapter() {
      @Override
      public void mouseDragged(@NotNull MouseEvent event) {
        setCurrentIndex(event);
      }

      @Override
      public void mouseMoved(@NotNull MouseEvent event) {
        setCurrentIndex(event);
      }
    });
  }

  private void setCurrentIndex(@NotNull MouseEvent event) {
    int index = locationToIndex(event.getPoint());

    // locationToIndex returns to closest index, we have to check if the cellBound actually contains the event's point.
    if (index != INVALID_INDEX && getCellBounds(index, index).contains(event.getPoint())) {
      myCellRenderer.setCurrentIndex(index);
    }
    else {
      myCellRenderer.setCurrentIndex(INVALID_INDEX);
    }

    repaint();
  }
}
