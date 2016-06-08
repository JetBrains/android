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
package com.android.tools.idea.uibuilder.mockup;

import com.android.tools.idea.uibuilder.graphics.NlConstants;
import com.android.tools.idea.uibuilder.model.Coordinates;
import com.android.tools.idea.uibuilder.model.ModelListener;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBPanel;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

import static java.lang.Math.min;

/**
 *  Panel where the ScreenView is displayed and the mockup can be positioned and
 *  resized
 */
public class MockupResizingPanel extends JBPanel implements ModelListener {

  public static final Color COMPONENT_FG_COLOR = JBColor.WHITE;
  private final MockupComponentAttributes myMockupComponentAttributes;
  private final ScreenView myScreenView;
  private BufferedImage myImage;
  private Dimension myScreenViewSize;

  private final CoordinateTransform myScreenViewToPanel;
  private final Rectangle myComponentSwingPosition = new Rectangle();

  public MockupResizingPanel(@NotNull ScreenView screenView, MockupComponentAttributes mockupComponentAttributes) {
    myScreenView = screenView;
    myScreenViewToPanel = new CoordinateTransform();
    myMockupComponentAttributes = mockupComponentAttributes;
    myImage = myMockupComponentAttributes.getImage();
    myMockupComponentAttributes.getComponent().getModel().addListener(this);
    setBackground(JBColor.background());
  }

  @Override
  public void paint(Graphics g) {
    super.paint(g);
    final Graphics2D g2d = (Graphics2D)g;
    Color color = g.getColor();
    myScreenViewSize = myScreenView.getSize(myScreenViewSize);
    myScreenViewToPanel.setDimensions(getSize(), myScreenViewSize);
    paintScreenView(g2d);
    paintMockup(g2d);
    paintAllComponents(g2d, myMockupComponentAttributes.getComponent().getRoot());
    g.setColor(color);
  }

