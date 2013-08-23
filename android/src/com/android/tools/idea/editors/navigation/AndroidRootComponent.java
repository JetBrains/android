/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.editors.navigation;

import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.rendering.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class AndroidRootComponent extends JComponent {
  //private static final Object RENDERING_LOCK = new Object();

  final RenderingParameters myRenderingParameters;
  final PsiFile myPsiFile;

  private float myScale = 1;

  private RenderResult myRenderResult = null;

  public AndroidRootComponent(@NotNull final RenderingParameters renderingParameters, @Nullable final PsiFile psiFile) {
    this.myRenderingParameters = renderingParameters;
    this.myPsiFile = psiFile;
  }

  @Nullable
  public RenderResult getRenderResult() {
    return myRenderResult;
  }

  private void setRenderResult(@Nullable RenderResult renderResult) {
    myRenderResult = renderResult;
    repaint();
  }

  public float getScale() {
    return myScale;
  }

  public void setScale(float scale) {
    myScale = scale;
  }

  @Nullable
  private BufferedImage getImage() {
    ScalableImage image = myRenderResult == null ? null : myRenderResult.getImage();
    return image == null ? null : image.getOriginalImage();
  }

  @Override
  public void paintComponent(Graphics g) {
    //ScalableImage image = myRenderResult.getImage();
    //if (image != null) {
    //  image.paint(g);
    //}
    BufferedImage image = getImage();
    if (image != null) {
      /*
      if (false) {
        Graphics2D g2 = (Graphics2D)g;
        g2.setRenderingHint(KEY_INTERPOLATION, VALUE_INTERPOLATION_BICUBIC);
        //g2.setRenderingHint(KEY_INTERPOLATION, VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.setRenderingHint(KEY_RENDERING, VALUE_RENDER_QUALITY);
        g2.setRenderingHint(KEY_ANTIALIASING, VALUE_ANTIALIAS_ON);
        g2.drawImage(image, 0, 0, getWidth(), getHeight(), 0, 0, image.getWidth(), image.getHeight(), null);
      }
      if (false) {
        g.drawImage(image, 0, 0, getWidth(), getHeight(), null);
      }
      */
      g.drawImage(ImageUtils.scale(image, myScale, myScale, 0, 0), 0, 0, getWidth(), getHeight(), null);
    } else {
      g.setColor(Color.WHITE);
      g.fillRect(0, 0, getWidth(), getHeight());
      g.setColor(Color.GRAY);
      String message = "Rendering...";
      Font font = g.getFont();
      int messageWidth = getFontMetrics(font).stringWidth(message);
      g.drawString(message, (getWidth() - messageWidth)/2, getHeight()/2);
      render();
    }
  }

  private void render() {
    if (myPsiFile == null) {
      return;
    }
    // The rendering service takes long enough to initialise that we don't want to do this from the EDT.
    // Further, IntellJ's helper classes don't not allow read access from outside EDT, so we need nested runnables.
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          Project project = myRenderingParameters.myProject;
          AndroidFacet facet = myRenderingParameters.myFacet;
          Configuration configuration = myRenderingParameters.myConfiguration;
          @Override
          public void run() {
            if (project.isDisposed()) {
              return;
            }
            Module module = facet.getModule();
            RenderLogger logger = new RenderLogger(myPsiFile.getName(), module);
            //synchronized (RENDERING_LOCK) {
            RenderService service = RenderService.create(facet, module, myPsiFile, configuration, logger, null);
            if (service != null) {
              setRenderResult(service.render());
              service.dispose();
            }
            //}
          }
        });
      }
    });
  }

  private int scale(int d) {
    return ((int)(d * myScale));
  }

  private int unScale(int d) {
    return (int)(d / myScale);
  }

  public Point convertPointFromViewToModel(Point p) {
    return new Point(unScale(p.x), unScale(p.y));
  }

  public Rectangle getBounds(@Nullable RenderedView leaf) {
    if (leaf == null) {
      return getBounds();
    }
    return new Rectangle(getX() + scale(leaf.x), getY() + scale(leaf.y), scale(leaf.w), scale(leaf.h));
  }
}
