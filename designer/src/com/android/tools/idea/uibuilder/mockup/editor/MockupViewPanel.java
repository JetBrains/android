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
package com.android.tools.idea.uibuilder.mockup.editor;

import com.android.tools.idea.uibuilder.mockup.Mockup;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.*;

/**
 * Panel that show the Mockup in the Editor window
 */
public class MockupViewPanel extends JPanel {

  public static final int MAX_SCALE = 10;
  public static final float MIN_SCALE = 0.95f;
  private static RenderingHints HQ_RENDERING = new RenderingHints(null);

  static {
    HQ_RENDERING.put(RenderingHints.KEY_ANTIALIASING,
                     RenderingHints.VALUE_ANTIALIAS_ON);
    HQ_RENDERING.put(RenderingHints.KEY_RENDERING,
                     RenderingHints.VALUE_RENDER_QUALITY);
  }

  private final SelectionLayer mySelectionLayer;
  private Mockup myMockup;

  private float myZoom = MIN_SCALE;
  @Nullable private BufferedImage myDisplayedImage;
  @Nullable private BufferedImage myImage;
  private boolean myDisplayOnlyCroppedRegion = true;
  private boolean mySelectionMode = true;
  PanZoomManager myPanZoomManager;
  private Mockup.MockupModelListener myMockupListener;

  private List<SelectionListener> mySelectionListeners = new ArrayList<>();
  AffineTransform myImageTransform = new AffineTransform();
  Point2D.Float myImageOffset = new Point2D.Float();
  private boolean myHqRendering = true;


  /**
   * Listener to notify the tools when a selection ended
   */
  public interface SelectionListener {

    /**
     * Called when a selection is started on the {@link SelectionLayer}
     * The given coordinate are in displayed Image coordinate system.
     *
     * @param mockupViewPanel The panel on which the selection is being done
     * @param x               x origin of the selection in displayed image coordinate system.
     * @param y               y origin of the selection in displayed image coordinate system.
     */
    void selectionStarted(MockupViewPanel mockupViewPanel, int x, int y);

    /**
     * Called when a selection is started on the {@link SelectionLayer}
     * The given coordinate are in the displayed image coordinate system.
     *
     * @param mockupViewPanel The panel on which the selection is being done
     * @param selection       the selection in the the displayed image coordinate system
     **/
    void selectionEnded(MockupViewPanel mockupViewPanel, Rectangle selection);
  }


  /**
   * Create a new MockupView Panel displaying the given mockup
   *
   * @param mockup       the mockup to display
   * @param mockupEditor
   */


  public MockupViewPanel(@NotNull Mockup mockup, @NotNull MockupEditor mockupEditor) {

    myMockupListener = this::updateDisplayedImage;
    mockupEditor.addListener(this::update);
    setLayout(new MyLayoutManager());
    setBackground(JBColor.background().brighter());
    setMockup(mockup);
    updateDisplayedImage(mockup);
    mySelectionLayer = new SelectionLayer(this, myImageTransform);
    addMouseListener(new MyMouseInteraction());
    addMouseMotionListener(new MyMouseInteraction());
    addMouseWheelListener(new MyMouseInteraction());
    addComponentListener(new MyComponentListener());
    myDisplayedImage = myImage;

    myPanZoomManager = new PanZoomManager();
    resetState();
  }

  private void update(Mockup mockup) {
    resetState();
    setMockup(mockup);
    repaint();
  }

  /**
   * Update the displayed image.
   *
   * @param mockup
   */
  private void updateDisplayedImage(@NotNull Mockup mockup) {
    myImage = mockup.getImage();
    if (myImage != null) {
      myDisplayedImage = createDisplayedImage(myImage, mockup.getRealCropping());
    }
    repaint();
  }

  public void resetState() {
    removeAll();
    setDisplayOnlyCroppedRegion(true);
    getSelectionLayer().clearSelection();
    getSelectionLayer().setFixedRatio(false);
    repaint();
  }

  public void setMockup(Mockup mockup) {
    if (mockup != myMockup) {
      if (myMockup != null) {
        myMockup.removeMockupListener(myMockupListener);
      }
      myMockup = mockup;
      myMockup.addMockupListener(myMockupListener);
    }
    updateDisplayedImage(mockup);
  }

  /**
   * Set if the panel should display only the cropped area of the mockup or the whole image
   *
   * @param displayOnlyCroppedRegion If true,only the cropped area of the mockup will be displayed.
   *                                 If false, the whole image will be displayed
   */
  public void setDisplayOnlyCroppedRegion(boolean displayOnlyCroppedRegion) {
    if (myDisplayOnlyCroppedRegion != displayOnlyCroppedRegion) {
      myDisplayOnlyCroppedRegion = displayOnlyCroppedRegion;
      if (myImage != null) {
        myDisplayedImage = createDisplayedImage(myImage, myMockup.getRealCropping());
      }
      repaint();
    }
  }

