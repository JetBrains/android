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

import static java.awt.RenderingHints.*;

public class AndroidRootComponent extends JComponent {
  private static final Object RENDERING_LOCK = new Object();
  @NotNull
  private RenderResult myRenderResult = null;
  private double myScale;

  public AndroidRootComponent() {
    myScale = 1;
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
      if (true) {
        g.drawImage(ImageUtils.scale(image, myScale, myScale, 0, 0), 0, 0, getWidth(), getHeight(), null);
      }
    }
  }

  @Override
  public Dimension getPreferredSize() {
    //return myRenderResult.getImage().getRequiredSize();
    BufferedImage image = getImage();
    return image == null ? new Dimension() : new Dimension(image.getWidth(), image.getHeight());
  }

  public void render(@NotNull final Project project, @NotNull final VirtualFile file) {
    Thread thread = new Thread(new Runnable() {
      @Override
      public void run() {
        PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
        AndroidFacet facet = AndroidFacet.getInstance(psiFile);
        synchronized (RENDERING_LOCK) {
          Module module = facet.getModule();
          Configuration configuration = facet.getConfigurationManager().getConfiguration(file);
          configuration.setTheme("@android:style/Theme.Holo");
          final RenderLogger logger = new RenderLogger(file.getName(), module);
          RenderService service = RenderService.create(facet, module, psiFile, configuration, logger, null);
          if (service != null) {
            myRenderResult = service.render();
            service.dispose();
          }
          else {
            myRenderResult = new RenderResult(null, null, psiFile, logger);
          }
          repaint();
        }
      }
    });
    //thread.setPriority(1);
    thread.run();
    //repaint();
  }
}
