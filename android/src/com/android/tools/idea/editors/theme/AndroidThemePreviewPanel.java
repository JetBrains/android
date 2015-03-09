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
package com.android.tools.idea.editors.theme;


import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.RenderContext;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.rendering.RenderedViewHierarchy;
import com.android.tools.idea.rendering.multi.RenderPreviewManager;
import com.android.tools.swing.layoutlib.AndroidPreviewPanel;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.parsers.ParserConfigurationException;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Subclass of AndroidPreviewPanel dedicated to Theme rendering.
 * Implements RenderContext to support Configuration toolbar in Theme Editor
 */
public class AndroidThemePreviewPanel extends AndroidPreviewPanel implements RenderContext {

  private static final Logger LOG = Logger.getInstance(AndroidThemePreviewPanel.class.getName());

  protected DumbService myDumbService;

  public AndroidThemePreviewPanel(@NotNull Configuration configuration) {
    super(configuration);

    myDumbService = DumbService.getInstance(myConfiguration.getModule().getProject());

    final int minApiLevel = configuration.getTarget() != null ? configuration.getTarget().getVersion().getApiLevel() : Integer.MAX_VALUE;
    try {
      setDocument(new ThemePreviewBuilder().setApiLevel(minApiLevel).build());
    }
    catch (ParserConfigurationException e) {
      LOG.error("Unable to generate dynamic theme preview", e);
    }

    // Find custom controls
    final Project project = configuration.getModule().getProject();
    myDumbService.runWhenSmart(new Runnable() {
      @Override
      public void run() {
        if (!project.isOpen()) {
          return;
        }
        PsiClass viewClass = JavaPsiFacade.getInstance(project).findClass("android.view.View", GlobalSearchScope.allScope(project));

        if (viewClass == null) {
          LOG.error("Unable to find 'com.android.View'");
          return;
        }
        Query<PsiClass> viewClasses = ClassInheritorsSearch.search(viewClass, GlobalSearchScope.projectScope(project), true);
        final ThemePreviewBuilder builder = new ThemePreviewBuilder().setApiLevel(minApiLevel);
        viewClasses.forEach(new Processor<PsiClass>() {
          @Override
          public boolean process(PsiClass psiClass) {
            String description = psiClass.getName(); // We use the "simple" name as description on the preview.
            String className = psiClass.getQualifiedName();

            if (description == null || className == null) {
              // Currently we ignore anonymous views
              // TODO: Decide how we want to display anonymous classes
              return false;
            }

            builder.addComponent(new ThemePreviewBuilder.ComponentDefinition(description, ThemePreviewBuilder.ComponentGroup.CUSTOM,
                                                                             className));
            return true;
          }
        });

        try {
          setDocument(builder.build());
          repaint();
        }
        catch (ParserConfigurationException e) {
          LOG.error("Unable to generate dynamic theme preview", e);
        }
      }
    });
  }

  @Override
  public void paintComponent(Graphics graphics) {
    // The AndroidPreviewPanel paint it's not DumbAware and it might require layoutlib to resolve some classes using the PSI.
    // We need to postpone the GraphicsLayoutRendererInitialization until the index has been loaded.
    // TODO: Display "loading" text.
    if (myDumbService.isDumb()) {
      myDumbService.runWhenSmart(new Runnable() {
        @Override
        public void run() {
          repaint();
        }
      });
      return;
    }

    super.paintComponent(graphics);
  }

  // Implements RenderContext
  // Only methods relevant to the configuration selection have been implemented

  @Nullable
  @Override
  public Configuration getConfiguration() {
    return myConfiguration;
  }

  @Override
  public void setConfiguration(@NotNull Configuration configuration) {

  }

  @Override
  public void requestRender() {

  }

  @NotNull
  @Override
  public UsageType getType() {
    return UsageType.UNKNOWN;
  }

  @Nullable
  @Override
  public XmlFile getXmlFile() {
    return null;
  }

  @Nullable
  @Override
  public VirtualFile getVirtualFile() {
    return null;
  }

  @Nullable
  @Override
  public Module getModule() {
    return null;
  }

  @Override
  public boolean hasAlphaChannel() {
    return false;
  }

  @NotNull
  @Override
  public Component getComponent() {
    return this;
  }

  @NotNull
  @Override
  public Dimension getFullImageSize() {
    return NO_SIZE;
  }

  @NotNull
  @Override
  public Dimension getScaledImageSize() {
    return NO_SIZE;
  }

  @NotNull
  @Override
  public Rectangle getClientArea() {
    return new Rectangle();
  }

  @Override
  public boolean supportsPreviews() {
    return false;
  }

  @Nullable
  @Override
  public RenderPreviewManager getPreviewManager(boolean createIfNecessary) {
    return null;
  }

  @Override
  public void setMaxSize(int width, int height) {

  }

  @Override
  public void zoomFit(boolean onlyZoomOut, boolean allowZoomIn) {

  }

  @Override
  public void updateLayout() {

  }

  @Override
  public void setDeviceFramesEnabled(boolean on) {

  }

  @Nullable
  @Override
  public BufferedImage getRenderedImage() {
    return null;
  }

  @Nullable
  @Override
  public RenderResult getLastResult() {
    return null;
  }

  @Nullable
  @Override
  public RenderedViewHierarchy getViewHierarchy() {
    return null;
  }
}
