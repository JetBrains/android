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

import com.android.tools.idea.ddms.EdtExecutor;
import com.android.tools.idea.editors.gfxtrace.renderers.RenderUtils;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.icons.AllIcons;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class LoadableIcon extends JComponent implements Icon {
  private final int myWidth, myHeight;
  private ListenableFuture<BufferedImage> myFuture;
  private BufferedImage myImage;
  private State myState = State.LOADED;
  private Component myRepaintComponent = this;

  public LoadableIcon(int width, int height) {
    myWidth = width;
    myHeight = height;
    setBounds(0, 0, width, height);
  }

  public LoadableIcon withImage(final ListenableFuture<BufferedImage> future) {
    synchronized (this) {
      myState = State.LOADING;
      myFuture = future;
      myImage = null;
    }

    Futures.addCallback(future, new FutureCallback<BufferedImage>() {
      @Override
      public void onSuccess(BufferedImage result) {
        update(result, State.LOADED);
      }

      @Override
      public void onFailure(Throwable t) {
        update(null, State.FAILED);
      }

      private void update(BufferedImage image, State state) {
        synchronized (LoadableIcon.this) {
          if (future == myFuture) {
            myState = state;
            myFuture = null;
            myImage = image;
          }
        }
        repaint();
      }
    }, EdtExecutor.INSTANCE);
    repaint();
    return this;
  }

  public synchronized LoadableIcon withRepaintComponent(Component component) {
    myRepaintComponent = component;
    return this;
  }

  @Override
  public void paintIcon(Component c, Graphics g, int x, int y) {
    synchronized (this) {
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
    synchronized (this) {
      return (myImage != null) ? new Dimension(myImage.getWidth(), myImage.getHeight()) : new Dimension(myWidth, myHeight);
    }
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
