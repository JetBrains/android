/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tools.adtui.chart.linechart;

import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * This class handles the configuration of events on a line chart.
 */
public class EventConfig {

  /**
   * The line color that marks the beginning and end of the event.
   */
  @NotNull
  private Color mColor;

  /**
   * The label that this event should render
   */
  @NotNull
  private final JLabel mLabel;

  /**
   * If true, the area that the event spans over will be grey'ed out.
   */
  private boolean mIsBlocking;

  /**
   * Whether the event region should be filled instead of showing only start/end markers
   */
  private boolean mIsFilled;


  public EventConfig(@NotNull Color color) {
    mColor = color;
    mLabel = new JBLabel();

    updateLabelBounds();
  }

  @NotNull
  public EventConfig setFilled(boolean isFilled) {
    mIsFilled = isFilled;
    return this;
  }

  public boolean isFilled() {
    return mIsFilled;
  }

  @NotNull
  public EventConfig setBlocking(boolean isBlocking) {
    mIsBlocking = isBlocking;
    return this;
  }

  public boolean isBlocking() {
    return mIsBlocking;
  }

  @NotNull
  public EventConfig setColor(@NotNull Color color) {
    mColor = color;
    return this;
  }

  @NotNull
  public Color getColor() {
    return mColor;
  }

  @NotNull
  public EventConfig setText(@NotNull String text) {
    mLabel.setText(text);
    updateLabelBounds();
    return this;
  }

  @NotNull
  public EventConfig setIcon(@Nullable Icon icon) {
    mLabel.setIcon(icon);
    updateLabelBounds();
    return this;
  }

  @NotNull
  public JLabel getLabel() {
    return mLabel;
  }

  private void updateLabelBounds() {
    Dimension size = mLabel.getPreferredSize();
    mLabel.setBounds(0, 0, size.width, size.height);
  }
}
