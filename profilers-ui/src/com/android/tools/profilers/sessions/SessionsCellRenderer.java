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

import com.android.tools.profilers.ViewBinder;
import com.android.tools.profilers.cpu.CpuCaptureArtifactView;
import com.android.tools.profilers.cpu.CpuCaptureSessionArtifact;
import com.android.tools.profilers.memory.HprofArtifactView;
import com.android.tools.profilers.memory.HprofSessionArtifact;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

import static com.android.tools.profilers.sessions.SessionsList.INVALID_INDEX;

/**
 * A {@link ListCellRenderer} that delegates to individual {@link SessionArtifactView} for rendering the cell's content.
 */
final class SessionsCellRenderer implements ListCellRenderer<SessionArtifact> {

  @NotNull private final SessionsList mySessionsList;
  @NotNull private final ViewBinder<SessionArtifactView.ArtifactDrawInfo, SessionArtifact, SessionArtifactView> myViewBinder;
  private int myCurrentIndex = INVALID_INDEX;

  public SessionsCellRenderer(@NotNull SessionsList sessionsList) {
    mySessionsList = sessionsList;
    myViewBinder = new ViewBinder<>();
    myViewBinder.bind(SessionItem.class, SessionItemView::new);
    myViewBinder.bind(HprofSessionArtifact.class, HprofArtifactView::new);
    myViewBinder.bind(CpuCaptureSessionArtifact.class, CpuCaptureArtifactView::new);
  }

  /**
   * Set the current cell index the mouse is interacting with (e.g. hover, click, etc).
   * TODO: support keyboard navigation.
   */
  public void setCurrentIndex(int index) {
    myCurrentIndex = index;
  }

  @Override
  public Component getListCellRendererComponent(JList<? extends SessionArtifact> list,
                                                SessionArtifact item,
                                                int index,
                                                boolean isSelected,
                                                boolean cellHasFocus) {
    SessionArtifactView.ArtifactDrawInfo drawInfo =
      new SessionArtifactView.ArtifactDrawInfo(index, isSelected, myCurrentIndex == index, cellHasFocus);
    SessionArtifactView component = myViewBinder.build(drawInfo, item);
    return component.getComponent();
  }
}
