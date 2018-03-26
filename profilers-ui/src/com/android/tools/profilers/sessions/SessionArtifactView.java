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
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

import static com.android.tools.profilers.ProfilerColors.SELECTED_SESSION_COLOR;

/**
 * A view for showing different {@link SessionArtifact}'s in the list's cell content.
 */
public abstract class SessionArtifactView<T extends SessionArtifact> {

  private static final int EXPAND_ICON_RIGHT_PADDING = JBUI.scale(8);
  private static final int EXPAND_ICON_VERTICAL_PADDING = JBUI.scale(4);
  private static final int SESSION_HIGHLIGHT_WIDTH = JBUI.scale(3);

  protected static final Border ARTIFACT_ICON_BORDER = BorderFactory.createEmptyBorder(EXPAND_ICON_VERTICAL_PADDING,
                                                                                       0,
                                                                                       EXPAND_ICON_VERTICAL_PADDING,
                                                                                       EXPAND_ICON_RIGHT_PADDING);

  protected static final Border SELECTED_BORDER =
    BorderFactory.createMatteBorder(0, SESSION_HIGHLIGHT_WIDTH, 0, 0, SELECTED_SESSION_COLOR);
  protected static final Border UNSELECTED_BORDER = BorderFactory.createEmptyBorder(0, SESSION_HIGHLIGHT_WIDTH, 0, 0);

  protected static final Border ARTIFACT_PADDING = JBUI.Borders.empty(2, 9, 2, 4);
  protected static final Border LABEL_PADDING = JBUI.Borders.empty(1, 0);

  protected static final Font TITLE_FONT = AdtUiUtils.DEFAULT_FONT.deriveFont(13f);
  protected static final Font STATUS_FONT = AdtUiUtils.DEFAULT_FONT.deriveFont(11f);

  @NotNull private final T myArtifact;
  @NotNull private final ArtifactDrawInfo myArtifactDrawInfo;

  public SessionArtifactView(@NotNull ArtifactDrawInfo drawInfo, @NotNull T artifact) {
    myArtifact = artifact;
    myArtifactDrawInfo = drawInfo;
  }

  /**
   * @return the component to be rendered in the JList for an {@link SessionArtifact}.
   */
  @NotNull
  public abstract JComponent getComponent();

  @NotNull
  public T getArtifact() {
    return myArtifact;
  }

  public boolean isSessionSelected() {
    return myArtifact.getSession().equals(myArtifact.getProfilers().getSession());
  }

  public int getIndex() {
    return myArtifactDrawInfo.myIndex;
  }

  public boolean isSelected() {
    return myArtifactDrawInfo.mySelected;
  }

  public boolean isHovered() {
    return myArtifactDrawInfo.myHovered;
  }

  /**
   * Helper object to wrap information related to the states of the cell in which a {@link SessionArtifactView} belongs.
   */
  public static class ArtifactDrawInfo {
    final int myIndex;
    final boolean mySelected;
    final boolean myHovered;
    final boolean myCellHasFocus;

    ArtifactDrawInfo(int index, boolean isSelected, boolean isHovered, boolean cellHasFocus) {
      myIndex = index;
      mySelected = isSelected;
      myHovered = isHovered;
      myCellHasFocus = cellHasFocus;
    }
  }
}