  /**
   * Allows the user to make selection on the image.
   *
   * @param selectionMode if true activate the selection mode
   */
  public void setSelectionMode(boolean selectionMode) {
    mySelectionMode = selectionMode;
    mySelectionLayer.clearSelection();
    repaint();
  }


  @Override
  public void paintComponent(Graphics g) {
    super.paintComponent(g);
    final Graphics2D g2d = ((Graphics2D)g.create());
    if (myHqRendering) {
      g2d.setRenderingHints(HQ_RENDERING);
    }
    paintMockup(g2d);
    g2d.dispose();
  }

  /**
   * Paint the mockup using the provided graphic context
   *
   * @param g2d the graphic context
   */
  private void paintMockup(Graphics2D g2d) {
    if (myDisplayedImage == null) {
      return;
    }
    final AffineTransform tx = g2d.getTransform();
    int sw = getWidth();
    int sh = getHeight();
    int iw = myDisplayedImage.getWidth();
    int ih = myDisplayedImage.getHeight();
    float scale = (float)(myZoom * min(sw / (double)iw, sh / (double)ih));
    updateTransform(sw, sh, iw, ih, scale);
    if (!isValid()) {
      doLayout();
    }
    g2d.transform(myImageTransform);
    g2d.drawImage(myDisplayedImage, 0, 0, iw, ih, null);
    painScaled(g2d);
    g2d.setTransform(tx);
  }

  private void updateTransform(int sw, int sh, int iw, int ih, float scale) {
    myImageTransform.setToIdentity();
    myImageTransform.translate(
      (1 + myImageOffset.x) * (sw - iw * scale) / 2,
      (1 + myImageOffset.y) * (sh - ih * scale) / 2);
    myImageTransform.scale(scale, scale);
  }

  private void painScaled(Graphics2D g2d) {
    if (mySelectionMode) {
      mySelectionLayer.paint(g2d);
    }
  }

  /**
   * Create the image that will be displayed on the panel and scaled to fit it.
   * The displayed image is the full image if {@link #setDisplayOnlyCroppedRegion(boolean)} is true
   * or if cropping bounds matches the image bounds. Otherwise it is the cropped region of the mockup
   *
   * @param image    Mockup's image {@link Mockup#getImage()}
   * @param cropping Mockup cropping area : {@link Mockup#getRealCropping()}
   * @return the scaled image
   */
  @Nullable
  private BufferedImage createDisplayedImage(@Nullable BufferedImage image, @NotNull Rectangle cropping) {
    if (image == null) {
      return null;
    }
    BufferedImage displayedImage;
    if (myDisplayOnlyCroppedRegion) {
      // Ensure the cropping is inside the image bounds
      Rectangle2D.intersect(cropping, new Rectangle(image.getWidth(), image.getHeight()), cropping);
      displayedImage = image.getSubimage(cropping.x, cropping.y, cropping.width, cropping.height);
    }
    else {
      displayedImage = image;
    }
    return displayedImage;
  }

  /**
   * Set the selection of the {@link SelectionLayer} to match the mockup crop
   */
  public void setSelectionToMockupCrop() {
    mySelectionLayer.setSelection(myMockup.getRealCropping());
  }

  /**
   * Add a {@link SelectionListener}
   *
   * @param selectionListener
   */
  public void addSelectionListener(SelectionListener selectionListener) {
    if (!mySelectionListeners.contains(selectionListener)) {
      mySelectionListeners.add(selectionListener);
    }
  }

  public void removeSelectionListener(SelectionListener listener) {
    mySelectionListeners.remove(listener);
  }

  public SelectionLayer getSelectionLayer() {
    return mySelectionLayer;
  }

  /**
   * Ensure that the current selection is resized when this panel is resized
   */
  private void resizeSelection() {
    if (mySelectionMode) {
      mySelectionLayer.contentResized();
    }
  }

  /**
   * Convert the selection in the Mockup's image coordinate system,
   * and notify the listener with the converted selection
   */
  private void notifySelectionEnded() {
    for (int i = 0; i < mySelectionListeners.size(); i++) {
      mySelectionListeners.get(i).selectionEnded(this, mySelectionLayer.getSelection());
    }
  }

  private void notifySelectionStarted(int x, int y) {
    for (int i = 0; i < mySelectionListeners.size(); i++) {
      mySelectionListeners.get(i).selectionStarted(this, x, y);
    }
  }

  private class MyComponentListener implements ComponentListener {

