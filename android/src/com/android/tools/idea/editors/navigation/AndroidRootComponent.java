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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;

public class AndroidRootComponent extends JComponent {
  //private static final Object RENDERING_LOCK = new Object();
  private static final RenderedView ABSENT_ROOT_VIEW = new RenderedView(null, null, 0, 0, 200, 200);
  public static final Dimension ZERO_SIZE = new Dimension(0, 0);

  private RenderResult myRenderResult = null;
  private double myScale;
  private Dim myDim;

  private static class Dim {
    float myKx;
    float myKy;

    Dim(float kx, float ky) {
      myKx = kx;
      myKy = ky;
    }
  }

  public AndroidRootComponent() {
    myScale = 1;
  }

  @Nullable
  public RenderResult getRenderResult() {
    return myRenderResult;
  }

  private void setRenderResult(@Nullable RenderResult renderResult) {
    myRenderResult = renderResult;
    myDim = null;
    repaint();
  }

  public double getScale() {
    return myScale;
  }

  public void setScale(double scale) {
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
    }
  }

  @Override
  public Dimension getPreferredSize() {
    //return myRenderResult.getImage().getRequiredSize();
    BufferedImage image = getImage();
    return image == null ? ZERO_SIZE : new Dimension(image.getWidth(), image.getHeight());
  }

  public void render(@NotNull final Project project, @NotNull final VirtualFile file) {
    // The rendering service takes long enough to initialise that we don't want to do this from the EDT.
    // Further, IntellJ's helper classes don't not allow read access from outside EDT, so we need nested runnables.
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          @Override
          public void run() {
            if (project.isDisposed()) {
              return;
            }
            PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
            if (psiFile != null) {
              AndroidFacet facet = AndroidFacet.getInstance(psiFile);
              if (facet != null) {
                Module module = facet.getModule();
                Configuration configuration = facet.getConfigurationManager().getConfiguration(file);
                RenderLogger logger = new RenderLogger(file.getName(), module);
                //synchronized (RENDERING_LOCK) {
                RenderService service = RenderService.create(facet, module, psiFile, configuration, logger, null);
                if (service != null) {
                  setRenderResult(service.render());
                  service.dispose();
                }
                //}
              }
            }
          }
        });
      }
    });
  }

  public RenderedView getRootView() {
    RenderResult renderResult = getRenderResult();
    if (renderResult == null) {
      return ABSENT_ROOT_VIEW;
    }
    RenderedViewHierarchy hierarchy = renderResult.getHierarchy();
    if (hierarchy == null) {
      return ABSENT_ROOT_VIEW;
    }
    List<RenderedView> roots = hierarchy.getRoots();
    if (roots.isEmpty()) {
      return ABSENT_ROOT_VIEW;
    }
    return roots.get(0);
  }

  private Dim getDim() {
    if (myDim == null) {
      RenderedView root = getRootView();
      int b = root.y2() + 100; // todo this accounts for the button bar at the bottom of the rendered view; remove
      int r = root.x2();

      int cW = getWidth();
      int cH = getHeight();

      float myKx = (float)r / cW;
      float myKy = (float)b / cH;

      myDim = new Dim(myKx, myKy);
    }
    return myDim;
  }

  public Point convertPointFromViewToModel(Point p) {
    int dx = p.x - getX();
    int dy = p.y - getY();
    Dim dim = getDim();
    return new Point((int)(dx * dim.myKx), (int)(dy * dim.myKy));
  }

  public Rectangle getBounds(@Nullable RenderedView leaf) {
    if (leaf == null) {
      leaf = getRootView();
    }
    Dim dim = getDim();
    float kx = dim.myKx;
    float ky = dim.myKy;
    return new Rectangle(getX() + ((int)(leaf.x / kx)), getY() + ((int)(leaf.y / ky)), ((int)(leaf.w / kx)), ((int)(leaf.h / ky)));
  }
}
