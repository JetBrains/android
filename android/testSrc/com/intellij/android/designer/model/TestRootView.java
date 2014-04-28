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

import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.rendering.RenderedImage;
import com.intellij.android.designer.AndroidDesignerEditor;
import com.intellij.android.designer.designSurface.AndroidDesignerEditorPanel;
import com.intellij.android.designer.designSurface.RootView;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * A test root view for rendering and parsing tests
 */
public class TestRootView extends RootView {

  private RenderedImage myImage;

  public static TestRootView getTestRootView(Project project, Module module, VirtualFile layoutFile, RenderResult result) {
    AndroidDesignerEditor throwawayEditor = new AndroidDesignerEditor(project, layoutFile);
    AndroidDesignerEditorPanel throwawayPanel = new AndroidDesignerEditorPanel(throwawayEditor, project, module, layoutFile);
    return new TestRootView(throwawayPanel, result);
  }

  private TestRootView(@NotNull AndroidDesignerEditorPanel panel, @NotNull RenderResult renderResult) {
    super(panel, 0, 0, renderResult);
  }

  @Nullable
  @Override
  public BufferedImage getImage() {
    return myImage.getOriginalImage();
  }

  @Override
  public void setRenderedImage(@Nullable RenderedImage image) {
    myImage = image;
  }

  @Nullable
  @Override
  public RenderedImage getRenderedImage() {
    return myImage;
  }

  @Override
  public void updateBounds(boolean imageChanged) {
    // TODO: do something here
  }

  @NotNull
  @Override
  public AndroidDesignerEditorPanel getPanel() {
    throw new RuntimeException("getPanel() is not supported by TestRootView");
  }

  @Override
  public boolean getShowDropShadow() {
    throw new RuntimeException("getShowDropShadow() is not supported by TestRootView");
  }

  @Override
  public void paintComponent(Graphics g) {
    throw new RuntimeException("paintComponent() is not supported by TestRootView");
  }

  @Override
  public void updateSize() {
    throw new RuntimeException("updateSize() is not supported by TestRootView");
  }

  @Override
  protected void paintImage(Graphics g) {
    throw new RuntimeException("paintImage() is not supported by TestRootView");
  }

  @Override
  public int getScaledWidth() {
    throw new RuntimeException("getScaledWidth() is not supported by TestRootView");
  }

  @Override
  public int getScaledHeight() {
    throw new RuntimeException("getScaledHeight() is not supported by TestRootView");
  }

  @Override
  public double getScale() {
    throw new RuntimeException("getScale() is not supported by TestRootView");
  }

  @Override
  public int getShiftX() {
    throw new RuntimeException("getShiftX() is not supported by TestRootView");
  }

  @Override
  public int getShiftY() {
    throw new RuntimeException("getShiftY() is not supported by TestRootView");
  }
}
