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
package com.android.tools.profilers;

import com.android.tools.adtui.common.AdtUiUtils;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

/**
 * An interface for different {@link SessionArtifact} to render their list cell content.
 */
public interface SessionArtifactRenderer<T extends SessionArtifact> {

  // TODO b\67509537 all values pending UX review/finalization.
  int EXPAND_COLLAPSE_COLUMN_WIDTH = JBUI.scale(16);
  int SESSION_HIGHLIGHT_WIDTH = JBUI.scale(2);
  Border SESSION_TIME_PADDING = BorderFactory.createEmptyBorder(JBUI.scale(5), JBUI.scale(0), JBUI.scale(2), JBUI.scale(3));
  Border SESSION_INFO_PADDING = BorderFactory.createEmptyBorder(JBUI.scale(2), JBUI.scale(0), JBUI.scale(2), JBUI.scale(3));
  Font SESSION_TIME_FONT = AdtUiUtils.DEFAULT_FONT.deriveFont(12f);
  Font SESSION_INFO_FONT = AdtUiUtils.DEFAULT_FONT.deriveFont(11f);

  /**
   * @return the component to be rendered in the JList for an SessionArtifact item.
   */
  JComponent generateComponent(@NotNull JList<SessionArtifact> list,
                               @NotNull T item,
                               int index,
                               boolean selected,
                               boolean hasFocus);
}
