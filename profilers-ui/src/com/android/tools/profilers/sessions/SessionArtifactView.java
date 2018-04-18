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

import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.model.AspectObserver;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.*;

import static com.android.tools.profilers.ProfilerColors.HOVERED_SESSION_COLOR;
import static com.android.tools.profilers.ProfilerColors.SELECTED_SESSION_COLOR;

/**
 * A view for showing different {@link SessionArtifact}'s in the sessions panel.
 */
public abstract class SessionArtifactView<T extends SessionArtifact> extends JPanel {

  protected static final Border ARTIFACT_ICON_BORDER = JBUI.Borders.empty(4, 0);
  protected static final Border SELECTED_BORDER = JBUI.Borders.customLine(SELECTED_SESSION_COLOR, 0, 3, 0, 0);
  protected static final Border UNSELECTED_BORDER = JBUI.Borders.empty(0, 3, 0, 0);

  protected static final Border ARTIFACT_PADDING = JBUI.Borders.empty(2, 9, 2, 4);
  protected static final Border LABEL_PADDING = JBUI.Borders.empty(1, 8, 1, 0);

  protected static final Font TITLE_FONT = AdtUiUtils.DEFAULT_FONT.deriveFont(13f);
  protected static final Font STATUS_FONT = AdtUiUtils.DEFAULT_FONT.deriveFont(11f);

  @NotNull private final T myArtifact;
  @NotNull private final ArtifactDrawInfo myArtifactDrawInfo;
  @NotNull protected final AspectObserver myObserver;

  public SessionArtifactView(@NotNull ArtifactDrawInfo artifactDrawInfo, @NotNull T artifact) {
    setBackground(HOVERED_SESSION_COLOR);
    setOpaque(false);
    myArtifactDrawInfo = artifactDrawInfo;
    myArtifact = artifact;
    myObserver = new AspectObserver();
    initializeListeners();

    // Listen to selected session changed so we can update the selection visuals accordingly.
    myArtifactDrawInfo.mySessionsView.getProfilers().getSessionsManager().addDependency(myObserver)
                                     .onChange(SessionAspect.SELECTED_SESSION, () -> {
                                       selectedSessionChanged();
                                       // Selection states have possibly changed for this artifact, re-render.
                                       revalidate();
                                       repaint();
                                     });
    selectedSessionChanged();
  }

  private void initializeListeners() {
    // Mouse listener to handle selection and hover effects.
    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        myArtifact.onSelect();
      }

      @Override
      public void mouseEntered(MouseEvent e) {
        showHoverState(true);
      }

      @Override
      public void mouseExited(MouseEvent e) {
        showHoverState(false);
      }
    });

    addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        // When a component is first constructed, we need to check whether the mouse is already hovered. If so, draw the hover effect.
        Point mousePosition = MouseInfo.getPointerInfo().getLocation();
        SwingUtilities.convertPointFromScreen(mousePosition, SessionArtifactView.this);
        showHoverState(SessionArtifactView.this.contains(mousePosition));
      }
    });
  }

  @NotNull
  public T getArtifact() {
    return myArtifact;
  }

  public boolean isSessionSelected() {
    return myArtifact.getSession().equals(myArtifact.getProfilers().getSessionsManager().getSelectedSession());
  }

  public int getIndex() {
    return myArtifactDrawInfo.myIndex;
  }

  protected abstract void selectedSessionChanged();

  private void showHoverState(boolean hoever) {
    setOpaque(hoever);
    repaint();
  }

  /**
   * Helper object to wrap information related to the states of the cell in which a {@link SessionArtifactView} belongs.
   */
  public static class ArtifactDrawInfo {
    @NotNull final SessionsView mySessionsView;
    final int myIndex;

    ArtifactDrawInfo(@NotNull SessionsView sessionsView, int index) {
      mySessionsView = sessionsView;
      myIndex = index;
    }
  }
}
