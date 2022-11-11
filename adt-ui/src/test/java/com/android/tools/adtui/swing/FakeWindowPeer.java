/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.adtui.swing;

import java.awt.AWTEvent;
import java.awt.AWTException;
import java.awt.BufferCapabilities;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.GraphicsConfiguration;
import java.awt.Image;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.event.FocusEvent;
import java.awt.event.PaintEvent;
import java.awt.image.ColorModel;
import java.awt.image.ImageObserver;
import java.awt.image.ImageProducer;
import java.awt.image.VolatileImage;
import java.awt.peer.ComponentPeer;
import java.awt.peer.ContainerPeer;
import java.awt.peer.WindowPeer;
import sun.java2d.pipe.Region;

class FakeWindowPeer implements WindowPeer {
  @Override
  public boolean isObscured() {
    return false;
  }

  @Override
  public boolean canDetermineObscurity() {
    return false;
  }

  @Override
  public void setVisible(boolean v) {
  }

  @Override
  public void setEnabled(boolean e) {
  }

  @Override
  public void paint(Graphics g) {
  }

  @Override
  public void print(Graphics g) {
  }

  @Override
  public void setBounds(int x, int y, int width, int height, int op) {
  }

  @Override
  public void handleEvent(AWTEvent e) {
  }

  @Override
  public void coalescePaintEvent(PaintEvent e) {
  }

  @Override
  public Point getLocationOnScreen() {
    return new Point(0, 0);
  }

  @Override
  public Dimension getPreferredSize() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Dimension getMinimumSize() {
    throw new UnsupportedOperationException();
  }

  @Override
  public ColorModel getColorModel() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Graphics getGraphics() {
    throw new UnsupportedOperationException();
  }

  @Override
  public FontMetrics getFontMetrics(Font font) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void dispose() {
  }

  @Override
  public void setForeground(Color c) {
  }

  @Override
  public void setBackground(Color c) {
  }

  @Override
  public void setFont(Font f) {
  }

  @Override
  public void updateCursorImmediately() {
  }

  @Override
  public boolean requestFocus(Component lightweightChild,
                              boolean temporary,
                              boolean focusedWindowChangeAllowed,
                              long time,
                              FocusEvent.Cause cause) {
    KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
    if (!(manager instanceof FakeKeyboardFocusManager)) {
      return false;
    }
    FakeKeyboardFocusManager fakeManager = (FakeKeyboardFocusManager)manager;
    fakeManager.setFocusOwner(lightweightChild, temporary, cause);
    return true;
  }

  @Override
  public boolean isFocusable() {
    return true;
  }

  @Override
  public Image createImage(int width, int height) {
    throw new UnsupportedOperationException();
  }

  @Override
  public VolatileImage createVolatileImage(int width, int height) {
    throw new UnsupportedOperationException();
  }

  //TODO Remove after fully switching to Java 17. Method was removed from java.awt.peer.ContainerPeer
  public Image createImage(ImageProducer producer) {
    throw new UnsupportedOperationException();
  }

  //TODO Remove after fully switching to Java 17. Method was removed from java.awt.peer.ContainerPeer
  public boolean prepareImage(Image img, int w, int h, ImageObserver o) {
    return false;
  }

  //TODO Remove after fully switching to Java 17. Method was removed from java.awt.peer.ContainerPeer
  public int checkImage(Image img, int w, int h, ImageObserver o) {
    return 0;
  }

  @Override
  public GraphicsConfiguration getGraphicsConfiguration() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean handlesWheelScrolling() {
    return false;
  }

  @Override
  public void createBuffers(int numBuffers, BufferCapabilities caps) throws AWTException {
  }

  @Override
  public Image getBackBuffer() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void flip(int x1, int y1, int x2, int y2, BufferCapabilities.FlipContents flipAction) {
  }

  @Override
  public void destroyBuffers() {
  }

  @Override
  public void reparent(ContainerPeer newContainer) {
  }

  @Override
  public boolean isReparentSupported() {
    return false;
  }

  @Override
  public void layout() {
  }

  @Override
  public void applyShape(Region shape) {
  }

  @Override
  public void setZOrder(ComponentPeer above) {
  }

  @Override
  public boolean updateGraphicsData(GraphicsConfiguration gc) {
    return false;
  }

  @Override
  public Insets getInsets() {
    //noinspection UseDPIAwareInsets
    return new Insets(0, 0, 0, 0);
  }

  @Override
  public void beginValidate() {
  }

  @Override
  public void endValidate() {
  }

  @Override
  public void beginLayout() {
  }

  @Override
  public void endLayout() {
  }

  @Override
  public void toFront() {
  }

  @Override
  public void toBack() {
  }

  @Override
  public void updateAlwaysOnTopState() {
  }

  @Override
  public void updateFocusableWindowState() {
  }

  @Override
  public void setModalBlocked(Dialog blocker, boolean blocked) {
  }

  @Override
  public void updateMinimumSize() {
  }

  @Override
  public void updateIconImages() {
  }

  @Override
  public void setOpacity(float opacity) {
  }

  @Override
  public void setOpaque(boolean isOpaque) {
  }

  @Override
  public void updateWindow() {
  }

  @Override
  public void repositionSecurityWarning() {
  }
}
