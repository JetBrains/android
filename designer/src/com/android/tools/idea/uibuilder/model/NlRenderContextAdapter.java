/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.model;

import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.RenderContext;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.rendering.RenderedViewHierarchy;
import com.android.tools.idea.rendering.multi.RenderPreviewManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * An {@link RenderContext} to be used when rendering in {@link NlModel}. It is used to notify to the model when a {@link RenderContext}
 * user has requested a render. For example, this is used within {@link com.android.tools.idea.rendering.RenderErrorPanel} to refresh the
 * error list when new "ignores" are added.
 */
class NlRenderContextAdapter implements RenderContext {
  private final NlModel myNlModel;

  public NlRenderContextAdapter(@NotNull NlModel nlModel) {
    myNlModel = nlModel;
  }

  @Override
  public void requestRender() {
    /*
     * Ideally, we would only ask the model for a new render but since the logger is not reset on render (only on inflate), we have to ask
     * for a full inflate + render using NlModel#notifyModified
     */
    myNlModel.notifyModified();
  }


  /*
   * Methods after this point are not used
   */

  @Nullable
  @Override
  public Configuration getConfiguration() {
    return null;
  }

  @Override
  public void setConfiguration(@NotNull Configuration configuration) {
  }

  @NotNull
  @Override
  public UsageType getType() {
    throw new UnsupportedOperationException();
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
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public Dimension getFullImageSize() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public Dimension getScaledImageSize() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public Rectangle getClientArea() {
    throw new UnsupportedOperationException();
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
