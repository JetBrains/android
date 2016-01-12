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
package com.android.tools.idea.editors.gfxtrace.controllers;

import com.android.tools.idea.editors.gfxtrace.GfxTraceEditor;
import com.android.tools.idea.editors.gfxtrace.actions.FramebufferTypeAction;
import com.android.tools.idea.editors.gfxtrace.actions.FramebufferWireframeAction;
import com.android.tools.idea.editors.gfxtrace.service.RenderSettings;
import com.android.tools.idea.editors.gfxtrace.service.WireframeMode;
import com.android.tools.idea.editors.gfxtrace.service.image.FetchedImage;
import com.android.tools.idea.editors.gfxtrace.service.path.AtomPath;
import com.android.tools.idea.editors.gfxtrace.service.path.DevicePath;
import com.android.tools.idea.editors.gfxtrace.service.path.ImageInfoPath;
import com.android.tools.idea.editors.gfxtrace.service.path.PathStore;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Separator;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class FrameBufferController extends ImagePanelController {
  public static JComponent createUI(GfxTraceEditor editor) {
    return new FrameBufferController(editor).myPanel;
  }

  private static final int MAX_SIZE = 0xffff;

  public enum BufferType {
    Color,
    Depth
  }

  @NotNull private final PathStore<DevicePath> myRenderDevice = new PathStore<DevicePath>();
  @NotNull private final PathStore<AtomPath> myAtomPath = new PathStore<AtomPath>();
  @NotNull private final RenderSettings mySettings = new RenderSettings();
  @NotNull private BufferType myBufferType = BufferType.Color;

  private FrameBufferController(@NotNull GfxTraceEditor editor) {
    super(editor, GfxTraceEditor.SELECT_ATOM);

    mySettings.setMaxHeight(MAX_SIZE);
    mySettings.setMaxWidth(MAX_SIZE);
    mySettings.setWireframeMode(WireframeMode.NoWireframe);

    initToolbar(getToolbarActions(), false);
  }

  private DefaultActionGroup getToolbarActions() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new FramebufferTypeAction(this, BufferType.Color, "Color Buffer", "Display the color framebuffer",
                                        AndroidIcons.GfxTrace.ColorBuffer));
    group.add(new FramebufferTypeAction(this, BufferType.Depth, "Depth Buffer", "Display the depth framebuffer",
                                        AndroidIcons.GfxTrace.DepthBuffer));
    group.add(new Separator());
    group.add(new FramebufferWireframeAction(this, WireframeMode.NoWireframe, "Shaded", "Display the framebuffer with shaded polygons",
                                             AndroidIcons.GfxTrace.WireframeNone));
    group.add(new FramebufferWireframeAction(this, WireframeMode.WireframeOverlay, "Shaded + Wireframe",
                                             "Display the framebuffer with shaded polygons and overlay the wireframe of the last draw call",
                                             AndroidIcons.GfxTrace.WireframeOverlay));
    group.add(new FramebufferWireframeAction(this, WireframeMode.AllWireframe, "Wireframe", "Display the framebuffer with wireframes",
                                             AndroidIcons.GfxTrace.WireframeAll));
    group.add(new Separator());
    return group;
  }

  @NotNull
  public BufferType getBufferType() {
    return myBufferType;
  }

  public void setBufferType(@NotNull BufferType bufferType) {
    if (!myBufferType.equals(bufferType)) {
      myBufferType = bufferType;
      updateBuffer();
    }
  }

  @NotNull
  public WireframeMode getWireframeMode() {
    return mySettings.getWireframeMode();
  }

  public void setWireframeMode(@NotNull WireframeMode mode) {
    if (!mySettings.getWireframeMode().equals(mode)) {
      mySettings.setWireframeMode(mode);
      updateBuffer();
    }
  }

  @Override
  public void notifyPath(PathEvent event) {
    boolean updateBuffer = myRenderDevice.updateIfNotNull(event.findDevicePath());
    updateBuffer = myAtomPath.updateIfNotNull(event.findAtomPath()) | updateBuffer;

    if (updateBuffer && myRenderDevice.getPath() != null && myAtomPath.getPath() != null) {
      updateBuffer();
    }
  }

  private void updateBuffer() {
    setImage(FetchedImage.load(myEditor.getClient(), getImageInfoPath()));
  }

  private ListenableFuture<ImageInfoPath> getImageInfoPath() {
    switch (myBufferType) {
      case Color:
        return myEditor.getClient().getFramebufferColor(myRenderDevice.getPath(), myAtomPath.getPath(), mySettings);
      case Depth:
        return myEditor.getClient().getFramebufferDepth(myRenderDevice.getPath(), myAtomPath.getPath());
      default:
        return null;
    }
  }
}
