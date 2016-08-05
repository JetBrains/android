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

import com.android.tools.idea.rendering.ImageUtils;
import com.android.tools.idea.uibuilder.mockup.CoordinateConverter;
import com.android.tools.idea.uibuilder.mockup.Mockup;
import com.android.tools.idea.uibuilder.mockup.editor.tools.MockupEditor;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Panel that show the Mockup in the Editor window
 */
public class MockupViewPanel extends JPanel {

  private AffineTransform myDisplayedImageAffineTransform;
  private Mockup.MockupModelListener myMockupListener;

  /**
   * Listener to notify the tools when a selection ended
   */
  public interface SelectionListener {

    /**
     * Called when a selection is started on the {@link SelectionLayer}
     * The given coordinate are in mockupViewPanel coordinate system.
     * 0,0 means on top left of the panel.
     *
     * The coordinate can be converted using {@link #getImageTransform()}
     * to get the coordinates in the whole image
     *
     * @param mockupViewPanel The panel on which the selection is being done
     * @param x               x origin of the selection in mockupViewPanel coordinate system.
     * @param y               y origin of the selection in mockupViewPanel coordinate system.
     */
    void selectionStarted(MockupViewPanel mockupViewPanel, int x, int y);

    /**
     * Called when a selection is started on the {@link SelectionLayer}
     * The given coordinate are in the displayed image coordinate system.
     * They gave been converted using the inverse of {@link #getDisplayedImageTransform()}
     * 0,0 means on top left of the image on screen (and NOT the panel)
     *
     * @param mockupViewPanel The panel on which the seleciton is being done
     * @param selection       the selection in the the displayed image coordinate system
     **/
    void selectionEnded(MockupViewPanel mockupViewPanel, Rectangle selection);
  }

  public static final float ADJUST_SCALE = 0.95f;
  private Mockup myMockup;

  private final SelectionLayer mySelectionLayer;

  @Nullable private BufferedImage myDisplayedImage;
  @Nullable private BufferedImage myImage;
  private boolean myDisplayOnlyCroppedRegion = true;
  private boolean mySelectionMode = true;
  CoordinateConverter myDisplayedImageTransform;

  CoordinateConverter myImageTransform;
  private List<SelectionListener> mySelectionListeners = new ArrayList<>();

