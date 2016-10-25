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
package com.android.tools.swing.ui;

import javax.swing.JScrollPane;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.math.BigInteger;

public class InfiniteScrollPane extends JScrollPane {

  public interface InfinitePanel {
    int getFullWidth();
    BigInteger getFullHeight();

    void setYOffset(BigInteger yOffset);
    BigInteger getYOffset();
  }

  private InfinitePanel myInfinitePanel;

  public InfiniteScrollPane(Component panel) {
    setViewportView(panel);

    getVerticalScrollBar().addAdjustmentListener(e -> {
      if (!e.getValueIsAdjusting() && myInfinitePanel != null) {
        int value = e.getValue();
        int viewHeight = getViewport().getViewSize().height;
        int viewportHeight = getViewport().getExtentSize().height;

        BigInteger oldYOffset = myInfinitePanel.getYOffset();
        BigInteger newYOffset = checkYOffsetBounds(oldYOffset.add(BigInteger.valueOf(value - ((viewHeight - viewportHeight) / 2))));

        if (!newYOffset.equals(oldYOffset)) {
          myInfinitePanel.setYOffset(newYOffset);
          getViewport().setViewPosition(new Point(0, getViewport().getViewPosition().y + oldYOffset.subtract(newYOffset).intValueExact()));
          repaint();
        }
      }
    });
  }

  @Override
  public void setViewportView(Component view) {
    super.setViewportView(view);
    myInfinitePanel = view instanceof InfinitePanel ? (InfinitePanel)view : null;
    updateViewPortSize();
  }

  public void setViewPosition(int x, BigInteger y) {
    int viewHeight = getViewport().getViewSize().height;
    int viewportHeight = getViewport().getExtentSize().height;
    BigInteger yOffset = checkYOffsetBounds(y.subtract(BigInteger.valueOf((viewHeight - viewportHeight) / 2)));
    myInfinitePanel.setYOffset(yOffset);
    getViewport().setViewPosition(new Point(x, y.subtract(yOffset).intValueExact()));
  }

  private BigInteger checkYOffsetBounds(BigInteger yOffset) {
    int viewHeight = getViewport().getViewSize().height;
    if (yOffset.compareTo(myInfinitePanel.getFullHeight().subtract(BigInteger.valueOf(viewHeight))) > 0) {
      yOffset = myInfinitePanel.getFullHeight().subtract(BigInteger.valueOf(viewHeight));
    }
    if (yOffset.compareTo(BigInteger.ZERO) < 0) {
      yOffset = BigInteger.ZERO;
    }
    return yOffset;
  }

  @Override
  public void setBounds(int x, int y, int width, int height) {
    super.setBounds(x, y, width, height);
    updateViewPortSize();
  }

  private void updateViewPortSize() {
    if (myInfinitePanel != null) {
      Component view = getViewport().getView();
      Dimension viewPortSize = getViewport().getExtentSize();
      Dimension size = new Dimension(myInfinitePanel.getFullWidth(), myInfinitePanel.getFullHeight().min(BigInteger.valueOf(Math.max(500, viewPortSize.height) * 100)).intValueExact());
      assert size.width > 0;
      assert size.height > 0;
      size.width = Math.max(viewPortSize.width, size.width);
      size.height = Math.max(viewPortSize.height, size.height);
      view.setPreferredSize(size);
      view.setMinimumSize(size);
      view.setMaximumSize(size);
      view.setSize(size);
    }
  }
}
