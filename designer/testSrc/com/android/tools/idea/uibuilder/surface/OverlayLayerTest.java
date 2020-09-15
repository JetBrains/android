/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tools.adtui.imagediff.ImageDiffUtil;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.ui.designer.overlays.OverlayConfiguration;
import com.android.tools.idea.ui.designer.overlays.OverlayData;
import com.android.tools.idea.ui.designer.overlays.OverlayEntry;
import com.android.tools.idea.ui.designer.overlays.OverlayProvider;
import com.intellij.mock.MockApplication;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.UIUtil;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.TextLayout;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class OverlayLayerTest {
  public static final int SCREEN_VIEW_WIDTH = 200;
  public static final int SCREEN_VIEW_HEIGHT = 200;
  public static final double SCALE = 1;
  private static final Rectangle FULL_SIZE = new Rectangle(SCREEN_VIEW_WIDTH, SCREEN_VIEW_HEIGHT);
  private OverlayConfiguration myOverlayConfiguration;

  private final Disposable myDisposable =  Disposer.newDisposable();

  @Before
  public void setUp() {
    MockApplication instance = new MockApplication(myDisposable);
    ApplicationManager.setApplication(instance, myDisposable);
    myOverlayConfiguration = new OverlayConfiguration();
  }

  @After
  public void tearDown() {
    Disposer.dispose(myDisposable);
  }

  @Test
  public void testPaintOverlay() throws Exception {
    DesignSurface surface = mock(DesignSurface.class);
    when(surface.getOverlayConfiguration()).thenReturn(myOverlayConfiguration);
    SceneView sceneView = getSceneViewMock(surface);

    OverlayLayer layer = new OverlayLayer(sceneView);

    BufferedImage screenViewReference = ImageUtil.createImage(SCREEN_VIEW_WIDTH,
                                                              SCREEN_VIEW_HEIGHT,
                                                              BufferedImage.TYPE_INT_ARGB);
    BufferedImage testOverlay= ImageUtil.createImage(SCREEN_VIEW_WIDTH,
                                                     SCREEN_VIEW_HEIGHT,
                                                     BufferedImage.TYPE_INT_ARGB);
    drawOverlay(screenViewReference, testOverlay);

    BufferedImage screenViewTestResult = ImageUtil.createImage(SCREEN_VIEW_WIDTH,
                                                               SCREEN_VIEW_HEIGHT,
                                                               BufferedImage.TYPE_INT_ARGB);
    paintOverlay(layer, screenViewTestResult, testOverlay);

    ImageDiffUtil.assertImageSimilar("overlaylayer_result.png",
                                     screenViewReference,
                                     screenViewTestResult,
                                     0.0);
  }

  @Test
  public void testPaintPlaceholder() throws Exception {
    DesignSurface surface = mock(DesignSurface.class);
    when(surface.getOverlayConfiguration()).thenReturn(myOverlayConfiguration);
    SceneView sceneView = getSceneViewMock(surface);

    BufferedImage sceneViewTestImage = ImageUtil.createImage(SCREEN_VIEW_WIDTH,
                                                             SCREEN_VIEW_HEIGHT,
                                                             BufferedImage.TYPE_INT_ARGB);;
    OverlayLayer layer = new OverlayLayer(sceneView);
    myOverlayConfiguration.showPlaceholder();
    Graphics2D g = paintColor(sceneViewTestImage, Color.BLACK, null);
    layer.paint(g);

    BufferedImage sceneViewBaseImage = ImageUtil.createImage(SCREEN_VIEW_WIDTH,
                                                             SCREEN_VIEW_HEIGHT,
                                                             BufferedImage.TYPE_INT_ARGB);;
    paintColor(sceneViewBaseImage, Color.BLACK, null);
    paintColor(sceneViewBaseImage, Color.WHITE, OverlayLayer.getPlaceholderAlpha(), null);
    paintText(sceneViewBaseImage,
              OverlayLayer.getPlaceholderText(),
              OverlayLayer.getPlaceholderAlpha());

    ImageDiffUtil.assertImageSimilar("overlaylayer_result.png",
                                     sceneViewBaseImage,
                                     sceneViewTestImage,
                                     0.0);
  }

  @Test
  public void testPaintPlaceholderShape() throws Exception {
    DesignSurface surface = mock(DesignSurface.class);
    when(surface.getOverlayConfiguration()).thenReturn(myOverlayConfiguration);
    SceneView sceneView = getSceneViewMock(surface);
    Shape shape = new Ellipse2D.Double(0, 0, SCREEN_VIEW_WIDTH, SCREEN_VIEW_HEIGHT);
    when(sceneView.getScreenShape()).thenReturn(shape);

    BufferedImage sceneViewTestImage = ImageUtil.createImage(SCREEN_VIEW_WIDTH,
                                                             SCREEN_VIEW_HEIGHT,
                                                             BufferedImage.TYPE_INT_ARGB);;
    OverlayLayer layer = new OverlayLayer(sceneView);
    myOverlayConfiguration.showPlaceholder();
    Graphics2D g = paintColor(sceneViewTestImage, Color.BLACK, shape);
    layer.paint(g);

    BufferedImage sceneViewBaseImage = ImageUtil.createImage(SCREEN_VIEW_WIDTH,
                                                             SCREEN_VIEW_HEIGHT,
                                                             BufferedImage.TYPE_INT_ARGB);;
    paintColor(sceneViewBaseImage, Color.BLACK, shape);
    paintColor(sceneViewBaseImage, Color.WHITE, OverlayLayer.getPlaceholderAlpha(), shape);
    paintText(sceneViewBaseImage,
              OverlayLayer.getPlaceholderText(),
              OverlayLayer.getPlaceholderAlpha());

    ImageDiffUtil.assertImageSimilar("overlaylayer_result.png",
                                     sceneViewBaseImage,
                                     sceneViewTestImage,
                                     0.0);
  }

  private void paintOverlay(OverlayLayer layer, BufferedImage image, BufferedImage overlay) {
    Graphics2D g2 = paintColor(image, Color.WHITE, null);
    paintColor(overlay, Color.BLACK, null);

    myOverlayConfiguration.updateOverlay(
      new OverlayData(new OverlayEntry("id",
                                       mock(OverlayProvider.class)),
                      "name", overlay));
    layer.paint(g2);
  }

  private void drawOverlay(BufferedImage image, BufferedImage overlay) {
    Graphics2D g = paintColor(image, Color.WHITE, null);
    paintColor(overlay, Color.BLACK, null);
    g.setComposite(AlphaComposite.SrcOver.derive(myOverlayConfiguration.getOverlayAlpha()));
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                       RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    g.drawImage(overlay,
                0,
                0,
                SCREEN_VIEW_WIDTH,
                SCREEN_VIEW_HEIGHT,
                null);
  }

  @NotNull
  private static Graphics2D paintColor(@NotNull BufferedImage image,
                                       @NotNull Color color,
                                       @Nullable Shape shape) {
    Graphics2D g = (Graphics2D)image.getGraphics();
    g.setColor(color);
    if(shape != null) {
      g.fill(shape);
    } else {
      g.fillRect(0, 0, SCREEN_VIEW_WIDTH, SCREEN_VIEW_HEIGHT);
    }
    return g;
  }

  private static void paintColor(@NotNull BufferedImage image,
                                 @NotNull Color color,
                                 float alpha,
                                 @Nullable Shape shape) {
    Graphics2D g = (Graphics2D)image.getGraphics();
    g.setComposite(AlphaComposite.SrcOver.derive(alpha));
    g.setColor(color);
    if(shape != null) {
      g.fill(shape);
    } else {
      g.fillRect(0, 0, SCREEN_VIEW_WIDTH, SCREEN_VIEW_HEIGHT);
    }
  }

  private static void paintText(@NotNull BufferedImage image, String text, float alpha) {
    Graphics2D g = (Graphics2D)image.getGraphics();
    g.setComposite(AlphaComposite.SrcOver.derive(alpha));
    g.setPaint(JBColor.WHITE);
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                       RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

    g.setFont(UIUtil.getFont(UIUtil.FontSize.NORMAL, null));
    g.setPaint(JBColor.BLACK);

    TextLayout textLayout = new TextLayout(text,
                                           g.getFont(),
                                           g.getFontRenderContext());
    double textHeight = textLayout.getBounds().getHeight();
    double textWidth = textLayout.getBounds().getWidth();

    g.drawString(text,
                 SCREEN_VIEW_WIDTH / 2 - (int)textWidth / 2,
                 SCREEN_VIEW_HEIGHT / 2 + (int)textHeight / 2);
  }


  private static Rectangle scaleRectangle(Rectangle rect, double scale) {
    return new Rectangle(rect.x, rect.y, (int)(rect.width * scale), (int)(rect.width * scale));
  }

  private static SceneView getSceneViewMock(DesignSurface surface) {
    SceneView sceneView = mock(SceneView.class);
    Ref<Rectangle> screenViewRectangle = new Ref<>(scaleRectangle(FULL_SIZE, SCALE));

    when(sceneView.getSurface()).thenReturn(surface);
    when(sceneView.getContentSize(any(Dimension.class))).thenAnswer(new Answer<Dimension>() {
      @Override
      public Dimension answer(InvocationOnMock invocation) {
        Dimension returnDimension = (Dimension)invocation.getArguments()[0];

        if (returnDimension != null) {
          returnDimension.setSize(screenViewRectangle.get().getSize());
        }
        else {
          returnDimension = screenViewRectangle.get().getSize();
        }

        return returnDimension;
      }
    });

    when(sceneView.getX()).thenReturn(0);
    when(sceneView.getY()).thenReturn(0);
    when(sceneView.getScale()).thenReturn(1.0);

    return sceneView;
  }
}
