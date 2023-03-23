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

import com.android.tools.idea.common.surface.Layer;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.rendering.imagepool.ImagePool;
import com.android.tools.idea.rendering.imagepool.ImagePoolImageDisposer;
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager;
import com.android.tools.idea.uibuilder.visual.colorblindmode.ColorBlindMode;
import com.android.tools.idea.uibuilder.visual.colorblindmode.ColorConverter;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.StartupUiUtil;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Transparency;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Responsible for painting a screen view
 */
public class ScreenViewLayer extends Layer {

  public final static Map<RenderingHints.Key, Object> HQ_RENDERING_HINTS = ImmutableMap.of(
    RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON,
    RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY,
    RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR
  );

  private final ScreenView myScreenView;

  /**
   * Cached scaled image
   */
  @Nullable private BufferedImage myCachedVisibleImage;
  /**
   * Cached last render result
   */
  @Nullable private RenderResult myLastRenderResult;

  private final Rectangle myScreenViewVisibleRect = new Rectangle();
  private final Dimension myScreenViewSize = new Dimension();
  private final Rectangle myCachedScreenViewDisplayRect = new Rectangle();
  private double myLastScale;

  private final ColorConverter myImageFilter;

  /**
   * Create a new ScreenViewLayer for the given screenView.
   * @param screenView The screenView containing the model to render
   */
  public ScreenViewLayer(@NotNull ScreenView screenView) {
    this(screenView, ColorBlindMode.NONE);
  }

  public ScreenViewLayer(@NotNull ScreenView screenView, @NotNull ColorBlindMode colorBlindFilter) {
    this(screenView, colorBlindFilter == ColorBlindMode.NONE ? null : new ColorConverter(colorBlindFilter));
  }

  private ScreenViewLayer(@NotNull ScreenView screenView, @Nullable ColorConverter imageFilter) {
    myScreenView = screenView;
    myLastScale = myScreenView.getScale();
    myImageFilter = imageFilter;
    Disposer.register(screenView.getSurface(), this);
    if(myImageFilter != null) Disposer.register(this, myImageFilter);
  }

  @SuppressWarnings("UseJBColor")
  private static final Color CLEAR_BACKGROUND = new Color(255, 255, 255, 0);

  /**
   * Renders a preview image trying to reuse the existing buffer when possible.
   */
  @NotNull
  private static BufferedImage getPreviewImage(@NotNull GraphicsConfiguration configuration,
                                               @NotNull ImagePool.Image renderedImage,
                                               int screenViewX, int screenViewY,
                                               @NotNull Rectangle screenViewVisibleSize,
                                               double xScaleFactor, double yScaleFactor,
                                               @Nullable BufferedImage existingBuffer,
                                               boolean screenViewHasBorderLayer) {
    // Extract from the result image only the visible rectangle. The result image might be bigger or smaller than the actual ScreenView
    // size so we need to also rescale.
    int sx1 = (int)Math.round((screenViewVisibleSize.x - screenViewX) * xScaleFactor);
    int sy1 = (int)Math.round((screenViewVisibleSize.y - screenViewY) * yScaleFactor);
    int sx2 = sx1 + (int)Math.round(screenViewVisibleSize.width * xScaleFactor);
    int sy2 = sy1 + (int)Math.round(screenViewVisibleSize.height * yScaleFactor);
    BufferedImage image;
    boolean clearBackground;
    boolean bufferWithScreenViewSizeExists = existingBuffer != null
                                             && ImageUtil.getUserWidth(existingBuffer) == screenViewVisibleSize.width
                                             && ImageUtil.getUserHeight(existingBuffer) == screenViewVisibleSize.height;
    if (screenViewHasBorderLayer && bufferWithScreenViewSizeExists) {
      image = existingBuffer;
      clearBackground = true;
    }
    else {
      image = ImageUtil.createImage(configuration, screenViewVisibleSize.width, screenViewVisibleSize.height, Transparency.TRANSLUCENT);
      existingBuffer = image;
      // No need to clear the background for a new image
      clearBackground = false;
    }
    int previewImageWidth = ImageUtil.getUserWidth(image);
    int previewImageHeight = ImageUtil.getUserHeight(image);
    Graphics2D cacheImageGraphics = image.createGraphics();
    if (clearBackground) {
      cacheImageGraphics.setColor(CLEAR_BACKGROUND);
      cacheImageGraphics.setComposite(AlphaComposite.Clear);
      cacheImageGraphics.fillRect(0,0, previewImageWidth, previewImageHeight);
      cacheImageGraphics.setComposite(AlphaComposite.Src);
    }
    cacheImageGraphics.setRenderingHints(HQ_RENDERING_HINTS);
    renderedImage.drawImageTo(cacheImageGraphics, 0, 0, previewImageWidth, previewImageHeight, sx1, sy1, sx2, sy2);
    cacheImageGraphics.dispose();

    return existingBuffer;
  }

