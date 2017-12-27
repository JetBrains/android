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
package com.android.tools.idea.uibuilder.mockup.backgroundremove;

import com.android.tools.idea.ui.resourcechooser.ResourceChooserImageIcon;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.image.BufferedImage;

/**
 * Panel to remove colors from a given {@link BufferedImage}.
 *
 * Set the image to remove the color from using {@link #setImage(BufferedImage)}.
 * The Panel uses the {@link RemoveColorComposite} to remove the background.
 *
 * On click on one pixel in the image, all the similar color are removed using a flood-fill algorithm.
 * The removal stops when there is no more similar pixels around. To remove the similar color from the
 * whole image, hold the Shift key.
 *
 * To adjust the threshold for the similar color ({@link RemoveColorComposite#setThreshold(double)},
 * drag the mouse while holding the left button.
 */
public class RemoveBackgroundPanel extends JPanel implements MouseMotionListener, MouseListener {

  /**
   * Distance in pixel of the dragging distance to set the Threshold to its maximum value
   */
  private static final int MAX_DRAG_DIST = 300;
  public static final ResourceChooserImageIcon.CheckerboardPaint CHECKERBOARD_PAINT
    = new ResourceChooserImageIcon.CheckerboardPaint(10);
  private final HistoryManager<BufferedImage> myImageHistoryManager;
  AffineTransform myAffineTransform;
  @Nullable private BufferedImage myImage;
  private BufferedImage myNewImage;
  private Point myPointHolder = new Point();
  private Point myMouseOrigin = new Point();
  private Point myCurrentMouse = new Point();
  private RemoveColorComposite myExtractComposite;
  private boolean myIsExtracting;

  public RemoveBackgroundPanel() {
    myAffineTransform = new AffineTransform();
    addMouseListener(this);
    addMouseMotionListener(this);
    setFocusable(false);
    myExtractComposite = new RemoveColorComposite();
    setBackground(UIUtil.getPanelBackground());
    myImageHistoryManager = new HistoryManager<>();
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    if (myImage == null) {
      return;
    }

    updateTransform();
    Graphics2D g2d = (Graphics2D)g;
    Paint paint = g2d.getPaint();
    g2d.setPaint(CHECKERBOARD_PAINT);
    g2d.fillRect(0, 0, getWidth(), getHeight());
    g2d.setPaint(paint);

    if (myIsExtracting) {
      g2d.drawImage(myNewImage, myAffineTransform, null);

      double dragDist = myMouseOrigin.distance(myCurrentMouse);
      if (dragDist > 0) {
        g2d.drawLine(myMouseOrigin.x, myMouseOrigin.y, myCurrentMouse.x, myCurrentMouse.y);
        g2d.drawString(String.valueOf(Math.round(dragDist / (double)MAX_DRAG_DIST * 100)), myMouseOrigin.x, myMouseOrigin.y);
      }
    }
    else {
      g2d.drawImage(myImage, myAffineTransform, null);
    }
  }

  public void setImage(@Nullable BufferedImage image) {
    if (image == null) {
      myImage = null;
      return;
    }
    myImage = createNewImage(image);
    myImageHistoryManager.setOriginalImage(myImage);
    myNewImage = createNewImage(image);
    repaintImage();
    updateTransform();
  }

  private void updateTransform() {
    if (myImage == null) {
      return;
    }
    double scale = Math.min(getWidth() / (double)myImage.getWidth(), getHeight() / (double)myImage.getHeight());
    myAffineTransform.setToIdentity();
    myAffineTransform.translate((getWidth() - scale * myImage.getWidth()) / 2f,
                                (getHeight() - scale * myImage.getHeight()) / 2f);
    myAffineTransform.scale(scale, scale);
  }

  private void repaintImage() {
    if (myImage == null) {
      return;
    }
    Graphics2D g2d = myNewImage.createGraphics();
    g2d.setComposite(myExtractComposite);
    g2d.drawImage(myImage, 0, 0, myImage.getWidth(), myImage.getHeight(), null);
    g2d.dispose();
    repaint();
  }

  @NotNull
  private static BufferedImage createNewImage(@NotNull BufferedImage image) {
    @SuppressWarnings("UndesirableClassUsage")
    BufferedImage newImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = newImage.createGraphics();
    g.drawImage(image, 0, 0, image.getWidth(), image.getHeight(), null);
    g.dispose();
    return newImage;
  }

  public boolean canUndo() {
    return myImageHistoryManager.canUndo();
  }

  public boolean canRedo() {
    return myImageHistoryManager.canRedo();
  }

  public void undo() {
    if(myImageHistoryManager.canUndo()) {
      myNewImage = myImage;
      myImage = myImageHistoryManager.undo();
      repaintImage();
      repaint();
    }
  }

  public void redo() {
    myImage = myImageHistoryManager.redo();
    repaintImage();
    repaint();
  }

  /*/////////////////////////
  // Event Implementations //
  /////////////////////////*/

  @Override
  public void mouseDragged(MouseEvent e) {
    if (!myIsExtracting) {
      return;
    }
    myCurrentMouse.setLocation(e.getPoint());
    myExtractComposite.setThreshold(e.getPoint().distance(myMouseOrigin) / MAX_DRAG_DIST);
    repaintImage();
  }

  @Override
  public void mousePressed(MouseEvent e) {
    if (myImage == null) {
      return;
    }
    updateTransform();
    myMouseOrigin.setLocation(e.getPoint());
    myCurrentMouse.setLocation(e.getPoint());

    // If click is outside image, cancel
    try {
      myAffineTransform.inverseTransform(e.getPoint(), myPointHolder);
      int width = myImage.getWidth();
      if (myPointHolder.x < 0 || myPointHolder.y < 0
          || myPointHolder.x >= width
          || myPointHolder.y >= myImage.getHeight()) {
        return;
      }

      int removeColor = myImage.getRGB(myPointHolder.x, myPointHolder.y);

      // If the selected pixel is already transparent, we don't begin the background extraction
      final int[] pixel = new int[4];
      myImage.getRaster().getPixel(myPointHolder.x, myPointHolder.y, pixel);
      if (pixel[3] == 0) {
        return;
      }

      // Init the RemoveComposite
      myIsExtracting = true;
      myExtractComposite.setRemoveColor(removeColor);
      myExtractComposite.setOriginPixel(myPointHolder.x, myPointHolder.y);
      myExtractComposite.setThreshold(0);

      // If ShiftKey pressed remove all the similar color in the image
      // otherwise remove only the similar color in the selected region
      myExtractComposite.setAreaOnly(!((e.getModifiers() & InputEvent.SHIFT_MASK) == InputEvent.SHIFT_MASK));

      myNewImage = createNewImage(myImage);
      repaintImage();
    }
    catch (NoninvertibleTransformException e1) {
      Logger.getInstance(RemoveBackgroundPanel.class).warn("Could not invert transform");
    }
  }

  @Override
  public void mouseReleased(MouseEvent e) {
    myMouseOrigin.setLocation(0, 0);
    myCurrentMouse.setLocation(0, 0);
    if (myImage == null || !myIsExtracting) {
      return;
    }
    myIsExtracting = false;
    myImage = myNewImage;
    myImageHistoryManager.pushUndo(myImage);
    repaintImage();
  }

  @Nullable
  public BufferedImage getImage() {
    return myImage;
  }

  @Override
  public void mouseEntered(MouseEvent e) {
  }

  @Override
  public void mouseExited(MouseEvent e) {
  }

  @Override
  public void mouseMoved(MouseEvent e) {
  }

  @Override
  public void mouseClicked(MouseEvent e) {
  }
}
