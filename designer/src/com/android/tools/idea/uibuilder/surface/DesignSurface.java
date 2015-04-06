/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.surface;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.State;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.ddms.screenshot.DeviceArtPainter;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.uibuilder.actions.SelectAllAction;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.model.SwingCoordinate;
import com.google.common.collect.Lists;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.wm.IdeGlassPane;
import com.intellij.ui.components.JBScrollBar;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.Magnificator;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.ui.ButtonlessScrollBarUI;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.plaf.ScrollBarUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.util.List;

import static com.android.tools.idea.uibuilder.graphics.NlConstants.*;

/**
 * The design surface in the layout editor, which contains the full background, rulers, one
 * or more device renderings, etc
 */
public class DesignSurface extends JPanel {
  private static final Logger LOG = Logger.getInstance(DesignSurface.class);

  private final ScreenView myScreenView;
  private final ScreenView myBlueprintView;
  private int myScreenX;
  private int myScreenY;
  private double myScale = 1;
  @NonNull private final JScrollPane myScrollPane;
  private final MyLayeredPane myLayeredPane;
  private boolean myDeviceFrames = false;
  private List<Layer> myLayers = Lists.newArrayList();
  private InteractionManager myInteractionManager;
  private GlassPane myGlassPane;

  public DesignSurface(@NonNull NlModel model) {
    super(new BorderLayout());
    setOpaque(true);
    setFocusable(true);
    setRequestFocusEnabled(true);
    setBackground(UIUtil.TRANSPARENT_COLOR);

    myInteractionManager = new InteractionManager(this);
    myScreenView = new ScreenView(this, model);
    myScreenX = RULER_SIZE_PX + DEFAULT_SCREEN_OFFSET_X;
    myScreenY = RULER_SIZE_PX + DEFAULT_SCREEN_OFFSET_Y;
    myScreenView.setLocation(myScreenX, myScreenY);

    myBlueprintView = new ScreenView(this, model);
    myBlueprintView.setLocation(myScreenX + myScreenView.getPreferredSize().width + 10, myScreenY);

    myLayeredPane = new MyLayeredPane();
    myLayeredPane.setBounds(0, 0, 100, 100);
    Dimension preferredSize = myScreenView.getPreferredSize();
    myLayeredPane.setPreferredSize(preferredSize);
    myGlassPane = new GlassPane();
    myLayeredPane.add(myGlassPane, JLayeredPane.DRAG_LAYER);

    myScrollPane = new MyScrollPane();
    myScrollPane.setViewportView(myLayeredPane);
    myScrollPane.setBorder(null);
    myScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
    myScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);

    add(myScrollPane, BorderLayout.CENTER);

    // TODO: Do this as part of the layout/validate operation instead
    addComponentListener(new ComponentListener() {
      @Override
      public void componentResized(ComponentEvent componentEvent) {
        updateScrolledAreaSize();
        if (isShowing() && getWidth() > 0 && getHeight() > 0) {
          zoomToFit();
        }
      }

      @Override
      public void componentMoved(ComponentEvent componentEvent) {
      }

      @Override
      public void componentShown(ComponentEvent componentEvent) {
      }

      @Override
      public void componentHidden(ComponentEvent componentEvent) {
      }
    });

    myLayers.add(new ScreenViewLayer(myScreenView));
    myLayers.add(new SelectionLayer(myScreenView));

    myLayers.add(new BlueprintLayer(myBlueprintView));
    myLayers.add(new SelectionLayer(myBlueprintView));

    myInteractionManager.registerListeners();

