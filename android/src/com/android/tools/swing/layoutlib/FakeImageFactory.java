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
package com.android.tools.swing.layoutlib;

import com.android.ide.common.rendering.api.IImageFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.Graphics2DDelegate;
import org.jetbrains.annotations.Nullable;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.image.BufferedImage;

/**
 * {@link IImageFactory} that allows changing the image on the fly.
 * <p/>
 * <p/>This is a temporary workaround until we expose an interface in layoutlib that allows directly
 * rendering to a {@link Graphics} instance.
 */
public class FakeImageFactory implements IImageFactory {
  private static final Logger LOG = Logger.getInstance(FakeImageFactory.class);
  private static final Graphics2D NOP_GRAPHICS2D = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();

  private Graphics myGraphics = NOP_GRAPHICS2D;
  private int myRequestedHeight;
  private int myRequestedWidth;

  /**
   * Set the Graphics object to be used in the factory.
   * @param graphics The Graphics object or null to remove the current value.
   */
  public void setGraphics(@Nullable Graphics graphics) {
    myGraphics = graphics != null ? graphics : NOP_GRAPHICS2D;
  }

  public int getRequestedHeight() {
    return myRequestedHeight;
  }

  public int getRequestedWidth() {
    return myRequestedWidth;
  }

  @Override
  public BufferedImage getImage(final int w, final int h) {
    // BufferedImage can not have a 0 size. We pass 1,1 since we are not really interested in the bitmap,
    // only in the createGraphics call.
    return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB) {
      @Override
      public Graphics2D createGraphics() {
        myRequestedHeight = h;
        myRequestedWidth = w;

        // TODO: Check if we can stop layoutlib from resetting the transforms.
        final Shape originalClip = myGraphics.getClip();
        final AffineTransform originalTx = ((Graphics2D)myGraphics).getTransform();

        AffineTransform inverse = null;

        if (originalTx != null) {
          try {
            inverse = originalTx.createInverse();
          }
          catch (NoninvertibleTransformException e) {
            LOG.error(e);
          }
        }

        final AffineTransform originalTxInverse = inverse;

        return new Graphics2DDelegate((Graphics2D)myGraphics.create()) {
          @Nullable
          private Shape intersect(@Nullable Shape s1, @Nullable Shape s2) {
            if (s1 == null || s2 == null) {
              return s1 == null ? s2 : s1;
            }

            Area a1 = new Area(s1);
            Area a2 = new Area(s2);

            a1.intersect(a2);
            return a1;
          }

          @Override
          public void clip(@Nullable Shape s) {
            if (s == null) {
              setClip(null);
              return;
            }

            super.clip(s);
          }

          @Override
          public void setClip(@Nullable Shape sh) {
            try {
              super.setClip(intersect(getTransform().createInverse().createTransformedShape(originalClip), sh));
            } catch (NoninvertibleTransformException e) {
              LOG.error(e);
            }
          }

          @Override
          public void setTransform(AffineTransform Tx) {
            if (originalTx != null) {
              AffineTransform transform = (AffineTransform)originalTx.clone();
              transform.concatenate(Tx);
              super.setTransform(transform);
            } else {
              super.setTransform(Tx);
            }
          }

          @Override
          public AffineTransform getTransform() {
            AffineTransform currentTransform = super.getTransform();

            if (originalTxInverse != null) {
              currentTransform.concatenate(originalTxInverse);
            }

            return currentTransform;
          }
        };
      }

      @Override
      public int getWidth() {
        return w;
      }

      @Override
      public int getHeight() {
        return h;
      }
    };
  }
}
