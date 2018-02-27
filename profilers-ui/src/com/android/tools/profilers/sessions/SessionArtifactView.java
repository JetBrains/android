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
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

/**
 * A view for showing different {@link SessionArtifact}'s in the list's cell content.
 */
public abstract class SessionArtifactView<T extends SessionArtifact> {

  // TODO b\67509537 all values pending UX review/finalization.
  private static final int EXPAND_ICON_LEFT_PADDING = JBUI.scale(5);
  private static final int EXPAND_ICON_RIGHT_PADDING = JBUI.scale(8);
  private static final int EXPAND_ICON_VERTICAL_PADDING = JBUI.scale(4);
  private static final int SESSION_HIGHLIGHT_WIDTH = JBUI.scale(3);
  private static final Color SESSION_HIGHLIGHT_COLOR = new JBColor(new Color(63, 125, 224), new Color(127, 173, 250));

  protected static final Icon EXPAND_ICON = UIManager.getIcon("Tree.collapsedIcon");
  protected static final Icon COLLAPSE_ICON = UIManager.getIcon("Tree.expandedIcon");
  protected static final int EXPAND_COLLAPSE_COLUMN_WIDTH =
    EXPAND_ICON.getIconWidth() + EXPAND_ICON_LEFT_PADDING + EXPAND_ICON_RIGHT_PADDING;
  protected static final Border EXPAND_ICON_BORDER = BorderFactory.createEmptyBorder(EXPAND_ICON_VERTICAL_PADDING,
                                                                                     EXPAND_ICON_LEFT_PADDING,
                                                                                     EXPAND_ICON_VERTICAL_PADDING,
                                                                                     EXPAND_ICON_RIGHT_PADDING);
  protected static final Border ARTIFACT_ICON_BORDER = BorderFactory.createEmptyBorder(EXPAND_ICON_VERTICAL_PADDING,
                                                                                       0,
                                                                                       EXPAND_ICON_VERTICAL_PADDING,
                                                                                       EXPAND_ICON_RIGHT_PADDING);

  protected static final Border SELECTED_BORDER =
    BorderFactory.createMatteBorder(0, SESSION_HIGHLIGHT_WIDTH, 0, 0, SESSION_HIGHLIGHT_COLOR);
  protected static final Border UNSELECTED_BORDER = BorderFactory.createEmptyBorder(0, SESSION_HIGHLIGHT_WIDTH, 0, 0);

  protected static final Border ARTIFACT_PADDING = BorderFactory.createEmptyBorder(0, 0, JBUI.scale(2), 0);
  protected static Border SESSION_TIME_PADDING =
    BorderFactory.createEmptyBorder(JBUI.scale(5), JBUI.scale(0), JBUI.scale(2), JBUI.scale(3));
  protected static Border SESSION_INFO_PADDING =
    BorderFactory.createEmptyBorder(JBUI.scale(2), JBUI.scale(0), JBUI.scale(2), JBUI.scale(3));

  protected static Font SESSION_TIME_FONT = AdtUiUtils.DEFAULT_FONT.deriveFont(12f);
  protected static Font SESSION_INFO_FONT = AdtUiUtils.DEFAULT_FONT.deriveFont(11f);
  protected static final Font ARTIFACT_TITLE_FONT = AdtUiUtils.DEFAULT_FONT.deriveFont(13f);
  protected static final Font ARTIFACT_STATUS_FONT = AdtUiUtils.DEFAULT_FONT.deriveFont(11f);

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

  /**
   * Handles the click event that occurred within this view. Note that the input point's coordinate is relative to this view's component
   * reference frame. The default action is to call {@link SessionArtifact#onSelect()}.
   */
  public void handleClick(@NotNull Point point) {
    myArtifact.onSelect();
  }

  /**
   * Helper object to wrap information related to the states of the cell in which a {@link SessionArtifactView} belongs.
   */
  public static class ArtifactDrawInfo {
    final int myIndex;
    final boolean mySelected;
    final boolean myCellHasFocus;

    ArtifactDrawInfo(int index, boolean isSelected, boolean cellHasFocus) {
      myIndex = index;
      mySelected = isSelected;
      myCellHasFocus = cellHasFocus;
    }
  }
}
