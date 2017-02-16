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
package com.android.tools.idea.uibuilder.surface;

import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.State;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.ddms.screenshot.DeviceArtPainter;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.uibuilder.editor.ActionManager;
import com.android.tools.idea.uibuilder.editor.NlActionManager;
import com.android.tools.idea.uibuilder.mockup.editor.MockupEditor;
import com.android.tools.idea.uibuilder.model.*;
import com.android.tools.idea.uibuilder.scene.Scene;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.lang.ref.WeakReference;

import static com.android.tools.idea.uibuilder.graphics.NlConstants.*;

/**
 * The {@link DesignSurface} for the layout editor, which contains the full background, rulers, one
 * or more device renderings, etc
 */
public class NlDesignSurface extends DesignSurface {
  public enum ScreenMode {
    SCREEN_ONLY(ScreenView.ScreenViewType.NORMAL),
    BLUEPRINT_ONLY(ScreenView.ScreenViewType.BLUEPRINT),
    BOTH(ScreenView.ScreenViewType.NORMAL);

    private final ScreenView.ScreenViewType myScreenViewType;

    ScreenMode(@NotNull ScreenView.ScreenViewType screenViewType) {
      myScreenViewType = screenViewType;
    }

    @NotNull
    public ScreenMode next() {
      ScreenMode[] values = values();
      return values[(ordinal() + 1) % values.length];
    }

    @NotNull
    private ScreenView.ScreenViewType getScreenViewType() {
      return myScreenViewType;
    }

    private static final String SCREEN_MODE_PROPERTY = "NlScreenMode";

    @NotNull
    public static ScreenMode loadDefault() {
      String modeName = PropertiesComponent.getInstance().getValue(SCREEN_MODE_PROPERTY);
      for (ScreenMode mode : values()) {
        if (mode.name().equals(modeName)) {
          return mode;
        }
      }
      return BOTH;
    }

    public static void saveDefault(@NotNull ScreenMode mode) {
      PropertiesComponent.getInstance().setValue(SCREEN_MODE_PROPERTY, mode.name());
    }
  }

  @NotNull private static ScreenMode ourDefaultScreenMode = ScreenMode.loadDefault();

  @NotNull private ScreenMode myScreenMode = ourDefaultScreenMode;
  @Nullable private ScreenView myBlueprintView;
  @SwingCoordinate private int myScreenX = RULER_SIZE_PX + DEFAULT_SCREEN_OFFSET_X;
  @SwingCoordinate private int myScreenY = RULER_SIZE_PX + DEFAULT_SCREEN_OFFSET_Y;
  private boolean myIsCanvasResizing = false;
  private boolean myStackVertically;
  private boolean myMockupVisible;
  private MockupEditor myMockupEditor;
  private boolean myCentered;
  @Nullable private ScreenView myScreenView;
  private final boolean myInPreview;
  private WeakReference<PanZoomPanel> myPanZoomPanel = new WeakReference<>(null);

  public NlDesignSurface(@NotNull Project project, boolean inPreview) {
    super(project);
    myInPreview = inPreview;
  }

  public boolean isPreviewSurface() {
    return myInPreview;
  }

  /**
   * Tells this surface to resize mode. While on resizing mode, the views won't be auto positioned.
   * This can be disabled to avoid moving the screens around when the user is resizing the canvas. See {@link CanvasResizeInteraction}
   *
   * @param isResizing true to enable the resize mode
   */
  public void setResizeMode(boolean isResizing) {
    myIsCanvasResizing = isResizing;
  }

  /**
   * Returns whether this surface is currently in resize mode or not. See {@link #setResizeMode(boolean)}
   */
  public boolean isCanvasResizing() {
    return myIsCanvasResizing;
  }

  @Override
  public boolean isLayoutDisabled() {
    return myIsCanvasResizing;
  }

  @Override
  public void activate() {
    super.activate();
    showPanZoomPanelIfRequired();
  }

  @NotNull
  public ScreenMode getScreenMode() {
    return myScreenMode;
  }

