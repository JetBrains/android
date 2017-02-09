/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.adtui.TreeWalker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;

/**
 * A utility class to interact with Swing components in unit tests.
 */
public final class FakeUi {
  public final FakeKeyboard keyboard;
  public final FakeMouse mouse;

  @NotNull
  private Component myRoot;

  public FakeUi(@NotNull Component root) {
    myRoot = root;
    myRoot.setPreferredSize(myRoot.getSize());
    keyboard = new FakeKeyboard();
    mouse = new FakeMouse(this, keyboard);
    layout();
  }

  public void layout() {
    new TreeWalker(myRoot).descendantStream().forEach(Component::doLayout);
  }

  public void render(OutputStream out) throws IOException {
    BufferedImage bi = new BufferedImage(myRoot.getWidth(), myRoot.getHeight(), BufferedImage.TYPE_INT_ARGB);
    myRoot.printAll(bi.getGraphics());
    ImageIO.write(bi, "png", out);
  }

  /**
   * Dumps the content of the Swing tree to stderr.
   */
  public void dump() {
    dump(myRoot, "");
  }

  private void dump(Component component, String prefix) {
    System.err.println(prefix + component.getClass().getSimpleName() + "@(" +
                       component.getX() + ", " + component.getY() + ") [" +
                       component.getSize().getWidth() + "x" + component.getSize().getHeight() + "]" +
                       (isMouseTarget(component) ? " {*}" : ""));
    if (component instanceof Container) {
      Container container = (Container)component;
      for (int i = 0; i < container.getComponentCount(); i++) {
        dump(container.getComponent(i), prefix + "  ");
      }
    }
  }

  @Nullable
  public Component getRoot() {
    return myRoot;
  }

  public Point getPosition(Component component) {
    int rx = 0;
    int ry = 0;
    while (component != myRoot) {
      rx += component.getX();
      ry += component.getY();
      component = component.getParent();
    }
    return new Point(rx, ry);
  }

  public Point toRelative(Component component, int x, int y) {
    Point position = getPosition(component);
    return new Point(x - position.x, y - position.y);
  }

  private RelativePoint findTarget(Component component, int x, int y) {
    if (component.contains(x, y)) {
      if (component instanceof Container) {
        Container container = (Container)component;
        for (int i = 0; i < container.getComponentCount(); i++) {
          Component child = container.getComponent(i);
          RelativePoint target = findTarget(child, x - child.getX(), y - child.getY());
          if (target != null) {
            return target;
          }
        }
      }
      if (isMouseTarget(component)) {
        return new RelativePoint(component, x, y);
      }
    }
    return null;
  }

  private boolean isMouseTarget(Component target) {
    return target.getMouseListeners().length > 0 ||
           target.getMouseMotionListeners().length > 0 ||
           target.getMouseWheelListeners().length > 0;
  }

  public RelativePoint targetMouseEvent(int x, int y) {
    return findTarget(myRoot, x, y);
  }

  public static class RelativePoint {
    public final Component component;
    public final int x;
    public final int y;

    public RelativePoint(Component component, int x, int y) {
      this.component = component;
      this.x = x;
      this.y = y;
    }
  }
}