  /**
   * Create a new MockupView Panel displaying the given mockup
   *
   * @param mockup       the mockup to display
   * @param mockupEditor
   */
  public MockupViewPanel(@NotNull Mockup mockup, MockupEditor mockupEditor) {
    myMockupListener = this::updateDisplayedImage;
    mockupEditor.addListener(this::update);
    setLayout(new MyLayoutManager());
    setBackground(JBColor.background().brighter());
    setMockup(mockup);
    updateDisplayedImage(mockup);
    mySelectionLayer = new SelectionLayer(this);
    addMouseListener(new MyMouseInteraction());
    addMouseMotionListener(new MyMouseInteraction());
    addComponentListener(new MyComponentListener());

    myDisplayedImageTransform = new CoordinateConverter();
    myDisplayedImageTransform.setFixedRatio(true);
    myDisplayedImageTransform.setCenterInDestination();

    myImageTransform = new CoordinateConverter();
    myImageTransform.setFixedRatio(true);
    myImageTransform.setCenterInDestination();
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
    final BufferedImage image = mockup.getImage();
    if (image != myImage) {
      myImage = image;
    }
    myDisplayedImage = null;
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
      myDisplayedImage = null;
    }
    myDisplayOnlyCroppedRegion = displayOnlyCroppedRegion;
    repaint();
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
    paintMockup(g2d, myMockup);
    if (mySelectionMode) {
      mySelectionLayer.paint(g2d);
    }
    g2d.dispose();
  }

  /**
   * Create an image scaled using the provided {@link CoordinateConverter}
   *
   * @param original       original image
   * @param imageTransform used to scale the image to the {@link CoordinateConverter} destination
   * @return the original image scaled in a new instance of {@link BufferedImage}
   */
  private static BufferedImage createScaledImage(@NotNull BufferedImage original,
                                                 @NotNull CoordinateConverter imageTransform) {
    final AffineTransform affineTransform = imageTransform.getAffineTransform();
    return ImageUtils.scale(original, affineTransform.getScaleX(), affineTransform.getScaleY());
  }

  /**
   * Paint the mockup using the provided graphic context
   *
   * @param g2d    the graphic context
   * @param mockup the mockup to dray
   */
  private void paintMockup(Graphics2D g2d, Mockup mockup) {

    if (myDisplayedImage == null) {
      if (myImage == null) {
        return;
      }
      myDisplayedImage = createDisplayedImage(myImage, mockup.getRealCropping());
    }
    myDisplayedImageAffineTransform = myDisplayedImageTransform.getAffineTransform();
    g2d.drawImage(myDisplayedImage, myDisplayedImageAffineTransform, null);
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
  @NotNull
  private BufferedImage createDisplayedImage(@NotNull BufferedImage image, @NotNull Rectangle cropping) {
    BufferedImage displayedImage;

    if (myDisplayOnlyCroppedRegion && !cropping.isEmpty()) {
      Rectangle2D.intersect(cropping, new Rectangle(image.getWidth(), image.getHeight()), cropping);
      final BufferedImage subImage = image.getSubimage(cropping.x, cropping.y, cropping.width, cropping.height);
      myImageTransform.setDimensions(getWidth(), getHeight(), cropping.width, cropping.height, ADJUST_SCALE);
      displayedImage = createScaledImage(subImage, myImageTransform);
    }
    else {
      myImageTransform.setDimensions(getWidth(), getHeight(), image.getWidth(), image.getHeight(), ADJUST_SCALE);
      displayedImage = createScaledImage(image, myImageTransform);
    }
    myDisplayedImageTransform.setDimensions(getWidth(), getHeight(),
                                            displayedImage.getWidth(), displayedImage.getHeight(),
                                            ADJUST_SCALE);
    return displayedImage;
  }

  /**
   * Set the selection of the {@link SelectionLayer} to match the mockup crop
   */
  public void setSelectionToMockupCrop() {
    if (myImage != null && mySelectionMode) {
      myImageTransform.setDimensions(getWidth(), getHeight(), myImage.getWidth(), myImage.getHeight(), ADJUST_SCALE);
      final Rectangle cropping = myMockup.getRealCropping();
      mySelectionLayer.setSelection(myImageTransform.convert(cropping, mySelectionLayer.getSelection()));
    }
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

  public CoordinateConverter getImageTransform() {
    return myImageTransform;
  }

  public CoordinateConverter getDisplayedImageTransform() {
    return myDisplayedImageTransform;
  }

  /**
   * Ensure that the current selection is resized when this panel is resized
   */
  private void resizeSelection() {
    Rectangle selection = mySelectionLayer.getSelection();
    if (myImage == null || selection.isEmpty()) {
      return;
    }
    myImageTransform.setDimensions(getWidth(), getHeight(), myImage.getWidth(), myImage.getHeight(), ADJUST_SCALE);
    myImageTransform.convert(myMockup.getRealCropping(), selection);
    mySelectionLayer.setSelection(selection.x, selection.y, selection.width, selection.height);
  }

  /**
   * Convert the selection in the Mockup's image coordinate system,
   * and notify the listener with the converted selection
   *
   * @param selection the {@link Rectangle} returned by {@link SelectionLayer#getSelection()}
   */
  private void notifySelectionEnded(Rectangle selection) {
    final Rectangle convertedSelection;
    if (selection.isEmpty() || myImage == null) {
      convertedSelection = new Rectangle(0, 0, -1, -1);
    }
    else {
      if (myDisplayOnlyCroppedRegion) {

        final Rectangle bounds = mySelectionLayer.getBounds();
        final Rectangle realCropping = myMockup.getRealCropping();
        final AffineTransform transform = new AffineTransform();

        transform.scale(realCropping.getWidth() / bounds.getWidth(),
                        realCropping.getHeight() / bounds.getHeight());
        transform.translate(-bounds.x, -bounds.y);
        convertedSelection = transform.createTransformedShape(selection).getBounds();
      }
      else {
        convertedSelection = myImageTransform.convertInverse(selection, null);
        Rectangle2D.intersect(convertedSelection, new Rectangle(myImage.getWidth(), myImage.getHeight()), convertedSelection);
      }
    }
    for (int i = 0; i < mySelectionListeners.size(); i++) {
      mySelectionListeners.get(i).selectionEnded(this, convertedSelection);
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
      myDisplayedImage = null;
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
    }
  }

  private class MyMouseInteraction extends MouseAdapter {

    @Override
    public void mousePressed(MouseEvent e) {
      if (mySelectionMode) {
        toSelectionLayer(e);
        notifySelectionStarted(e.getX(), e.getY());
        repaint();
      }
    }

    /**
     * Pass the mouse event to the SelectionLayer
     *
     * @param e MouseEvent
     */
    private void toSelectionLayer(MouseEvent e) {
      if (myImage == null) {
        mySelectionLayer.setBounds(0, 0, getWidth(), getHeight());
      }

      if (myDisplayedImage == null) {
        myDisplayedImage = createDisplayedImage(myImage, myMockup.getRealCropping());
      }
      myDisplayedImageTransform.setDimensions(getWidth(), getHeight(),
                                              myDisplayedImage.getWidth(), myDisplayedImage.getHeight(),
                                              ADJUST_SCALE);

      // Set the bounds of the selectable area
      mySelectionLayer.setBounds(myDisplayedImageTransform.x(0),
                                 myDisplayedImageTransform.y(0),
                                 myDisplayedImageTransform.dX(myDisplayedImageTransform.getSourceSize().width),
                                 myDisplayedImageTransform.dY(myDisplayedImageTransform.getSourceSize().height));
      mySelectionLayer.mousePressed(e);
    }

    @Override
    public void mouseDragged(MouseEvent e) {
      if (mySelectionMode) {
        mySelectionLayer.mouseDragged(e);
      }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
      if (mySelectionMode) {
        mySelectionLayer.mouseReleased(e);
        notifySelectionEnded(mySelectionLayer.getSelection());
      }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
      if (mySelectionMode) {
        mySelectionLayer.mouseMoved(e);
      }
    }
  }

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
        final Dimension preferredSize = component.getPreferredSize();
        int x = selection.x + selection.width + H_GAP;
        final int y = selection.y;
        final int width = preferredSize.width;
        final int height = preferredSize.height;
        if (x + width > getWidth()) {
          x = selection.x + selection.width - H_GAP - width;
        }
        component.setBounds(x, y, width, height);
      }
      else {
        component.setVisible(false);
      }
    }
  }
}
