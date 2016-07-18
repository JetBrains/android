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
  private Color myColor;

  /**
   * The label that this event should render
   */
  @NotNull
  private final JLabel myLabel;

  /**
   * If true, the area that the event spans over will be grey'ed out.
   */
  private boolean myIsBlocking;

  /**
   * Whether the event region should be filled instead of showing only start/end markers
   */
  private boolean myIsFilled;

  @NotNull
  private Stroke myStroke;

  public EventConfig(@NotNull Color color) {
    myColor = color;
    myLabel = new JBLabel();
    myStroke = new BasicStroke(1); // Default

    updateLabelBounds();
  }

  @NotNull
  public EventConfig setFilled(boolean isFilled) {
    myIsFilled = isFilled;
    return this;
  }

  public boolean isFilled() {
    return myIsFilled;
  }

  @NotNull
  public EventConfig setBlocking(boolean isBlocking) {
    myIsBlocking = isBlocking;
    return this;
  }

  public boolean isBlocking() {
    return myIsBlocking;
  }

  @NotNull
  public EventConfig setColor(@NotNull Color color) {
    myColor = color;
    return this;
  }

  @NotNull
  public Color getColor() {
    return myColor;
  }

  @NotNull
  public EventConfig setText(@NotNull String text) {
    myLabel.setText(text);
    updateLabelBounds();
    return this;
  }

  @NotNull
  public EventConfig setIcon(@Nullable Icon icon) {
    myLabel.setIcon(icon);
    updateLabelBounds();
    return this;
  }

  @NotNull
  public JLabel getLabel() {
    return myLabel;
  }

  private void updateLabelBounds() {
    Dimension size = myLabel.getPreferredSize();
    myLabel.setBounds(0, 0, size.width, size.height);
  }

  @NotNull
  public EventConfig setStroke(@NotNull Stroke stroke) {
    myStroke = stroke;
    return this;
  }

  @NotNull
  public Stroke getStroke() {
    return myStroke;
  }
}
