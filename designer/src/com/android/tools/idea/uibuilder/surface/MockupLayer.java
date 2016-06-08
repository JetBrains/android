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
package com.android.tools.idea.uibuilder.surface;

import com.android.tools.idea.uibuilder.mockup.MockupComponentAttributes;
import com.android.tools.idea.uibuilder.model.Coordinates;
import com.android.tools.idea.uibuilder.model.ModelListener;
import com.android.tools.idea.uibuilder.model.NlModel;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Layer build to be on top of the BluePrint ScreenView displaying
 * an optional mockup of the layout to be build
 *
 * @See MockupModel
 **/
public class MockupLayer extends Layer {

  private final ScreenView myScreenView;
  private Dimension myScreenViewSize = new Dimension();
  private Rectangle myComponentSwingPosition = new Rectangle();
  private Rectangle myDrawingRectangle = new Rectangle();
  private NlModel myNlModel;
  private java.util.List<MockupComponentAttributes> myMockupComponentAttributes;

  public MockupLayer(ScreenView screenView) {
    assert screenView != null;
    myScreenView = screenView;
    myScreenViewSize = myScreenView.getSize(myScreenViewSize);
    setNlModel(myScreenView.getModel());
    myNlModel.addListener(new ModelListener() {
      @Override
      public void modelChanged(@NotNull NlModel model) {
        setNlModel(model);
      }

      @Override
      public void modelRendered(@NotNull NlModel model) {
      }
    });
    setNlModel(myScreenView.getModel());
  }

  public void setNlModel(NlModel nlModel) {
    if (nlModel == myNlModel) {
      return;
    }
    myNlModel = nlModel;
    myMockupComponentAttributes = MockupComponentAttributes.createAll(myNlModel);
  }

  @NotNull
  public List<MockupComponentAttributes> getMockupComponentAttributes() {
    return myMockupComponentAttributes;
  }

  @Override
  public void paint(@NotNull Graphics2D g) {
    if (!myScreenView.getSurface().isMockupVisible()
        || myMockupComponentAttributes.isEmpty()) {
      return;
    }
    final Composite composite = g.getComposite();
    myScreenViewSize = myScreenView.getSize(myScreenViewSize);

    for (int i = 0; i < myMockupComponentAttributes.size(); i++) {
      final MockupComponentAttributes mockupComponentAttributes = myMockupComponentAttributes.get(i);
      paintMockup(g, mockupComponentAttributes);
    }
    g.setComposite(composite);
  }

  private void paintMockup(@NotNull Graphics2D g, MockupComponentAttributes mockupComponentAttributes) {
    final BufferedImage image = mockupComponentAttributes.getImage();
    if (image != null) {
      g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, mockupComponentAttributes.getAlpha()));
      // Coordinates of the component in the ScreenView system
      int componentSwingX = Coordinates.getSwingX(myScreenView, mockupComponentAttributes.getComponent().x);
      int componentSwingY = Coordinates.getSwingY(myScreenView, mockupComponentAttributes.getComponent().y);
      int componentSwingW = Coordinates.getSwingDimension(myScreenView, mockupComponentAttributes.getComponent().w);
      int componentSwingH = Coordinates.getSwingDimension(myScreenView, mockupComponentAttributes.getComponent().h);

      myComponentSwingPosition.setBounds(
        componentSwingX,
        componentSwingY,
        componentSwingW,
        componentSwingH);

      Rectangle2D.intersect(myComponentSwingPosition, g.getClipBounds(), myComponentSwingPosition);
      if (myComponentSwingPosition.isEmpty()) {
        return;
      }

      Rectangle d = getDestinationRectangle(mockupComponentAttributes.getPosition(), myComponentSwingPosition);

      int sx = mockupComponentAttributes.getCropping().x;
      int sy = mockupComponentAttributes.getCropping().y;
      int sw = mockupComponentAttributes.getCropping().width;
      int sh = mockupComponentAttributes.getCropping().height;

      sw = sw <= 0 ? image.getWidth() : sw;
      sh = sh <= 0 ? image.getHeight() : sh;

      g.drawImage(image,
                  d.x, d.y, d.x + d.width, d.y + d.height,
                  sx, sy, sx + sw, sy + sh,
                  null);
    }
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
    Rectangle d = myDrawingRectangle;

    // if one of the dimension was not set in the xml.
    // it had been set to -1 in the model, meaning we should
    // use the ScreenView dimension and/or the Image dimension
    d.width = mockupPosition.width <= 0 ? componentPosition.width : Coordinates.getSwingDimensionDip(myScreenView, mockupPosition.width);
    d.height =
      mockupPosition.height <= 0 ? componentPosition.height : Coordinates.getSwingDimensionDip(myScreenView, mockupPosition.height);

    d.x = componentPosition.x + Coordinates.dpToPx(myScreenView, mockupPosition.x);
    d.y = componentPosition.y + Coordinates.dpToPx(myScreenView, mockupPosition.y);

    // Ensure that we don't draw anything outside the design surface
    Rectangle2D.intersect(d, componentPosition, d);
    return d;
  }
}