    @Override
    public void componentResized(ComponentEvent e) {
      if (mySelectionMode) {
        resizeSelection();
      }
    }

    @Override
    public void componentMoved(ComponentEvent e) {
    }

    @Override
    public void componentShown(ComponentEvent e) {
    }

    @Override
    public void componentHidden(ComponentEvent e) {
      myPanZoomManager.stop();
    }
  }

  private class MyMouseInteraction extends MouseAdapter {

    @Override
    public void mousePressed(MouseEvent e) {
      if (isPanAction(e)) {
        myPanZoomManager.mousePressed(e);
        return;
      }
      final Point origin = new Point(e.getPoint());
      myImageTransform.transform(e.getPoint(), e.getPoint());
      if (mySelectionMode) {
        toSelectionLayer(e);
        notifySelectionStarted(e.getX(), e.getY());
        repaint();
      }
      e.getPoint().setLocation(origin);
    }

    /**
     * Pass the mouse event to the SelectionLayer
     *
     * @param e MouseEvent
     */
    private void toSelectionLayer(MouseEvent e) {
      if (myDisplayedImage == null) {
        mySelectionLayer.setBounds(0, 0, getWidth(), getHeight());
      }
      else {
        mySelectionLayer.setBounds(0, 0, myDisplayedImage.getWidth(), myDisplayedImage.getHeight());
      }
      mySelectionLayer.mousePressed(e);
    }

    @Override
    public void mouseDragged(MouseEvent e) {
      if (isPanAction(e)) {
        myPanZoomManager.mouseDragged(e);
      }
      else if (mySelectionMode) {
        mySelectionLayer.mouseDragged(e);
      }
    }

    private boolean isPanAction(MouseEvent e) {
      return SwingUtilities.isMiddleMouseButton(e)
             || (e.getModifiers() & InputEvent.SHIFT_MASK) == InputEvent.SHIFT_MASK;
    }

    @Override
    public void mouseReleased(MouseEvent e) {
      if (isPanAction(e)) {
        return;
      }
      if (mySelectionMode) {
        mySelectionLayer.mouseReleased(e);
        notifySelectionEnded();
      }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
      if (mySelectionMode) {
        mySelectionLayer.mouseMoved(e);
      }
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
      if (isPanAction(e)) {
        myPanZoomManager.zoomAnimate(e.getWheelRotation(), e.getPoint());
      }
    }
  }

  /**
   * Displays the components next to the selection, inside the bounds of the panel
   */
  private class MyLayoutManager implements LayoutManager {

    public static final int H_GAP = 10;

    @Override
    public void addLayoutComponent(String name, Component comp) {
    }

    @Override
    public void removeLayoutComponent(Component comp) {
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
      return null;
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
      return null;
    }

    @Override
    public void layoutContainer(Container parent) {
      if (parent.getComponentCount() == 0) {
        return;
      }
      final Component component = parent.getComponent(0);
      final Rectangle selection = mySelectionLayer.getSelection();

      if (!selection.isEmpty()) {
        final int selectionWidth = (int)round(selection.width * myImageTransform.getScaleX());

        float[] selectionOrigin = new float[]{selection.x, selection.y};
        myImageTransform.transform(selectionOrigin, 0, selectionOrigin, 0, 1);

        final int selectionX = round(selectionOrigin[0]);
        final int selectionY = round(selectionOrigin[1]);

        final Dimension preferredSize = component.getPreferredSize();
        int x = selectionX + selectionWidth + H_GAP;
        final int width = preferredSize.width;
        final int height = preferredSize.height;
        if (x + width > getWidth()) {
          x = selectionX + selectionWidth - H_GAP - width;
        }
        component.setBounds(x, selectionY, width, height);
      }
      else {
        component.setVisible(false);
      }
    }
  }

  /**
   * Handle the zoom and pan interaction on the image
   */
  class PanZoomManager {
    private final static int ANIMATION_DURATION = 200;
    private final static int ZOOM_DELAY = 20;
    private float myTargetZoom;
    private Timer myZoomTimer;
    private Point myMouseDown = new Point();
    private Point2D.Float myImageDown = new Point.Float();
    private Point2D.Float myDownOffset = new Point.Float();
    private Point2D.Float myBounds = new Point2D.Float(1, 1);
    private long myStartTime;
    private float myStartZoom;

    private PanZoomManager() {
      // Timer to make the  zoom smoother
      myZoomTimer = new Timer(ZOOM_DELAY, et -> {
        final long currentTime = System.currentTimeMillis();
        final float zoom;

        final float t = (currentTime - myStartTime) / (float)ANIMATION_DURATION;
        if (t > 1) {
          ((Timer)et.getSource()).stop();
          zoom = myTargetZoom;
          myHqRendering = true;
        }
        else {
          myHqRendering = false;
          zoom = myStartZoom + (myTargetZoom - myStartZoom) * t;
        }
        zoomExact(zoom, myMouseDown);
      });
      myZoomTimer.setRepeats(true);
      //myZoomTimer.setCoalesce(true);
      myZoomTimer.setInitialDelay(0);
    }

