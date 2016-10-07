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
package com.android.tools.idea.editors.gfxtrace.widgets;

import com.android.tools.idea.editors.gfxtrace.renderers.RenderUtils;
import com.intellij.icons.AllIcons;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class LoadableIcon extends JComponent implements Icon {
  private final int myWidth, myHeight;
  private BufferedImage myImage;
  private State myState = State.LOADING;
  private Component myRepaintComponent = this;

  public LoadableIcon(int width, int height) {
    myWidth = width;
    myHeight = height;
    setBounds(0, 0, width, height);
  }

  public LoadableIcon withImage(@Nullable("image still loading or loading failed") BufferedImage image, boolean loadingFailed) {
    myState = loadingFailed ? State.FAILED : (image == null) ? State.LOADING : State.LOADED;
    myImage = image;
    repaint();
    return this;
  }

  public LoadableIcon withRepaintComponent(Component component) {
    myRepaintComponent = component;
    return this;
  }

  @Override
  public void paintIcon(Component c, Graphics g, int x, int y) {
    switch (myState) {
      case LOADING:
        LoadingIndicator.paint(c, g, x, y, getWidth(), getHeight());
        LoadingIndicator.scheduleForRedraw(Repaintables.forComponent(myRepaintComponent));
        break;
      case LOADED:
        if (myImage != null) {
          RenderUtils.drawImage(c, g, myImage, x, y, getWidth(), getHeight());
        }
        break;
      case FAILED:
        RenderUtils.drawIcon(c, g, AllIcons.General.Warning, x, y, getWidth(), getHeight());
        break;
    }
  }

  @Override
  public int getIconWidth() {
    return getWidth();
  }

  @Override
  public int getIconHeight() {
    return getHeight();
  }

  @Override
  public Dimension getPreferredSize() {
    return (myImage != null) ? new Dimension(myImage.getWidth(), myImage.getHeight()) : new Dimension(myWidth, myHeight);
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    paintIcon(this, g, 0, 0);
  }

  private enum State {
    LOADING, LOADED, FAILED;
  }
}