  public void setScreenMode(@NotNull ScreenMode screenMode, boolean setAsDefault) {
    if (setAsDefault) {
      if (ourDefaultScreenMode != screenMode) {
        //noinspection AssignmentToStaticFieldFromInstanceMethod
        ourDefaultScreenMode = screenMode;

        ScreenMode.saveDefault(screenMode);
      }
    }

    if (screenMode != myScreenMode) {
      // If we're going from 1 screens to 2 or back from 2 to 1, must adjust the zoom
      // to-fit the screen(s) in the surface
      boolean adjustZoom = screenMode == ScreenMode.BOTH || myScreenMode == ScreenMode.BOTH;
      myScreenMode = screenMode;

      if (myScreenView != null) {
        NlModel model = myScreenView.getModel();
        setModel(null);
        setModel(model);
        if (adjustZoom) {
          zoomToFit();
        }
      }
    }
  }

  private void addLayers(@NotNull NlModel model) {
    assert myScreenView != null;

    MyBottomLayer bottom = new MyBottomLayer();
    myLayers.add(bottom);
    switch (myScreenMode) {
      case SCREEN_ONLY:
        addScreenLayers();
        break;
      case BLUEPRINT_ONLY:
        addBlueprintLayers(myScreenView);
        break;
      case BOTH:
        myBlueprintView = new ScreenView(this, ScreenView.ScreenViewType.BLUEPRINT, model);
        myBlueprintView.setLocation(myScreenX + myScreenView.getPreferredSize().width + 10, myScreenY);

        addScreenLayers();
        addBlueprintLayers(myBlueprintView);

        break;
      default:
        assert false : myScreenMode;
    }
    myLayers.add(new MyTopLayer(bottom));
  }

  private void addScreenLayers() {
    assert myScreenView != null;

    myLayers.add(new ScreenViewLayer(myScreenView));
    myLayers.add(new SelectionLayer(myScreenView));

    if (myScreenView.getModel().getType().isLayout()) {
      myLayers.add(new ConstraintsLayer(this, myScreenView, true));
    }

    myLayers.add(new SceneLayer(this, myScreenView, false));
    myLayers.add(new WarningLayer(myScreenView));
    if (getLayoutType().isSupportedByDesigner()) {
      myLayers.add(new CanvasResizeLayer(this, myScreenView));
    }
  }

  private void addBlueprintLayers(@NotNull ScreenView view) {
    myLayers.add(new SelectionLayer(view));
    myLayers.add(new MockupLayer(view));
    myLayers.add(new CanvasResizeLayer(this, view));
    myLayers.add(new SceneLayer(this, view, true));
  }

  @Nullable
  @Override
  public ScreenView getCurrentSceneView() {
    return myScreenView;
  }

  @Override
  @Nullable
  public SceneView getSceneView(@SwingCoordinate int x, @SwingCoordinate int y) {
    // Currently only a single screen view active in the canvas.
    if (myBlueprintView != null && x >= myBlueprintView.getX() && y >= myBlueprintView.getY()) {
      return myBlueprintView;
    }
    return myScreenView;
  }

  /**
   * Return the ScreenView under the given position
   *
   * @param x
   * @param y
   * @return the ScreenView, or null if we are not above one.
   */
  @Nullable
  ScreenView getHoverScreenView(@SwingCoordinate int x, @SwingCoordinate int y) {
    if (myBlueprintView != null
        && x >= myBlueprintView.getX() && x <= myBlueprintView.getX() + myBlueprintView.getSize().width
        && y >= myBlueprintView.getY() && y <= myBlueprintView.getY() + myBlueprintView.getSize().height) {
      return myBlueprintView;
    }
    if (myScreenView != null
        && x >= myScreenView.getX() && x <= myScreenView.getX() + myScreenView.getSize().width
        && y >= myScreenView.getY() && y <= myScreenView.getY() + myScreenView.getSize().height) {
      return myScreenView;
    }
    return null;
  }

  @Nullable
  public ScreenView getBlueprintView() {
    return myBlueprintView;
  }

