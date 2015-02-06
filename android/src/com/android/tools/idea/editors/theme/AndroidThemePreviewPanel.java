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
import com.android.tools.idea.rendering.DomPullParser;
import com.android.tools.idea.rendering.LayoutPullParserFactory;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.rendering.RenderedViewHierarchy;
import com.android.tools.idea.rendering.multi.RenderPreviewManager;
import com.android.tools.swing.layoutlib.AndroidPreviewPanel;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.SAXException;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * Subclass of AndroidPreviewPanel dedicated to Theme rendering.
 * Implements RenderContext to support Configuration toolbar in Theme Editor
 */
public class AndroidThemePreviewPanel extends AndroidPreviewPanel implements RenderContext {

  private static final Logger LOG = Logger.getInstance(AndroidThemePreviewPanel.class.getName());
  private static final String THEME_PREVIEW_LAYOUT = "/themeEditor/sample_layout.xml";

  public AndroidThemePreviewPanel(Configuration configuration) {
    super(configuration);
    try {
      myDocument =
        DomPullParser.createNewDocumentBuilder().parse(LayoutPullParserFactory.class.getResourceAsStream(THEME_PREVIEW_LAYOUT));
    }
    catch (SAXException e) {
      LOG.error(e);
    }
    catch (IOException e) {
      LOG.error(e);
    }
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
    return null;
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
