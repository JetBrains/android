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
package com.android.tools.idea.uibuilder.scene.decorator;

import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.scene.draw.DrawRegion;
import com.android.tools.idea.common.scene.decorator.SceneDecorator;
import com.android.tools.idea.uibuilder.handlers.constraint.drawing.ColorSet;
import com.android.tools.idea.uibuilder.handlers.constraint.drawing.decorator.ColorTheme;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

/**
 * Decorator and Draw for ImageView
 */
public class ImageViewDecorator extends SceneDecorator {
  public static class DrawImageView extends DrawRegion {
    static double[] move = {196.0, 319.99908};
    String TITLE = "ImageView";
    private Font mFont = new Font("Helvetica", Font.PLAIN, 12);

    static double[][] curve = {
      {3.9168854, -4.08313, 18.501312, -17.415588, 23.501312, -24.498688},
      {5.0, -7.08313, 1.7489014, -10.666687, 6.4986877, -18.0},
      {4.7497864, -7.3333435, 14.166672, -19.916443, 22.0, -26.0},
      {7.8333282, -6.083557, 13.416443, 2.9986877, 25.0, -10.501312},
      {11.583557, -13.5, 35.334656, -58.498688, 44.501312, -70.49869},
      {9.1666565, -12.0, 6.3320312, 1.0822296, 10.498688, -1.5013123},
      {4.1666565, -2.5835571, 11.167969, -11.5, 14.501312, -14.0},
      {3.3333435, -2.5, -4.417755, 9.083115, 5.4986877, -1.0},
      {9.916443, -10.083115, 38.5, -53.41558, 54.0, -59.498695},
      {15.5, -6.0831146, 30.666656, 18.416885, 39.0, 23.0},
      {8.3333435, 4.5831146, 3.1666565, -4.0013123, 11.0, 4.4986877},
      {7.8333435, 8.500008, 19.249786, 33.417763, 36.0, 46.50132},
      {16.750214, 13.083542, 50.917786, 27.250214, 64.50134, 32.0},
      {13.583496, 4.749771, 6.833313, -12.251099, 17.0, -3.5013123},
      {10.166626, 8.749771, 26.166626, 36.74977, 44.0, 56.0},
      {17.833313, 19.250214, 51.750183, 49.001312, 63.0, 59.501312},
      {11.249756, 10.5, 3.7489014, 2.9155579, 4.498657, 3.4986877}
    };
    static Path2D sPath2D = new Path2D.Float();
    static Path2D sClosedPath2D;
    static int sPathWidth;
    static int sPathHeight;
    private AffineTransform mTransform = new AffineTransform();

    static {
      sPath2D.moveTo(move[0], move[1]);
      double cx = move[0];
      double cy = move[1];

      for (double[] val : curve) {
        int k = 0;
        sPath2D.curveTo(cx + val[k], cy + val[k + 1], cx + val[k + 2],
                        cy + val[k + 3], cx + val[k + 4], cy + val[k + 5]);
        cx += val[k + 4];
        cy += val[k + 5];
      }
      Rectangle bounds = sPath2D.getBounds();
      sClosedPath2D = (Path2D)sPath2D.clone();
      sClosedPath2D.lineTo(cx, cy += 20);
      sClosedPath2D.lineTo(move[0], cy);
      sClosedPath2D.closePath();
      AffineTransform transform = new AffineTransform();
      transform.translate(-bounds.x, -bounds.y);
      sPath2D.transform(transform);
      sClosedPath2D.transform(transform);
      double scale = 100 / (double)Math.max(bounds.width, bounds.height);
      transform.setToIdentity();
      transform.scale(scale, scale);
      sPath2D.transform(transform);
      sClosedPath2D.transform(transform);
      bounds = sPath2D.getBounds();
      sPathWidth = bounds.width;
      sPathHeight = bounds.height;
    }

    @Override
    public int getLevel() {
      return COMPONENT_LEVEL;
    }

    DrawImageView(@SwingCoordinate int x, @SwingCoordinate int y, @SwingCoordinate int width, @SwingCoordinate int height) {
      super(x, y, width, height);
    }

    public DrawImageView(String s) {
      String[] sp = s.split(",");
      super.parse(sp, 0);
    }

    @Override
    public void paint(Graphics2D g, SceneContext sceneContext) {
      g.drawRect(x, y, width, height);
      ColorSet colorSet = sceneContext.getColorSet();
      if (colorSet.drawBackground()) {
        mTransform.setToIdentity();
        double sw = (width - 1) / (double)sPathWidth;
        double sh = height / (double)sPathHeight;

        double s = Math.max(sw, sh);
        double dx = 1 + (width - sPathWidth * s) / 2;
        double dy = (height - sPathHeight * s) / 2;
        mTransform.translate(dx, 0);
        mTransform.scale(s, s);
        Shape shape;

        Graphics2D clipGraphics = ((Graphics2D)g.create(x, y, width, height));

        clipGraphics.setColor(ColorTheme.updateBrightness(sceneContext.getColorSet().getBackground(), 0.8f, 64));

        shape = sClosedPath2D.createTransformedShape(mTransform);
        clipGraphics.fill(shape);

        shape = sPath2D.createTransformedShape(mTransform);
        clipGraphics.setColor(sceneContext.getColorSet().getText());
        clipGraphics.draw(shape);

        int stringWidth = g.getFontMetrics(mFont).stringWidth(TITLE);
        float scale = width / ((stringWidth * 3f) / 2);
        g.setFont(mFont.deriveFont(mFont.getSize() * scale));
        FontMetrics fontMetrics = g.getFontMetrics();
        g.setColor(Color.WHITE);
        Rectangle2D bounds = fontMetrics.getStringBounds(TITLE, g);
        g.drawString(TITLE, x + (int)((width - bounds.getWidth()) / 2f), y + (int)(height - (height - bounds.getHeight()) / 3f));
      }
    }
  }

  @Override
  public void addContent(@NotNull DisplayList list, long time, @NotNull SceneContext sceneContext, @NotNull SceneComponent component) {
    Rectangle rect = new Rectangle();
    component.fillDrawRect(time, rect);
    int l = sceneContext.getSwingXDip(rect.x);
    int t = sceneContext.getSwingYDip(rect.y);
    int w = sceneContext.getSwingDimensionDip(rect.width);
    int h = sceneContext.getSwingDimensionDip(rect.height);
    list.add(new DrawImageView(l, t, w, h));
  }
}
