/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.intellij.android.designer.model;

import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.RenderContext;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.rendering.RenderedViewHierarchy;
import com.android.tools.idea.rendering.multi.RenderPreviewManager;
import com.intellij.designer.ModuleProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Render context for test renders
 */
public class TestRenderContext implements RenderContext, ModuleProvider {
  private Project myProject;
  private Module myModule;
  private XmlFile myXmlFile;
  private Configuration myConfiguration;

  public TestRenderContext(Project project, Module module, XmlFile xmlFile) {
    myProject = project;
    myModule = module;
    myXmlFile = xmlFile;
  }

  @Nullable
  @Override
  public Configuration getConfiguration() {
    return myConfiguration;
  }

  @Override
  public void setConfiguration(@NotNull Configuration configuration) {
    myConfiguration = configuration;
  }

  @Override
  public void requestRender() {
    throw new RuntimeException("requestRender() is not supported by TestRenderContext");
  }

  @NotNull
  @Override
  public RenderContext.UsageType getType() {
    return UsageType.XML_PREVIEW;
  }

  @Nullable
  @Override
  public XmlFile getXmlFile() {
    return myXmlFile;
  }

  @Nullable
  @Override
  public VirtualFile getVirtualFile() {
    return myXmlFile.getVirtualFile();
  }

  @Nullable
  @Override
  public Module getModule() {
    return myModule;
  }

  @Override
  public Project getProject() {
    return myProject;
  }

  @Override
  public boolean hasAlphaChannel() {
    throw new RuntimeException("hasAlphaChannel() is not supported by TestRenderContext");
  }

  @NotNull
  @Override
  public Component getComponent() {
    throw new RuntimeException("getComponent() is not supported by TestRenderContext");
  }

  @NotNull
  @Override
  public Dimension getFullImageSize() {
    throw new RuntimeException("getFullImageSize() is not supported by TestRenderContext");
  }

  @NotNull
  @Override
  public Dimension getScaledImageSize() {
    throw new RuntimeException("getScaledImageSize() is not supported by TestRenderContext");
  }

  @NotNull
  @Override
  public Rectangle getClientArea() {
    throw new RuntimeException("getClientArea() is not supported by TestRenderContext");
  }

  @Override
  public boolean supportsPreviews() {
    throw new RuntimeException("supportsPreviews() is not supported by TestRenderContext");
  }

  @Nullable
  @Override
  public RenderPreviewManager getPreviewManager(boolean createIfNecessary) {
    throw new RuntimeException("getPreviewManager() is not supported by TestRenderContext");
  }

  @Override
  public void setMaxSize(int width, int height) {
    throw new RuntimeException("setMaxSize() is not supported by TestRenderContext");
  }

  @Override
  public void zoomFit(boolean onlyZoomOut, boolean allowZoomIn) {
    throw new RuntimeException("zoomFit() is not supported by TestRenderContext");
  }

  @Override
  public void updateLayout() {
    throw new RuntimeException("updateLayout() is not supported by TestRenderContext");
  }

  @Override
  public void setDeviceFramesEnabled(boolean on) {
    throw new RuntimeException("setDeviceFramesEnabled() is not supported by TestRenderContext");
  }

  @Nullable
  @Override
  public BufferedImage getRenderedImage() {
    throw new RuntimeException("getRenderedImage() is not supported by TestRenderContext");
  }

  @Nullable
  @Override
  public RenderResult getLastResult() {
    throw new RuntimeException("getLastResult() is not supported by TestRenderContext");
  }

  @Nullable
  @Override
  public RenderedViewHierarchy getViewHierarchy() {
    throw new RuntimeException("getViewHierarchy() is not supported by TestRenderContext");
  }
}
