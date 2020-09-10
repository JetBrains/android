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
import com.google.common.base.Predicates;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.testFramework.PlatformTestUtil;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.VolatileImage;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Enumeration;
import java.util.function.Predicate;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A utility class to interact with Swing components in unit tests.
 */
public final class FakeUi {
  public final FakeKeyboard keyboard;
  public final FakeMouse mouse;
  public final double screenScale;

  @NotNull
  private final Component root;

  /**
   * Initializes the UI emulating a non-HiDPI screen.
   *
   * @param rootComponent the top-level component component
   */
  public FakeUi(@NotNull Component rootComponent) {
    this(rootComponent, 1);
  }

  /**
   * Initializes the UI emulating a HiDPI screen.
   *
   * @param rootComponent the top-level component component
   * @param screenScale size of a virtual pixel in physical pixels
   */
  public FakeUi(@NotNull Component rootComponent, double screenScale) {
    root = rootComponent;
    this.screenScale = screenScale;
    keyboard = new FakeKeyboard();
    mouse = new FakeMouse(this, keyboard);
    //noinspection FloatingPointEquality
    if (screenScale != 1 && rootComponent.getParent() == null) {
      // Applying graphics configuration involves reparenting, so don't do it for a component that already has a parent.
      applyGraphicsConfiguration(new FakeGraphicsConfiguration(screenScale), root);
    }
    root.setPreferredSize(root.getSize());
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
    new TreeWalker(root).descendantStream().forEach(Component::doLayout);
  }

  /**
   * Forces a re-layout of all components scoped by this FakeUi instance and dispatches all resulting
   * resizing events.
   */
  public void layoutAndDispatchEvents() throws InterruptedException {
    layout();
    // Allow resizing events to propagate.
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
  }

  /**
   * Renders the component and returns the image reflecting its appearance.
   */
  @NotNull
  public BufferedImage render() {
    BufferedImage image = ImageUtils.createDipImage((int)(root.getWidth() * screenScale), (int)(root.getHeight() * screenScale),
                                                    BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = image.createGraphics();
    graphics.setTransform(AffineTransform.getScaleInstance(screenScale, screenScale));
    root.printAll(graphics);
    graphics.dispose();
    return image;
  }

  /**
   * Dumps the content of the Swing tree to stderr.
   */
  public void dump() {
    dump(root, "");
  }

  private static void dump(@NotNull Component component, @NotNull String prefix) {
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
    return root;
  }

  @NotNull
  public Point getPosition(@NotNull Component component) {
    int rx = 0;
    int ry = 0;
    while (component != root) {
      rx += component.getX();
      ry += component.getY();
      component = component.getParent();
    }
    return new Point(rx, ry);
  }

  @NotNull
  public Point toRelative(@NotNull Component component, int x, int y) {
    Point position = getPosition(component);
    return new Point(x - position.x, y - position.y);
  }

  /**
   * Simulates pressing and releasing the left mouse button over the given component.
   */
  public void clickOn(@NotNull Component component) throws InterruptedException {
    Point location = getPosition(component);
    mouse.click(location.x, location.y);
    // Allow events to propagate.
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
  }

  /**
   * Returns the first component of the given type by doing breadth-first search starting from the root
   * component, or null if no components satisfy the predicate.
   */
  @Nullable
  public <T> T findComponent(@NotNull Class<T> type) {
    return findComponent(type, Predicates.alwaysTrue());
  }

  /**
   * Returns the first component of the given type satisfying the given predicate by doing breadth-first
   * search starting from the root component, or null if no components satisfy the predicate.
   */
  @SuppressWarnings("unchecked")
  @Nullable
  public <T> T findComponent(@NotNull Class<T> type, @NotNull Predicate<T> predicate) {
    if (type.isInstance(root) && predicate.test((T)root)) {
      return (T)root;
    }
    if (root instanceof Container) {
      Deque<Container> queue = new ArrayDeque<>();
      queue.add((Container)root);
      Container container;
      while ((container = queue.poll()) != null) {
        for (Component child : container.getComponents()) {
          if (type.isInstance(child) && predicate.test((T)child)) {
            return (T)child;
          }
          if (child instanceof Container) {
            queue.add((Container)child);
          }
        }
      }
    }
    return null;
  }

  @Nullable
  private static RelativePoint findTarget(@NotNull Component component, int x, int y) {
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

  private static boolean isMouseTarget(@NotNull Component target) {
    return target.getMouseListeners().length > 0 ||
           target.getMouseMotionListeners().length > 0 ||
           target.getMouseWheelListeners().length > 0;
  }

  @Nullable
  public RelativePoint targetMouseEvent(int x, int y) {
    return findTarget(root, x, y);
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
    updateToolbars(root);
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

  private static void applyGraphicsConfiguration(@NotNull GraphicsConfiguration config, @NotNull Component component) {
    // Work around package-private visibility of the Component.setGraphicsConfiguration method.
    Container container = new Container() {
      @Override
      public GraphicsConfiguration getGraphicsConfiguration() {
        return config;
      }
    };
    container.add(component);
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

  private static class FakeGraphicsConfiguration extends GraphicsConfiguration {
    private final AffineTransform transform;
    private final GraphicsDevice device;

    protected FakeGraphicsConfiguration(double scale) {
      transform = AffineTransform.getScaleInstance(scale, scale);
      device = new FakeGraphicsDevice(this);
    }

    @Override
    public GraphicsDevice getDevice() {
      return device;
    }

    @Override
    public VolatileImage createCompatibleVolatileImage(int width, int height) {
      return super.createCompatibleVolatileImage(width, height);
    }

    @Override
    public ColorModel getColorModel() {
      return ColorModel.getRGBdefault();
    }

    @Override
    public ColorModel getColorModel(int transparency) {
      return ColorModel.getRGBdefault();
    }

    @Override
    public AffineTransform getDefaultTransform() {
      return transform;
    }

    @Override
    public AffineTransform getNormalizingTransform() {
      return transform;
    }

    @Override
    public Rectangle getBounds() {
      return new Rectangle();
    }
  }

  private static class FakeGraphicsDevice extends GraphicsDevice {
    private final GraphicsConfiguration defaultConfiguration;

    FakeGraphicsDevice(GraphicsConfiguration config) {
      defaultConfiguration = config;
    }

    @Override
    public int getType() {
      return TYPE_RASTER_SCREEN;
    }

    @Override
    public String getIDstring() {
      return "FakeDevice";
    }

    @Override
    public GraphicsConfiguration[] getConfigurations() {
      return new GraphicsConfiguration[0];
    }

    @Override
    public GraphicsConfiguration getDefaultConfiguration() {
      return defaultConfiguration;
    }
  }
}