  @Override
  public Dimension getScrolledAreaSize() {
    if (myScreenView == null) {
      return null;
    }
    Dimension size = myScreenView.getSize();
    // TODO: Account for the size of the blueprint screen too? I should figure out if I can automatically make it jump
    // to the side or below based on the form factor and the available size
    Dimension dimension = new Dimension(size.width + 2 * DEFAULT_SCREEN_OFFSET_X,
                                        size.height + 2 * DEFAULT_SCREEN_OFFSET_Y);
    if (myScreenMode == ScreenMode.BOTH) {
      if (isStackVertically()) {
        dimension.setSize(dimension.getWidth(),
                          dimension.getHeight() + size.height + SCREEN_DELTA);
      }
      else {
        dimension.setSize(dimension.getWidth() + size.width + SCREEN_DELTA,
                          dimension.getHeight());
      }
    }
    return dimension;
  }

  /**
   * Returns true if we want to arrange screens vertically instead of horizontally
   */
  private static boolean isVerticalScreenConfig(int availableWidth, int availableHeight, @NotNull Dimension preferredSize) {
    boolean stackVertically = preferredSize.width > preferredSize.height;
    if (availableWidth > 10 && availableHeight > 3 * availableWidth / 2) {
      stackVertically = true;
    }
    return stackVertically;
  }

  public void setCentered(boolean centered) {
    myCentered = centered;
  }

  @Override
  protected ActionManager createActionManager() {
    return new NlActionManager(this);
  }

  /**
   * <p>
   * If type is {@link ZoomType#IN}, zoom toward the given x and y coordinates
   * (relative to {@link #getLayeredPane()})
   * </p><p>
   * If x or y are negative, zoom toward the selected component if there is one otherwise
   * zoom toward the center of the viewport.
   * </p><p>
   * For all other types of zoom see {@link DesignSurface#zoom(ZoomType, int, int)}
   * </p>
   *
   * @param type Type of zoom to execute
   * @param x    Coordinate where the zoom will be centered
   * @param y    Coordinate where the zoom will be centered
   * @see DesignSurface#zoom(ZoomType, int, int)
   */
  @Override
  public void zoom(@NotNull ZoomType type, int x, int y) {
    if (type == ZoomType.IN && (x < 0 || y < 0)
        && myScreenView != null && !myScreenView.getSelectionModel().isEmpty()) {
      NlComponent component = myScreenView.getSelectionModel().getPrimary();
      if (component != null) {
        x = Coordinates.getSwingX(myScreenView, component.getMidpointX());
        y = Coordinates.getSwingY(myScreenView, component.getMidpointY());
      }
    }
    super.zoom(type, x, y);
  }

  @Override
  protected void layoutContent() {
    if (myScreenView == null) {
      return;
    }
    Dimension screenViewSize = myScreenView.getSize();

    // Position primary screen
    int availableWidth = myScrollPane.getWidth();
    int availableHeight = myScrollPane.getHeight();
    myStackVertically = isVerticalScreenConfig(availableWidth, availableHeight, screenViewSize);

    // If we are resizing the canvas, do not relocate the primary screen
    if (!myIsCanvasResizing) {
      if (myCentered && availableWidth > 10 && availableHeight > 10) {
        int requiredWidth = screenViewSize.width;
        if (myScreenMode == ScreenMode.BOTH && !myStackVertically) {
          requiredWidth += SCREEN_DELTA;
          requiredWidth += screenViewSize.width;
        }
        myScreenX = Math.max((availableWidth - requiredWidth) / 2, RULER_SIZE_PX + DEFAULT_SCREEN_OFFSET_X);

        int requiredHeight = screenViewSize.height;
        if (myScreenMode == ScreenMode.BOTH && myStackVertically) {
          requiredHeight += SCREEN_DELTA;
          requiredHeight += screenViewSize.height;
        }
        myScreenY = Math.max((availableHeight - requiredHeight) / 2, RULER_SIZE_PX + DEFAULT_SCREEN_OFFSET_Y);
      }
      else {
        if (myDeviceFrames) {
          myScreenX = RULER_SIZE_PX + 2 * DEFAULT_SCREEN_OFFSET_X;
          myScreenY = RULER_SIZE_PX + 2 * DEFAULT_SCREEN_OFFSET_Y;
        }
        else {
          myScreenX = RULER_SIZE_PX + DEFAULT_SCREEN_OFFSET_X;
          myScreenY = RULER_SIZE_PX + DEFAULT_SCREEN_OFFSET_Y;
        }
      }
    }
    myScreenView.setLocation(myScreenX, myScreenY);

    // Position blueprint view
    if (myBlueprintView != null) {

      if (myStackVertically) {
        // top/bottom stacking
        myBlueprintView.setLocation(myScreenX, myScreenY + screenViewSize.height + SCREEN_DELTA);
      }
      else {
        // left/right ordering
        myBlueprintView.setLocation(myScreenX + screenViewSize.width + SCREEN_DELTA, myScreenY);
      }
    }
    if (myScreenView != null) {
      Scene scene = myScreenView.getScene();
      scene.needsRebuildList();
    }
    if (myBlueprintView != null) {
      Scene scene = myBlueprintView.getScene();
      scene.needsRebuildList();
    }
  }