  private void paintMockup(Graphics2D g) {
    myScreenViewSize = myScreenView.getSize(myScreenViewSize);

    // Coordinates of the component in the ScreenView system
    int componentSwingX = Coordinates.getSwingX(myScreenView, myMockupComponentAttributes.getComponent().x);
    int componentSwingY = Coordinates.getSwingY(myScreenView, myMockupComponentAttributes.getComponent().y);
    int componentSwingW = Coordinates.getSwingDimension(myScreenView, myMockupComponentAttributes.getComponent().w);
    int componentSwingH = Coordinates.getSwingDimension(myScreenView, myMockupComponentAttributes.getComponent().h);

    myComponentSwingPosition.setBounds(
      componentSwingX,
      componentSwingY,
      componentSwingW,
      componentSwingH);

    Rectangle2D.intersect(myComponentSwingPosition, g.getClipBounds(), myComponentSwingPosition);
    if (myComponentSwingPosition.isEmpty()) {
      return;
    }

    Rectangle d = getDestinationRectangle(myMockupComponentAttributes.getPosition(), myComponentSwingPosition);

    // Source coordinates
    int sx = myMockupComponentAttributes.getCropping().x;
    int sy = myMockupComponentAttributes.getCropping().y;
    int sw = myMockupComponentAttributes.getCropping().width;
    int sh = myMockupComponentAttributes.getCropping().height;

    sw = sw <= 0 ? myImage.getWidth() : sw;
    sh = sh <= 0 ? myImage.getHeight() : sh;
    final Composite composite = g.getComposite();
    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, myMockupComponentAttributes.getAlpha()));
    g.drawImage(myImage,
                d.x,
                d.y,
                d.x + d.width,
                d.y + d.height,
                sx, sy, sx + sw, sy + sh,
                null);
    g.setComposite(composite);
  }

  /**
   * Find the destination drawing rectangle where we will draw the mockup image.
   *
   * We use the mockup position Rectangle to find the coordinates where the mockup will be drawn relative to
   * the position of the component.
   *
   * @param mockupPosition    Position of the mockup relative to the component Position and its size in dip
   * @param componentPosition Position of the component where the mockup is drawn and its size in dip
   * @return The rectangle where the mockup will be drawn
   */
  @NotNull
  public Rectangle getDestinationRectangle(@NotNull Rectangle mockupPosition, Rectangle componentPosition) {
    Rectangle d = myComponentSwingPosition;

    // First we find the coordinate of the component holding the mockup
    // in the ScreenView coordinate system

    // if one of the dimension was not set in the xml.
    // it had been set to -1 in the model, meaning we should
    // use the ScreenView dimension and/or the Image dimension
    d.width = mockupPosition.width <= 0 ? componentPosition.width
                                        : Coordinates.getSwingDimensionDip(myScreenView, mockupPosition.width);
    d.height = mockupPosition.height <= 0 ? componentPosition.height
                                          : Coordinates.getSwingDimensionDip(myScreenView, mockupPosition.height);

    d.x = componentPosition.x + Coordinates.dpToPx(myScreenView, mockupPosition.x);
    d.y = componentPosition.y + Coordinates.dpToPx(myScreenView, mockupPosition.y);


    // Ensure that we don't draw anything outside the design surface
    Rectangle2D.intersect(d, componentPosition, d);

    // Finally we transform the screenView Coordinates into the ResizingPanel Coordinates
    d.x = myScreenViewToPanel.x(d.x);
    d.y = myScreenViewToPanel.y(d.y);
    d.width = myScreenViewToPanel.d(d.width);
    d.height = myScreenViewToPanel.d(d.height);
    return d;
  }

  private void paintScreenView(@NotNull Graphics2D g) {
    g.setColor(NlConstants.BLUEPRINT_BG_COLOR);
    g.fillRect(
      myScreenViewToPanel.x(0),
      myScreenViewToPanel.y(0),
      myScreenViewToPanel.d(myScreenViewSize.width),
      myScreenViewToPanel.d(myScreenViewSize.height)
    );
  }

  /**
   * Recursively draw all child components
   *
   * @param g         the {@link Graphics2D} to draw on
   * @param component a node in the component tree.
   */
  private void paintAllComponents(Graphics2D g, NlComponent component) {
    g.setColor(COMPONENT_FG_COLOR);
    paintComponent(g, component);
    for (int i = 0; i < component.getChildCount(); i++) {
      paintAllComponents(g, component.getChild(i));
    }
  }

  /**
   * Draw one component.
   *
   * @param gc        the {@link Graphics2D} to draw on
   * @param component a component in the {@link ScreenView}
   */
  private void paintComponent(Graphics2D gc, NlComponent component) {
    int x = myScreenViewToPanel.x(Coordinates.getSwingX(myScreenView, component.x));
    int y = myScreenViewToPanel.y(Coordinates.getSwingY(myScreenView, component.y));
    int w = myScreenViewToPanel.d(Coordinates.getSwingDimension(myScreenView, component.w));
    int h = myScreenViewToPanel.d(Coordinates.getSwingDimension(myScreenView, component.h));
    gc.drawRect(x, y, w, h);
  }

  @Override
  public void modelChanged(@NotNull NlModel model) {
    myImage = myMockupComponentAttributes.getImage();
    repaint();
  }

  @Override
  public void modelRendered(@NotNull NlModel model) {
  }

  private class CoordinateTransform {

    private double myScale = 1;
    private Dimension mySourceSize = new Dimension(1, 1);
    private Dimension myTargetSize = getSize();
    private Point myTargetOrigin = new Point(0, 0);

    public void setDimensions(Dimension destSize, Dimension srcSize) {
      if (mySourceSize == srcSize && getSize() == myTargetSize) {
        return;
      }
      mySourceSize = srcSize;
      myTargetSize = destSize;
      myScale = min(myTargetSize.width / srcSize.getWidth(), myTargetSize.getHeight() / srcSize.getHeight());
      setCenterInTarget();
    }

    public void setTargetOrigin(double x, double y) {
      myTargetOrigin.setLocation(x, y);
    }

    public void setCenterInTarget() {
      setTargetOrigin(
        myTargetSize.width / 2. - (mySourceSize.width / 2.) * myScale,
        myTargetSize.height / 2. - (mySourceSize.height / 2.) * myScale
      );
    }

    public int x(double x) {
      return (int)Math.round(myTargetOrigin.x + myScale * x);
    }

    public int y(double y) {
      return (int)Math.round(myTargetOrigin.y + myScale * y);
    }

    public int d(double dim) {
      return (int)Math.round(myScale * dim);
    }
  }
}