    /**
     * Same as {@link #zoom(float, Point)} but smoother
     *
     * @param amount
     * @param screenDown
     */
    private void zoomAnimate(float amount, Point screenDown) {
      myMouseDown.setLocation(screenDown);
      if (!myZoomTimer.isRunning()) {
        myTargetZoom = myZoom;
        myStartTime = System.currentTimeMillis();
        myStartZoom = myZoom;
        myZoomTimer.restart();
      }
      myTargetZoom *= (1 - amount / 10f);
    }

    /**
     * Zoom the image by 1/10th of amount centered on screenDown.
     *
     * @param amount
     * @param screenDown point where to center the zoom
     */
    private void zoom(float amount, Point screenDown) {
      zoomExact(myZoom * (1 - amount / 10f), screenDown);
    }

    /**
     * Set the zoom to exactly the provided value. The zoom is centered on screenDown
     *
     * @param zoom
     * @param screenDown
     */
    private void zoomExact(float zoom, Point screenDown) {
      if (myDisplayedImage == null) {
        return;
      }
      float oldZoom = myZoom;
      myZoom = max(MIN_SCALE, min(MAX_SCALE, zoom));
      int screenDownX = screenDown.x;
      int screenDownY = screenDown.y;
      myDownOffset.x = myImageOffset.x;
      myDownOffset.y = myImageOffset.y;
      try {
        myImageTransform.inverseTransform(screenDown, myImageDown);

        // CALC the transform
        int sw = getWidth();
        int sh = getHeight();
        int iw = myDisplayedImage.getWidth();
        int ih = myDisplayedImage.getHeight();
        float scale = (float)(myZoom * min(sw / (double)iw, sh / (double)ih));
        updateTransform(sw, sh, iw, ih, scale);

        // CALC the transform
        myImageTransform.transform(myImageDown, myImageDown);
        float wrongDownX = (float)myImageDown.getX();
        float wrongDownY = (float)myImageDown.getY();
        float dx = screenDownX - wrongDownX;
        float dy = screenDownY - wrongDownY;

        if (iw * scale <= sw && oldZoom > myZoom) {
          // Ensure that the image stays centered when zooming out
          myImageOffset.x = 0;
        }
        else {
          myImageOffset.x = myDownOffset.x + 2 * (dx / (sw - scale * iw));
        }

        if (ih * scale <= sh && oldZoom > myZoom) {
          // Ensure that the image stays centered when zooming out
          myImageOffset.y = 0;
        }
        else {
          myImageOffset.y = myDownOffset.y + 2 * (dy / (sh - scale * ih));
        }

        myBounds.x = max(1, abs(myImageOffset.x));
        myBounds.y = max(1, abs(myImageOffset.y));
      }
      catch (NoninvertibleTransformException e1) {
        Logger.getInstance(MockupViewPanel.class).warn(e1);
      }

      // Remove antialiasing when getting closer to enable
      // the user to make a pixel perfect selection
      myHqRendering = zoom <= MIN_SCALE + (MAX_SCALE - MIN_SCALE) / 2f;
      mySelectionLayer.contentResized();
      repaint();
    }

    /**
     * Stop the zoom animation
     */
    private void stop() {
      myZoomTimer.stop();
    }

    public void mousePressed(MouseEvent e) {
      myMouseDown.setLocation(e.getPoint());
      myDownOffset.setLocation(myImageOffset);
    }

    public void mouseDragged(MouseEvent e) {
      if (myDisplayedImage == null) {
        return;
      }
      int dx = e.getX() - myMouseDown.x;
      int dy = e.getY() - myMouseDown.y;

      int sw = getWidth();
      int sh = getHeight();
      int iw = myDisplayedImage.getWidth();
      int ih = myDisplayedImage.getHeight();
      float scale = myZoom * min(sw / (float)iw, sh / (float)ih);
      if (iw * scale > sw) {
        myImageOffset.x = myDownOffset.x + 2 * (dx / (sw - scale * iw));
        myImageOffset.x = max(min(myImageOffset.x, myBounds.x), -myBounds.x);
      }

      if (ih * scale > sh) {
        myImageOffset.y = myDownOffset.y + 2 * (dy / (sh - scale * ih));
        myImageOffset.y = max(min(myImageOffset.y, myBounds.y), -myBounds.y);
      }
      invalidate();
      repaint();
    }
  }
}
