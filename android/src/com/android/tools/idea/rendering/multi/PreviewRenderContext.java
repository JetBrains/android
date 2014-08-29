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
package com.android.tools.idea.rendering.multi;

import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.RenderContext;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.rendering.RenderedViewHierarchy;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * {@linkplain RenderContext} used when rendering a configuration preview.
 *
 * It basically delegates to a different context (such as the XML preview rendering context
 * or the layout editor rendering context), but (crucially) lets you specify a custom
 * configuration to use instead of the main configuration used by the context. This lets
 * the previews vary configurations for the main preview. It also lets you specify
 * a different input file (used when rendering included files or files with a better configuration
 * match for a particular configuration than the current file.)
 */
class PreviewRenderContext implements RenderContext {
  private final @NotNull RenderContext myRenderContext;
  private final @NotNull Configuration myConfiguration;
  private final @NotNull XmlFile myPsiFile;

  public PreviewRenderContext(@NotNull RenderContext renderContext, @NotNull Configuration configuration,
                              @NotNull XmlFile psiFile) {
    myRenderContext = renderContext;
    myConfiguration = configuration;
    myPsiFile = psiFile;
  }

  @NotNull
  @Override
  public Configuration getConfiguration() {
    return myConfiguration;
  }

  @Override
  public void setConfiguration(@NotNull Configuration configuration) {
    myRenderContext.setConfiguration(configuration);
  }

  @Override
  public void requestRender() {
    myRenderContext.requestRender();
  }

  @NotNull
  @Override
  public UsageType getType() {
    return UsageType.THUMBNAIL_PREVIEW;
  }

  @Nullable
  @Override
  public XmlFile getXmlFile() {
    return myPsiFile;
  }

  @Nullable
  @Override
  public VirtualFile getVirtualFile() {
    return myPsiFile.getVirtualFile();
  }

  @Nullable
  @Override
  public Module getModule() {
    return myRenderContext.getModule();
  }

  @Override
  public boolean hasAlphaChannel() {
    return myRenderContext.hasAlphaChannel();
  }

  @NotNull
  @Override
  public Component getComponent() {
    return myRenderContext.getComponent();
  }

  @NotNull
  @Override
  public Dimension getFullImageSize() {
    return myRenderContext.getFullImageSize();
  }

  @NotNull
  @Override
  public Dimension getScaledImageSize() {
    return myRenderContext.getScaledImageSize();
  }

  @NotNull
  @Override
  public Rectangle getClientArea() {
    return myRenderContext.getClientArea();
  }

  @Nullable
  @Override
  public RenderPreviewManager getPreviewManager(boolean createIfNecessary) {
    return myRenderContext.getPreviewManager(createIfNecessary);
  }

  @Override
  public void setMaxSize(int width, int height) {
    myRenderContext.setMaxSize(width, height);
  }

  @Override
  public boolean supportsPreviews() {
    return myRenderContext.supportsPreviews();
  }

  @Override
  public void zoomFit(boolean onlyZoomOut, boolean allowZoomIn) {
    myRenderContext.zoomFit(onlyZoomOut, allowZoomIn);
  }

  @Override
  public void updateLayout() {
    myRenderContext.updateLayout();
  }

  @Override
  public void setDeviceFramesEnabled(boolean on) {
    myRenderContext.setDeviceFramesEnabled(on);
  }

  @Nullable
  @Override
  public BufferedImage getRenderedImage() {
    return myRenderContext.getRenderedImage();
  }

  @Nullable
  @Override
  public RenderResult getLastResult() {
    return myRenderContext.getLastResult();
  }

  @Nullable
  @Override
  public RenderedViewHierarchy getViewHierarchy() {
    return myRenderContext.getViewHierarchy();
  }
}
