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

import com.android.tools.adtui.ImageUtils;
import com.android.tools.adtui.TreeWalker;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.testFramework.PlatformTestUtil;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Enumeration;
import javax.imageio.ImageIO;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import org.jetbrains.annotations.NotNull;

/**
 * A utility class to interact with Swing components in unit tests.
 */
public final class FakeUi {
  public final FakeKeyboard keyboard;
  public final FakeMouse mouse;

  @NotNull
  private final Component myRoot;

  public FakeUi(@NotNull Component root) {
    myRoot = root;
    myRoot.setPreferredSize(myRoot.getSize());
    keyboard = new FakeKeyboard();
    mouse = new FakeMouse(this, keyboard);
    layout();
  }

  /**
   * Forces a re-layout of all components scoped by this FakeUi instance, for example in response to
   * a parent's bounds changing.
   *
   * Note: The constructor automatically forces a layout pass. You should only need to call this
   * method if you update the UI after constructing the FakeUi.
   */
  public void layout() {
    new TreeWalker(myRoot).descendantStream().forEach(Component::doLayout);
  }

  /**
   * Forces a re-layout of all components scoped by this FakeUi instance and dispatches all resulting
   * resizing events.
   */
  public void layoutAndDispatchEvents() throws InterruptedException {
    layout();
    // Allow resizing events to propagate,
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
  }

  public void render(@NotNull OutputStream out) throws IOException {
    BufferedImage image = render();
    ImageIO.write(image, "png", out);
  }

  /**
   * Renders the component and returns the image reflecting its appearance.
   */
  @NotNull
  public BufferedImage render() {
    BufferedImage image = ImageUtils.createDipImage(myRoot.getWidth(), myRoot.getHeight(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = image.createGraphics();
    myRoot.printAll(graphics);
    graphics.dispose();
    return image;
  }

  /**
   * Dumps the content of the Swing tree to stderr.
   */
  public void dump() {
    dump(myRoot, "");
  }

  private static void dump(Component component, String prefix) {
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

  @NotNull
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

  private static RelativePoint findTarget(Component component, int x, int y) {
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

  private static boolean isMouseTarget(Component target) {
    return target.getMouseListeners().length > 0 ||
           target.getMouseMotionListeners().length > 0 ||
           target.getMouseWheelListeners().length > 0;
  }

  public RelativePoint targetMouseEvent(int x, int y) {
    return findTarget(myRoot, x, y);
  }

  /**
   * Sets all default fonts to Droid Sans that is included in the bundled JDK. This makes fonts the same across all platforms.
   */
  public static void setPortableUiFont() {
    Enumeration<?> keys = UIManager.getLookAndFeelDefaults().keys();
    while (keys.hasMoreElements()) {
      Object key = keys.nextElement();
      Object value = UIManager.get(key);
      if (value instanceof FontUIResource) {
        FontUIResource font = (FontUIResource)value;
        UIManager.put(key, new FontUIResource("Droid Sans", font.getStyle(), font.getSize()));
      }
    }
  }

  /**
   * IJ doesn't always refresh the state of the toolbar buttons. This method forces it to refresh.
   */
  public void updateToolbars() {
    updateToolbars(myRoot);
  }

  private static void updateToolbars(@NotNull Component component) {
    if (component instanceof ActionButton) {
      ActionButton button = (ActionButton)component;
      button.updateUI();
      button.updateIcon();
    }

    if (component instanceof ActionToolbar) {
      ActionToolbar toolbar = (ActionToolbar)component;
      toolbar.updateActionsImmediately();
    }

    if (component instanceof Container) {
      for (Component child : ((Container)component).getComponents()) {
        updateToolbars(child);
      }
    }
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