  @Override
  public void paint(@NotNull Graphics2D graphics2D) {
    myScreenView.getScaledContentSize(myScreenViewSize);
    // Calculate the portion of the screen view that it's visible
    myScreenViewVisibleRect.setBounds(myScreenView.getX(), myScreenView.getY(),
                                      myScreenViewSize.width, myScreenViewSize.height);
    Rectangle2D clipBounds = graphics2D.getClipBounds();
    if (!myScreenViewVisibleRect.intersects(clipBounds)) {
      return;
    }

    // When the screen view visible rect is bigger than the current viewport, we limit the visible rect to the viewport.
    // We will never be painting an image bigger than the viewport.
    if (myScreenViewVisibleRect.width > clipBounds.getWidth() || myScreenViewVisibleRect.height > clipBounds.getHeight()) {
      Rectangle2D.intersect(myScreenViewVisibleRect, clipBounds, myScreenViewVisibleRect);
    }

    // In some cases, we will try to re-use the previous image to paint on top of it, assuming that it still matches the right dimensions.
    // This way we can save the allocation.
    BufferedImage previousVisibleImage = myCachedVisibleImage;
    RenderResult renderResult = myScreenView.getResult();
    boolean drawNewImg = false;
    if (newRenderImageAvailable(renderResult)) {
      setLastRenderResult(renderResult);
      myScreenView.getScene().needsRebuildList();
      drawNewImg = true;
    }

    Graphics2D g = (Graphics2D) graphics2D.create();
    BufferedImage[] cachedVisibleImage = new BufferedImage[1];
    cachedVisibleImage[0] = drawNewImg ? null : previousVisibleImage;
    double currentScale = myScreenView.getScale();
    //noinspection FloatingPointEquality
    if (drawNewImg || currentScale != myLastScale || !myScreenViewVisibleRect.equals(myCachedScreenViewDisplayRect)) {
      if (myLastRenderResult != null) {
        ImagePool.Image image = myLastRenderResult.getRenderedImage();
        ImagePoolImageDisposer.runWithDisposeLock(image, theImage -> {
          if (theImage.isValid()) {
            int resultImageWidth = theImage.getWidth();
            int resultImageHeight = theImage.getHeight();

            myCachedScreenViewDisplayRect.setBounds(myScreenViewVisibleRect);
            // Obtain the factors to convert from screen view coordinates to our result image coordinates
            double xScaleFactor = (double)resultImageWidth / myScreenViewSize.width;
            double yScaleFactor = (double)resultImageHeight / myScreenViewSize.height;

            cachedVisibleImage[0] = getPreviewImage(g.getDeviceConfiguration(), theImage,
                                                 myScreenView.getX(), myScreenView.getY(),
                                                 myScreenViewVisibleRect, xScaleFactor, yScaleFactor,
                                                 previousVisibleImage, myScreenView.hasBorderLayer());
            myCachedVisibleImage = cachedVisibleImage[0];
            myLastScale = currentScale;
          } else {
            // The image is not valid, keep using the previously cached one, if available.
            cachedVisibleImage[0] = myCachedVisibleImage;
          }
        });
      }
    }

    if (cachedVisibleImage[0] != null) {
      Shape screenShape = myScreenView.getScreenShape();
      if (screenShape != null) {
        g.clip(screenShape);
      }

      // When screen rotation feature is enabled, we want to rotate the image.
      NlDesignSurface surface = myScreenView.getSurface();
      float degree = surface.getRotateSurfaceDegree();
      if (!Float.isNaN(degree)) {
        // We change the graphic context with the assumption the context will be disposed right after drawing operation.
        g.rotate(Math.toRadians(degree), myScreenView.getX() + myScreenViewSize.width / 2, myScreenView.getY() + myScreenViewSize.height / 2);
      }

      StartupUiUtil.drawImage(g, cachedVisibleImage[0], myScreenViewVisibleRect.x, myScreenViewVisibleRect.y, null);
    }
    g.dispose();
  }

  protected void setLastRenderResult(@Nullable RenderResult result) {
    myLastRenderResult = result;
    if (myImageFilter == null || result == null) return;

    // Apply the color converter if any.
    // This is used for example to support different color-blind modes
    result.processImageIfNotDisposed(image -> {
      if (image == null) return;
      BufferedImage copy = image.getCopy();
      if (copy == null) return;
      myImageFilter.convert(copy, copy);
      image.paint(g2D -> {
        int w = image.getWidth();
        int h = image.getHeight();
        g2D.drawImage(copy, 0, 0, w, h, 0, 0, w, h, null);
      });
    });
  }

  /**
   * Check whether the provided render result has new image to draw. We only accept successful renders. If the new result is
   * an error, we prefer to keep the last successful one.
   *
   * @param renderResult The renderResult from {@link LayoutlibSceneManager#getRenderResult()}
   * @return false if renderResult is null or the same as the previous one or if no image is available, true otherwise
   */
  private boolean newRenderImageAvailable(@Nullable RenderResult renderResult) {
    return renderResult != null &&
           renderResult.getRenderResult().isSuccess() &&
           renderResult.getRenderedImage().isValid() &&
           renderResult != myLastRenderResult;
  }

  @Override
  public void dispose() {
    super.dispose();
    setLastRenderResult(null);
  }
}