  @Override
  @SwingCoordinate
  protected int getContentOriginX() {
    return myScreenX;
  }

  @Override
  @SwingCoordinate
  protected int getContentOriginY() {
    return myScreenY;
  }

  public boolean isStackVertically() {
    return myStackVertically;
  }

  @Override
  public void doSetModel(@Nullable NlModel model) {
    if (model == null && myScreenView == null) {
      return;
    }
    myScreenView = null;
    myLayers.clear();
    if (model != null) {
      myScreenView = new ScreenView(this, ScreenView.ScreenViewType.NORMAL, model);

      getLayeredPane().setPreferredSize(myScreenView.getPreferredSize());

      NlLayoutType layoutType = myScreenView.getModel().getType();

      if (layoutType.equals(NlLayoutType.MENU) || layoutType.equals(NlLayoutType.PREFERENCE_SCREEN)) {
        myScreenMode = ScreenMode.SCREEN_ONLY;
      }

      myScreenView.setType(myScreenMode.getScreenViewType());

      addLayers(model);
      layoutContent();
    }
    else {
      myScreenView = null;
      myBlueprintView = null;
    }
  }

  @Override
  @NotNull
  public Dimension getContentSize(@Nullable Dimension dimension) {
    if (dimension == null) {
      dimension = new Dimension();
    }
    if (myScreenMode == ScreenMode.BOTH
        && myScreenView != null && myBlueprintView != null) {
      if (isStackVertically()) {
        dimension.setSize(
          myScreenView.getSize().getWidth(),
          myScreenView.getSize().getHeight() + myBlueprintView.getSize().getHeight()
        );
      }
      else {
        dimension.setSize(
          myScreenView.getSize().getWidth() + myBlueprintView.getSize().getWidth(),
          myScreenView.getSize().getHeight()
        );
      }
    }
    else if (getCurrentSceneView() != null) {
      dimension.setSize(
        getCurrentSceneView().getSize().getWidth(),
        getCurrentSceneView().getSize().getHeight());
    }
    return dimension;
  }

  @Override
  public Configuration getConfiguration() {
    return myScreenView != null ? myScreenView.getConfiguration() : null;
  }

  @Override
  protected Dimension getDefaultOffset() {
    return new Dimension(2 * DEFAULT_SCREEN_OFFSET_X + RULER_SIZE_PX, 2 * DEFAULT_SCREEN_OFFSET_Y + RULER_SIZE_PX);
  }

  @SwingCoordinate
  @Override
  protected Dimension getPreferredContentSize(int availableWidth, int availableHeight) {
    Dimension preferredSize = myScreenView.getPreferredSize();
    int requiredWidth = preferredSize.width;
    int requiredHeight = preferredSize.height;
    if (myScreenMode == ScreenMode.BOTH) {
      if (isVerticalScreenConfig(availableWidth, availableHeight, preferredSize)) {
        requiredHeight *= 2;
        requiredHeight += SCREEN_DELTA;
      }
      else {
        requiredWidth *= 2;
        requiredWidth += SCREEN_DELTA;
      }
    }

    return new Dimension(requiredWidth, requiredHeight);
  }

  @Override
  protected Point translateCoordinate(@NotNull Point coord) {
    return Coordinates.getAndroidCoordinate(myScreenView, coord);
  }

  private class MyBottomLayer extends Layer {
    private boolean myPaintedFrame;