    AnAction selectAllAction = new SelectAllAction(this);
    registerAction(selectAllAction, "$SelectAll");
  }

  public void registerAction(AnAction action, @NonNls String actionId) {
    action.registerCustomShortcutSet(
      ActionManager.getInstance().getAction(actionId).getShortcutSet(),
      myLayeredPane
    );
  }

  private void updateScrolledAreaSize() {
    Dimension size = myScreenView.getPreferredSize();
    if (size != null) {
      Dimension dimension =
        new Dimension((int)(myScale * size.width) + 2 * DEFAULT_SCREEN_OFFSET_X, (int)(myScale * size.height) + 2 * DEFAULT_SCREEN_OFFSET_Y);
      myLayeredPane.setBounds(0, 0, dimension.width, dimension.height);
      myScrollPane.revalidate();
    }
  }

  public JComponent getPreferredFocusedComponent() {
    return myGlassPane;
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    super.paintComponent(graphics);
  }

  @Override
  protected void paintChildren(Graphics graphics) {
    super.paintChildren(graphics);

    if (isFocusOwner()) {
      graphics.setColor(UIUtil.getFocusedBoundsColor());
      graphics.drawRect(getX(), getY(), getWidth() - 1, getHeight() - 1);
    }
  }

  @NonNull
  public ScreenView getCurrentScreenView() {
    return myScreenView;
  }

  @NonNull
  public ScreenView getScreenView(@SwingCoordinate int x, @SwingCoordinate int y) {
    // Currently only a single screen view active in the canvas.
    if (x >= myBlueprintView.getX() && y >= myBlueprintView.getY()) {
      return myBlueprintView;
    }
    return myScreenView;
  }

  public void zoomActual() {
    setScale(1);
    repaint();
  }

  public void zoomIn() {
    setScale(myScale * 1.1);
    repaint();
  }

  public void zoomOut() {
    setScale(myScale * (1/1.1));
    repaint();
  }

  public void zoomToFit() {
    // Fit to zoom
    int availableWidth = myScrollPane.getWidth();
    int availableHeight = myScrollPane.getHeight();
    Dimension preferredSize = myScreenView.getPreferredSize();
    if (preferredSize != null) {
      int requiredWidth = preferredSize.width;
      int requiredHeight = preferredSize.height;
      availableWidth -= 2 * DEFAULT_SCREEN_OFFSET_X;
      availableHeight -= 2 * DEFAULT_SCREEN_OFFSET_Y;

      if (myBlueprintView != null) {
        if (requiredWidth > requiredHeight) {
          requiredHeight *= 2;
        } else {
          requiredWidth *= 2;
        }
      }

      double scaleX = (double)availableWidth / requiredWidth;
      double scaleY = (double)availableHeight / requiredHeight;
      setScale(Math.min(scaleX, scaleY));
      repaint();
    }
  }

  public double getScale() {
    return myScale;
  }

  private void setScale(double scale) {
    if (Math.abs(scale - 1) < 0.0001) {
      scale = 1;
    } else if (scale < 0.01) {
      scale = 0.01;
    } else if (scale > 10) {
      scale = 10;
    }
    myScale = scale;
    Dimension preferredSize = myScreenView.getPreferredSize();
    if (preferredSize != null) {
      if (preferredSize.width > preferredSize.height) {
        // top/bottom stacking
        myBlueprintView.setLocation(myScreenX, myScreenY + (int)(myScale * preferredSize.height) + 10);
      } else {
        // left/right ordering
        myBlueprintView.setLocation(myScreenX + (int)(myScale * preferredSize.width) + 10, myScreenY);
      }
    }
    updateScrolledAreaSize();
  }

  public void toggleDeviceFrames() {
    myDeviceFrames = !myDeviceFrames;
    if (myDeviceFrames) {
      myScreenX = 2 * DEFAULT_SCREEN_OFFSET_X;
      myScreenY = 2 * DEFAULT_SCREEN_OFFSET_Y;
    } else {
      myScreenX = DEFAULT_SCREEN_OFFSET_X;
      myScreenY = DEFAULT_SCREEN_OFFSET_Y;
    }
    myScreenView.setLocation(myScreenX, myScreenY);
    repaint();
  }

  public JComponent getLayeredPane() {
    return myLayeredPane;
  }

  @VisibleForTesting
  public InteractionManager getInteractionManager() {
    return myInteractionManager;
  }

  /** The editor has been activated */
  public void activate() {
    myScreenView.getModel().activate();
  }

  public void deactivate() {
    myScreenView.getModel().deactivate();
  }

  private static class MyScrollPane extends JBScrollPane {
    private MyScrollPane() {
      super(0);
      setOpaque(true);
      setBackground(UIUtil.TRANSPARENT_COLOR);
      setupCorners();
    }

    @NonNull
    @Override
    public JScrollBar createVerticalScrollBar() {
      return new MyScrollBar(Adjustable.VERTICAL);
    }

    @Override
    public JScrollBar createHorizontalScrollBar() {
      return new MyScrollBar(Adjustable.HORIZONTAL);
    }

    @Override
    protected boolean isOverlaidScrollbar(@Nullable JScrollBar scrollbar) {
      ScrollBarUI vsbUI = scrollbar == null ? null : scrollbar.getUI();
      return vsbUI instanceof ButtonlessScrollBarUI && !((ButtonlessScrollBarUI)vsbUI).alwaysShowTrack();
    }
  }

  private static final Field decrButtonField = ReflectionUtil.getDeclaredField(BasicScrollBarUI.class, "decrButton");
  private static final Field incrButtonField = ReflectionUtil.getDeclaredField(BasicScrollBarUI.class, "incrButton");

  private static class MyScrollBar extends JBScrollBar implements IdeGlassPane.TopComponent {
    @NonNls private static final String APPLE_LAF_AQUA_SCROLL_BAR_UI_CLASS = "apple.laf.AquaScrollBarUI";
    private ScrollBarUI myPersistentUI;

    private MyScrollBar(@JdkConstants.AdjustableOrientation int orientation) {
      super(orientation);
      setOpaque(false);
    }

    void setPersistentUI(ScrollBarUI ui) {
      myPersistentUI = ui;
      setUI(ui);
    }

    @Override
    public boolean canBePreprocessed(MouseEvent e) {
      return JBScrollPane.canBePreprocessed(e, this);
    }

    @Override
    public void setUI(ScrollBarUI ui) {
      if (myPersistentUI == null) myPersistentUI = ui;
      super.setUI(myPersistentUI);
      setOpaque(false);
    }

    /**
     * This is helper method. It returns h of the top (decrease) scroll bar
     * button. Please note, that it's possible to return real h only if scroll bar
     * is instance of BasicScrollBarUI. Otherwise it returns fake (but good enough :) )
     * value.
     */
    int getDecScrollButtonHeight() {
      ScrollBarUI barUI = getUI();
      Insets insets = getInsets();
      int top = Math.max(0, insets.top);
      if (barUI instanceof ButtonlessScrollBarUI) {
        return top + ((ButtonlessScrollBarUI)barUI).getDecrementButtonHeight();
      }
      if (barUI instanceof BasicScrollBarUI) {
        try {
          JButton decrButtonValue = (JButton)decrButtonField.get(barUI);
          LOG.assertTrue(decrButtonValue != null);
          return top + decrButtonValue.getHeight();
        }
        catch (Exception exc) {
          throw new IllegalStateException(exc);
        }
      }
      return top + 15;
    }

    /**
     * This is helper method. It returns h of the bottom (increase) scroll bar
     * button. Please note, that it's possible to return real h only if scroll bar
     * is instance of BasicScrollBarUI. Otherwise it returns fake (but good enough :) )
     * value.
     */
    int getIncScrollButtonHeight() {
      ScrollBarUI barUI = getUI();
      Insets insets = getInsets();
      if (barUI instanceof ButtonlessScrollBarUI) {
        return insets.top + ((ButtonlessScrollBarUI)barUI).getIncrementButtonHeight();
      }
      if (barUI instanceof BasicScrollBarUI) {
        try {
          JButton incrButtonValue = (JButton)incrButtonField.get(barUI);
          LOG.assertTrue(incrButtonValue != null);
          return insets.bottom + incrButtonValue.getHeight();
        }
        catch (Exception exc) {
          throw new IllegalStateException(exc);
        }
      }
      if (APPLE_LAF_AQUA_SCROLL_BAR_UI_CLASS.equals(barUI.getClass().getName())) {
        return insets.bottom + 30;
      }
      return insets.bottom + 15;
    }

    @Override
    public int getUnitIncrement(int direction) {
      return 5;
    }

    @Override
    public int getBlockIncrement(int direction) {
      return 1;
    }
  }

  private class MyLayeredPane extends JLayeredPane implements Magnificator {
    public MyLayeredPane() {
      setOpaque(true);
      setBackground(UIUtil.TRANSPARENT_COLOR);

      // Enable pinching to zoom
      putClientProperty(Magnificator.CLIENT_PROPERTY_KEY, this);
    }

    // ---- Implements Magnificator ----

    @Override
    public Point magnify(double scale, Point at) {
      setScale(scale * myScale);
      DesignSurface.this.repaint();
      return new Point((int)(at.x * scale), (int)(at.y * scale));
    }

    @Override
    protected void paintComponent(@NonNull Graphics graphics) {
      super.paintComponent(graphics);

      Graphics2D g2d = (Graphics2D)graphics;
      // (x,y) coordinates of the top left corner in the view port
      int tlx = myScrollPane.getHorizontalScrollBar().getValue();
      int tly = myScrollPane.getVerticalScrollBar().getValue();

      paintBackground(g2d, tlx, tly);
    }

    private void paintBackground(@NonNull Graphics2D graphics, int lx, int ly) {
      int width = myScrollPane.getWidth() - RULER_SIZE_PX;
      int height = myScrollPane.getHeight() - RULER_SIZE_PX;
      graphics.setColor(DESIGN_SURFACE_BG);
      graphics.fillRect(RULER_SIZE_PX + lx, RULER_SIZE_PX + ly, width, height);
    }

    private void paintRulers(@NonNull Graphics2D g, int lx, int ly) {
      final Graphics2D graphics = (Graphics2D)g.create();
      try {
        int width = myScrollPane.getWidth();
        int height = myScrollPane.getHeight();

        graphics.setColor(RULER_BG);
        graphics.fillRect(lx, ly, width, RULER_SIZE_PX);
        graphics.fillRect(lx, ly + RULER_SIZE_PX, RULER_SIZE_PX, height - RULER_SIZE_PX);

        graphics.setColor(RULER_TICK_COLOR);

        int x = myScreenX + lx - lx % 100;
        int px2 = x + 10 - 100;
        for (int i = 1; i < 10; i++, px2 += 10) {
          if (px2 < myScreenX + lx - 100) {
            continue;
          }
          graphics.drawLine(px2, ly, px2, ly + RULER_MINOR_TICK_PX);
        }
        // TODO: The rulers need to be updated to track the scale!!!

        for (int px = 0; px < width; px += 100, x += 100) {
          graphics.drawLine(x, ly, x, ly + RULER_MAJOR_TICK_PX);
          px2 = x + 10;
          for (int i = 1; i < 10; i++, px2 += 10) {
            graphics.drawLine(px2, ly, px2, ly + RULER_MINOR_TICK_PX);
          }
        }

        int y = myScreenY + ly - ly % 100;
        int py2 = y + 10 - 100;
        for (int i = 1; i < 10; i++, py2 += 10) {
          if (py2 < myScreenY + ly - 100) {
            continue;
          }
          graphics.drawLine(lx, py2, lx + RULER_MINOR_TICK_PX, py2);
        }
        for (int py = 0; py < height; py += 100, y += 100) {
          graphics.drawLine(lx, y, lx + RULER_MAJOR_TICK_PX, y);
          py2 = y + 10;
          for (int i = 1; i < 10; i++, py2 += 10) {
            graphics.drawLine(lx, py2, lx + RULER_MINOR_TICK_PX, py2);
          }
        }

        graphics.setColor(RULER_TEXT_COLOR);
        graphics.setFont(RULER_TEXT_FONT);
        int xDelta = lx - lx % 100;
        x = myScreenX + 2 + xDelta;
        for (int px = 0; px < width; px += 100, x += 100) {
          graphics.drawString(Integer.toString(px + xDelta), x, ly + RULER_MAJOR_TICK_PX);
        }

        graphics.rotate(-Math.PI / 2);
        int yDelta = ly - ly % 100;
        y = myScreenY - 2 + yDelta;
        for (int py = 0; py < height; py += 100, y += 100) {
          graphics.drawString(Integer.toString(py + yDelta), -y, lx + RULER_MAJOR_TICK_PX);
        }
      }
      finally {
        graphics.dispose();
      }
    }

    private void paintBoundsRectangle(Graphics2D g2d) {
      g2d.setColor(BOUNDS_RECT_COLOR);
      int x = myScreenX;
      int y = myScreenY;
      Dimension preferredSize = myScreenView.getPreferredSize();
      if (preferredSize == null) {
        return;
      }
      double scale = myScreenView.getScale();
      int width = (int)(scale * preferredSize.width);
      int height = (int)(scale * preferredSize.height);

      Stroke prevStroke = g2d.getStroke();
      g2d.setStroke(DASHED_STROKE);

      g2d.drawLine(x, y - BOUNDS_RECT_DELTA, x, y + height + BOUNDS_RECT_DELTA);
      g2d.drawLine(x - BOUNDS_RECT_DELTA, y, x + width + BOUNDS_RECT_DELTA, y);
      g2d.drawLine(x + width, y - BOUNDS_RECT_DELTA, x + width, y + height + BOUNDS_RECT_DELTA);
      g2d.drawLine(x - BOUNDS_RECT_DELTA, y + height, x + width + BOUNDS_RECT_DELTA, y + height);

      g2d.setStroke(prevStroke);
    }

    @Override
    protected void paintChildren(@NonNull Graphics graphics) {
      super.paintChildren(graphics); // paints the screen

      Graphics2D g2d = (Graphics2D)graphics;

      // (x,y) coordinates of the top left corner in the view port
      int tlx = myScrollPane.getHorizontalScrollBar().getValue();
      int tly = myScrollPane.getVerticalScrollBar().getValue();

      Composite oldComposite = g2d.getComposite();

      RenderResult result = myScreenView.getResult();
      boolean paintedFrame = false;
      if (myDeviceFrames && result != null && result.getRenderedImage() != null) {
        Configuration configuration = myScreenView.getConfiguration();
        Device device = configuration.getDevice();
        State deviceState = configuration.getDeviceState();
        DeviceArtPainter painter = DeviceArtPainter.getInstance();
        if (device != null && painter.hasDeviceFrame(device) && deviceState != null) {
          paintedFrame = true;
          g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.8f));
          painter.paintFrame(g2d, device, deviceState.getOrientation(), true, myScreenX, myScreenY,
                             (int)(myScale * result.getRenderedImage().getHeight()));
        }
      }

      if (paintedFrame) {
        // Only use alpha on the ruler bar if overlaying the device art
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.9f));
      } else {
        // Only show bounds dashed lines when there's no device
        paintBoundsRectangle(g2d);
      }

      g2d.setComposite(oldComposite);

      for (Layer layer : myLayers) {
        if (!layer.isHidden()) {
          layer.paint(g2d);
        }
      }

      // Temporary overlays:
      List<Layer> layers = myInteractionManager.getLayers();
      if (layers != null) {
        for (Layer layer : layers) {
          if (!layer.isHidden()) {
            layer.paint(g2d);
          }
        }
      }

      paintRulers(g2d, tlx, tly);
    }
  }

  private static class GlassPane extends JComponent {
    private static final long EVENT_FLAGS = AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK;

    public GlassPane() {
      enableEvents(EVENT_FLAGS);
    }

    @Override
    public void setEnabled(boolean enabled) {
      super.setEnabled(enabled);
      if (enabled) {
        enableEvents(EVENT_FLAGS);
      }
      else {
        disableEvents(EVENT_FLAGS);
      }
    }

    @Override
    protected void processKeyEvent(KeyEvent event) {
      if (!event.isConsumed()) {
        super.processKeyEvent(event);
      }
    }

    @Override
    protected void processMouseEvent(MouseEvent event) {
      if (event.getID() == MouseEvent.MOUSE_PRESSED) {
        requestFocusInWindow();
      }

      super.processMouseEvent(event);
    }

    @Override
    protected void processMouseMotionEvent(MouseEvent event) {
      super.processMouseMotionEvent(event);
    }
  }
}
