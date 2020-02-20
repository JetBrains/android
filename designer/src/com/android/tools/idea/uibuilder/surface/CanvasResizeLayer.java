package com.android.tools.idea.uibuilder.surface;

import static com.android.tools.idea.uibuilder.graphics.NlConstants.BOUNDS_RECT_DELTA;
import static com.android.tools.idea.uibuilder.graphics.NlConstants.RESIZING_CORNER_COLOR;
import static com.android.tools.idea.uibuilder.graphics.NlConstants.RESIZING_CUE_COLOR;
import static com.android.tools.idea.uibuilder.graphics.NlConstants.RESIZING_HOVERING_SIZE;

import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.surface.Layer;
import com.android.tools.idea.uibuilder.graphics.NlConstants;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import org.jetbrains.annotations.NotNull;

/**
 * Layer to buildDisplayList the canvas resizing cue in the bottom-right corner of the screen view.
 */
public class CanvasResizeLayer extends Layer {
  private final NlDesignSurface myDesignSurface;
  private final ScreenView myScreenView;
  private boolean myIsHovering;

  public CanvasResizeLayer(@NotNull NlDesignSurface designSurface, @NotNull ScreenView screenView) {
    myDesignSurface = designSurface;
    myScreenView = screenView;
  }

  /**
   * Sets the state of this layer according to the mouse hovering at point (x, y).
   * Returns whether that required any modification to the state of the layer.
   */
  @Override
  public void onHover(@SwingCoordinate int x, @SwingCoordinate int y) {
    boolean oldHovering = myIsHovering;
    Dimension size = myScreenView.getScaledContentSize();
    Rectangle resizeZone =
      new Rectangle(myScreenView.getX() + size.width, myScreenView.getY() + size.height, RESIZING_HOVERING_SIZE, RESIZING_HOVERING_SIZE);
    myIsHovering = resizeZone.contains(x, y);
    if (myIsHovering != oldHovering) {
      myDesignSurface.repaint();
    }
  }

  @Override
  public void paint(@NotNull Graphics2D g2d) {
    Dimension size = myScreenView.getScaledContentSize();
    int x = myScreenView.getX();
    int y = myScreenView.getY();

    Graphics2D graphics = (Graphics2D)g2d.create();
    graphics.setStroke(NlConstants.SOLID_STROKE);
    graphics.setColor(myIsHovering ? RESIZING_CORNER_COLOR : RESIZING_CUE_COLOR);
    graphics.drawLine(x + size.width + BOUNDS_RECT_DELTA, y + size.height + 4, x + size.width + 4, y + size.height + BOUNDS_RECT_DELTA);
    graphics.drawLine(x + size.width + BOUNDS_RECT_DELTA, y + size.height + 12, x + size.width + 12, y + size.height + BOUNDS_RECT_DELTA);
    graphics.dispose();
  }

  @Override
  public boolean isVisible() {
    return myScreenView.getScene().isResizeAvailable();
  }
}