    @Override
    public void paint(@NotNull Graphics2D g2d) {
      Composite oldComposite = g2d.getComposite();

      RenderResult result = myScreenView.getResult();
      myPaintedFrame = false;
      if (myDeviceFrames && result != null && result.hasImage()) {
        Configuration configuration = myScreenView.getConfiguration();
        Device device = configuration.getDevice();
        State deviceState = configuration.getDeviceState();
        DeviceArtPainter painter = DeviceArtPainter.getInstance();
        if (device != null && painter.hasDeviceFrame(device) && deviceState != null) {
          myPaintedFrame = true;
          g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.8f));
          painter.paintFrame(g2d, device, deviceState.getOrientation(), true, myScreenX, myScreenY,
                             (int)(myScale * result.getRenderedImage().getHeight()));
        }
      }

      g2d.setComposite(oldComposite);

      if (!getLayoutType().isSupportedByDesigner()) {
        return;
      }

      if (!myPaintedFrame) {
        // Only show bounds dashed lines when there's no device
        paintBoundsRectangle(g2d);
      }
    }

    private void paintBoundsRectangle(Graphics2D g2d) {
      if (myScreenView == null) {
        return;
      }

      g2d.setColor(BOUNDS_RECT_COLOR);
      int x = myScreenX;
      int y = myScreenY;
      Dimension size = myScreenView.getSize();

      Stroke prevStroke = g2d.getStroke();
      g2d.setStroke(DASHED_STROKE);

      Shape screenShape = myScreenView.getScreenShape();
      if (screenShape == null) {
        g2d.drawLine(x - 1, y - BOUNDS_RECT_DELTA, x - 1, y + size.height + BOUNDS_RECT_DELTA);
        g2d.drawLine(x - BOUNDS_RECT_DELTA, y - 1, x + size.width + BOUNDS_RECT_DELTA, y - 1);
        g2d.drawLine(x + size.width, y - BOUNDS_RECT_DELTA, x + size.width, y + size.height + BOUNDS_RECT_DELTA);
        g2d.drawLine(x - BOUNDS_RECT_DELTA, y + size.height, x + size.width + BOUNDS_RECT_DELTA, y + size.height);
      }
      else {
        g2d.draw(screenShape);
      }

      g2d.setStroke(prevStroke);
    }
  }

  private class MyTopLayer extends Layer {

    private MyBottomLayer myBottomLayer;

    public MyTopLayer(@NotNull MyBottomLayer bottom) {
      myBottomLayer = bottom;
    }

    @Override
    public void paint(@NotNull Graphics2D g2d) {
      if (getLayoutType().isSupportedByDesigner()) {
        Composite oldComposite = g2d.getComposite();
        if (myBottomLayer.myPaintedFrame) {
          // Only use alpha on the ruler bar if overlaying the device art
          g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.9f));
        }
        // Paint rulers on top of whatever is under the scroll panel
        // (x,y) coordinates of the top left corner in the view port
        int tlx = myScrollPane.getHorizontalScrollBar().getValue();
        int tly = myScrollPane.getVerticalScrollBar().getValue();
        paintRulers(g2d, tlx, tly);
        g2d.setComposite(oldComposite);
      }
    }

    private void paintRulers(@NotNull Graphics2D g, int scrolledX, int scrolledY) {
      if (myScale < 0) {
        return;
      }

      final Graphics2D graphics = (Graphics2D)g.create();
      try {
        int width = myScrollPane.getWidth();
        int height = myScrollPane.getHeight();

        graphics.setColor(RULER_BG);
        graphics.fillRect(scrolledX, scrolledY, width, RULER_SIZE_PX);
        graphics.fillRect(scrolledX, scrolledY + RULER_SIZE_PX, RULER_SIZE_PX, height - RULER_SIZE_PX);

        graphics.setColor(RULER_TICK_COLOR);
        graphics.setStroke(SOLID_STROKE);
        graphics.setFont(RULER_TEXT_FONT);

        // Distance between two minor ticks (corrected with the current scale)
        int minorTickDistance = Math.max((int)Math.round(RULER_TICK_DISTANCE * myScale), 1);
        // If we keep reducing the scale, at some point we only buildDisplayList half of the minor ticks
        int tickIncrement = (minorTickDistance > RULER_MINOR_TICK_MIN_DIST_PX) ? 1 : 2;
        int labelWidth = RULER_TEXT_FONT.getStringBounds("0000", graphics.getFontRenderContext()).getBounds().width;
        // Only display the text if it fits between major ticks
        boolean displayText = labelWidth < minorTickDistance * 10;

        // Get the first tick that is within the viewport
        int firstVisibleTickX = scrolledX / minorTickDistance - Math.min(myScreenX / minorTickDistance, 10);
        for (int i = firstVisibleTickX; i * minorTickDistance < width + scrolledX; i += tickIncrement) {
          if (i == -10) {
            continue;
          }

          int tickPosition = i * minorTickDistance + myScreenX;
          boolean majorTick = i >= 0 && (i % 10) == 0;

          graphics.drawLine(tickPosition, scrolledY, tickPosition, scrolledY + (majorTick ? RULER_MAJOR_TICK_PX : RULER_MINOR_TICK_PX));

          if (displayText && majorTick) {
            graphics.setColor(RULER_TEXT_COLOR);
            graphics.drawString(Integer.toString(i * 10), tickPosition + 2, scrolledY + RULER_MAJOR_TICK_PX);
            graphics.setColor(RULER_TICK_COLOR);
          }
        }

        graphics.rotate(-Math.PI / 2);
        int firstVisibleTickY = scrolledY / minorTickDistance - Math.min(myScreenY / minorTickDistance, 10);
        for (int i = firstVisibleTickY; i * minorTickDistance < height + scrolledY; i += tickIncrement) {
          if (i == -10) {
            continue;
          }

          int tickPosition = i * minorTickDistance + myScreenY;
          boolean majorTick = i >= 0 && (i % 10) == 0;

          //noinspection SuspiciousNameCombination (we rotate the drawing 90 degrees)
          graphics.drawLine(-tickPosition, scrolledX, -tickPosition, scrolledX + (majorTick ? RULER_MAJOR_TICK_PX : RULER_MINOR_TICK_PX));

          if (displayText && majorTick) {
            graphics.setColor(RULER_TEXT_COLOR);
            graphics.drawString(Integer.toString(i * 10), -tickPosition + 2, scrolledX + RULER_MAJOR_TICK_PX);
            graphics.setColor(RULER_TICK_COLOR);
          }
        }
      }
      finally {
        graphics.dispose();
      }
    }
  }

  public void setMockupVisible(boolean mockupVisible) {
    myMockupVisible = mockupVisible;
    repaint();
  }

  public boolean isMockupVisible() {
    return myMockupVisible;
  }

  public void setMockupEditor(@Nullable MockupEditor mockupEditor) {
    myMockupEditor = mockupEditor;
  }

  @Nullable
  public MockupEditor getMockupEditor() {
    return myMockupEditor;
  }

  private void setPanZoomPanel(@Nullable PanZoomPanel panZoomPanel) {
    myPanZoomPanel = new WeakReference<>(panZoomPanel);
  }

  @Nullable
  public PanZoomPanel getPanZoomPanel() {
    return myPanZoomPanel.get();
  }

  /**
   * Shows the {@link PanZoomPanel} if the {@link PropertiesComponent} {@link PanZoomPanel#PROP_OPEN} is true
   */
  private void showPanZoomPanelIfRequired() {
    if (PanZoomPanel.isPropertyComponentOpen()) {
      setPanZoomPanelVisible(true);
    }
  }

  /**
   * If show is true, displays the {@link PanZoomPanel}.
   *
   * If the {@link DesignSurface} is not shows yet, it register a callback that will show the {@link PanZoomPanel}
   * once the {@link DesignSurface} is visible, otherwise it shows it directly.
   *
   * @param show
   */
  public void setPanZoomPanelVisible(boolean show) {
    PanZoomPanel panel = myPanZoomPanel.get();
    if (show) {
      if (panel == null) {
        panel = new PanZoomPanel(this);
      }
      setPanZoomPanel(panel);
      if (isShowing()) {
        panel.showPopup();
      }
      else {
        PanZoomPanel finalPanel = panel;
        ComponentAdapter adapter = new ComponentAdapter() {
          @Override
          public void componentShown(ComponentEvent e) {
            finalPanel.showPopup();
            removeComponentListener(this);
          }
        };
        addComponentListener(adapter);
      }
    }
    else if (panel != null) {
      panel.closePopup();
    }
  }
}
